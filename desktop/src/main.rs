#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod blocker_state;
mod delay;
mod hosts;
mod pin;
mod tray;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::sync::Mutex;
use tauri::{Manager, State};

// ── Shared app state ──────────────────────────────────────────────────────────

#[derive(Debug, Default, Serialize, Deserialize)]
pub struct Config {
    /// Current domain blocklist (populated from shared blocklist file or extension)
    pub blocklist: Vec<String>,
}

pub struct AppState {
    pub enabled: Mutex<bool>,
    pub config: Mutex<Config>,
}

impl Default for AppState {
    fn default() -> Self {
        AppState {
            enabled: Mutex::new(true),
            config: Mutex::new(Config::default()),
        }
    }
}

// ── Tauri commands (called from settings window JS) ───────────────────────────

#[tauri::command]
fn get_status(state: State<'_, AppState>) -> serde_json::Value {
    let enabled = *state.enabled.lock().unwrap();
    let delay = delay::get()
        .ok()
        .flatten()
        .map(|s| s.seconds_remaining());

    serde_json::json!({
        "enabled": enabled,
        "pin_set": pin::is_set(),
        "disable_seconds_remaining": delay,
    })
}

#[tauri::command]
fn cmd_set_pin(new_pin: String) -> Result<(), String> {
    pin::set_pin(&new_pin).map_err(|e| e.to_string())
}

#[tauri::command]
fn cmd_verify_pin(entered: String) -> bool {
    pin::verify(&entered).unwrap_or(false)
}

#[tauri::command]
fn cmd_request_disable() -> Result<serde_json::Value, String> {
    match delay::get().map_err(|e| e.to_string())? {
        Some(s) => Ok(serde_json::json!({
            "already_pending": true,
            "seconds_remaining": s.seconds_remaining(),
        })),
        None => {
            let s = delay::request_disable().map_err(|e| e.to_string())?;
            Ok(serde_json::json!({
                "already_pending": false,
                "seconds_remaining": s.seconds_remaining(),
                "requested_at": s.requested_at.to_rfc3339(),
            }))
        }
    }
}

#[tauri::command]
fn cmd_confirm_disable(app: tauri::AppHandle, pin: String) -> Result<(), String> {
    blocker_state::confirm_disable(&app, &pin)
}

#[tauri::command]
fn cmd_enable(app: tauri::AppHandle) {
    blocker_state::do_enable(&app);
}

// ── Entry point ───────────────────────────────────────────────────────────────

fn main() {
    tauri::Builder::default()
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            get_status,
            cmd_set_pin,
            cmd_verify_pin,
            cmd_request_disable,
            cmd_confirm_disable,
            cmd_enable,
        ])
        .setup(|app| {
            // Hide the settings window on startup (tray-only app)
            if let Some(win) = app.get_webview_window("settings") {
                let _ = win.hide();
            }

            // Build system tray
            tray::build_tray(&app.handle())?;

            // Restore hosts state on startup
            startup_restore(app)?;

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running BlockCorn");
}

fn startup_restore(app: &tauri::App) -> Result<()> {
    let state = app.state::<AppState>();
    let config = state.config.lock().unwrap();
    let domains = config.blocklist.clone();
    drop(config);

    let path = hosts::hosts_path();

    // Self-heal: if hosts was cleared externally but filter should be active
    let currently_active = hosts::is_active(&path).unwrap_or(false);
    let should_be_active = *state.enabled.lock().unwrap();

    if should_be_active && !currently_active {
        if let Err(e) = hosts::apply_blocklist(&path, &domains) {
            eprintln!("[BlockCorn] startup_restore: failed to re-apply hosts: {e}");
        }
    }

    Ok(())
}
