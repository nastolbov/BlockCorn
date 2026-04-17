// Handles enable/disable flow including PIN check and 24h delay.

use tauri::AppHandle;

use crate::{delay, hosts, pin, AppState};

/// Called when user clicks "toggle" in tray.
pub fn handle_toggle(app: &AppHandle) {
    use tauri::Manager;
    let state = app.state::<AppState>();
    let enabled = *state.enabled.lock().unwrap();

    if enabled {
        initiate_disable(app);
    } else {
        do_enable(app);
    }
}

/// Start the disable flow: check delay state, request if new.
pub fn initiate_disable(app: &AppHandle) {
    use tauri::Manager;

    // Check if there's already a completed delay
    match delay::ready_to_disable() {
        Ok(Some(_)) => {
            // Delay expired — ask for PIN confirmation via settings window
            show_confirm_disable_window(app);
        }
        Ok(None) => {
            match delay::get() {
                Ok(Some(s)) => {
                    // Delay in progress — show remaining time
                    let secs = s.seconds_remaining();
                    let hours = secs / 3600;
                    let mins = (secs % 3600) / 60;
                    show_info_dialog(
                        app,
                        &format!(
                            "Отключение заблокировано.\nОсталось ждать: {}ч {}мин.\nПосле истечения откройте настройки и введите PIN.",
                            hours, mins
                        ),
                    );
                }
                Ok(None) => {
                    // No pending request — start the 24h countdown
                    match delay::request_disable() {
                        Ok(s) => {
                            let deadline = s.requested_at + chrono::Duration::hours(24);
                            show_info_dialog(
                                app,
                                &format!(
                                    "Запрос на отключение принят.\nФильтр будет доступен для отключения {}.\nПосле этого потребуется PIN.",
                                    deadline.format("%d.%m.%Y в %H:%M UTC")
                                ),
                            );
                        }
                        Err(e) => eprintln!("[BlockCorn] delay::request_disable error: {e}"),
                    }
                }
                Err(e) => eprintln!("[BlockCorn] delay::get error: {e}"),
            }
        }
        Err(e) => eprintln!("[BlockCorn] delay::ready_to_disable error: {e}"),
    }
}

/// Enable the filter immediately (no restriction on enabling).
pub fn do_enable(app: &AppHandle) {
    use tauri::Manager;
    let state = app.state::<AppState>();
    let config = state.config.lock().unwrap();
    let domains = config.blocklist.clone();
    drop(config);

    let path = hosts::hosts_path();
    match hosts::apply_blocklist(&path, &domains) {
        Ok(()) => {
            let mut enabled = state.enabled.lock().unwrap();
            *enabled = true;
            drop(enabled);
            crate::tray::refresh_tray(app);
        }
        Err(e) => eprintln!("[BlockCorn] Failed to apply hosts: {e}"),
    }
}

/// Confirm disable: verify PIN and then actually disable.
pub fn confirm_disable(app: &AppHandle, entered_pin: &str) -> Result<(), String> {
    use tauri::Manager;

    match pin::verify(entered_pin) {
        Ok(true) => {}
        Ok(false) => return Err("Неверный PIN".into()),
        Err(e) => return Err(format!("Ошибка проверки PIN: {e}")),
    }

    match delay::ready_to_disable() {
        Ok(None) => return Err("24-часовая задержка ещё не истекла".into()),
        Err(e) => return Err(format!("Ошибка проверки задержки: {e}")),
        Ok(Some(_)) => {}
    }

    let path = hosts::hosts_path();
    if let Err(e) = hosts::remove_blocklist(&path) {
        return Err(format!("Не удалось изменить hosts: {e}"));
    }
    let _ = delay::clear();

    let state = app.state::<AppState>();
    let mut enabled = state.enabled.lock().unwrap();
    *enabled = false;
    drop(enabled);
    crate::tray::refresh_tray(app);

    Ok(())
}

fn show_info_dialog(app: &AppHandle, message: &str) {
    // In a real build this opens a native dialog; for now log to stderr
    eprintln!("[BlockCorn] {message}");
    // TODO: replace with tauri dialog plugin or a dedicated mini-window
    let _ = app;
}

fn show_confirm_disable_window(app: &AppHandle) {
    use tauri::Manager;
    if let Some(win) = app.get_webview_window("settings") {
        let _ = win.show();
        let _ = win.set_focus();
    }
}
