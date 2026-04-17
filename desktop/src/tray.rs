use anyhow::Result;
use tauri::{
    menu::{MenuBuilder, MenuItemBuilder},
    tray::TrayIconBuilder,
    AppHandle, Manager,
};

use crate::{blocker_state, AppState};

pub fn build_tray(app: &AppHandle) -> Result<()> {
    let state = app.state::<AppState>();
    let enabled = *state.enabled.lock().unwrap();

    let status_item = MenuItemBuilder::with_id(
        "status",
        if enabled { "● Фильтр активен" } else { "○ Фильтр отключён" },
    )
    .enabled(false)
    .build(app)?;

    let sep1 = tauri::menu::PredefinedMenuItem::separator(app)?;

    let toggle_item = MenuItemBuilder::with_id(
        "toggle",
        if enabled { "Отключить фильтр…" } else { "Включить фильтр" },
    )
    .build(app)?;

    let sep2 = tauri::menu::PredefinedMenuItem::separator(app)?;

    let settings_item = MenuItemBuilder::with_id("settings", "Настройки…").build(app)?;
    let quit_item = MenuItemBuilder::with_id("quit", "Выйти").build(app)?;

    let menu = MenuBuilder::new(app)
        .items(&[&status_item, &sep1, &toggle_item, &sep2, &settings_item, &quit_item])
        .build()?;

    TrayIconBuilder::with_id("tray")
        .icon(app.default_window_icon().cloned().unwrap_or_else(|| {
            tauri::image::Image::from_bytes(include_bytes!("../icons/tray.png"))
                .expect("embedded tray icon")
        }))
        .menu(&menu)
        .tooltip(if enabled {
            "BlockCorn — фильтр активен"
        } else {
            "BlockCorn — фильтр отключён"
        })
        .on_menu_event(|app, event| {
            match event.id.as_ref() {
                "toggle" => blocker_state::handle_toggle(app),
                "settings" => {
                    if let Some(win) = app.get_webview_window("settings") {
                        let _ = win.show();
                        let _ = win.set_focus();
                    }
                }
                "quit" => app.exit(0),
                _ => {}
            }
        })
        .build(app)?;

    Ok(())
}

/// Rebuild the tray menu to reflect current enabled state.
pub fn refresh_tray(app: &AppHandle) {
    // Tauri 2: get tray by id and update its menu
    if let Some(tray) = app.tray_by_id("tray") {
        let state = app.state::<AppState>();
        let enabled = *state.enabled.lock().unwrap();

        let tooltip = if enabled {
            "BlockCorn — фильтр активен"
        } else {
            "BlockCorn — фильтр отключён"
        };
        let _ = tray.set_tooltip(Some(tooltip));

        // Recreate menu items with updated labels
        let _ = build_tray_menu(app, enabled).map(|menu| tray.set_menu(Some(menu)));
    }
}

fn build_tray_menu(
    app: &AppHandle,
    enabled: bool,
) -> Result<tauri::menu::Menu<tauri::Wry>> {
    let status = MenuItemBuilder::with_id(
        "status",
        if enabled { "● Фильтр активен" } else { "○ Фильтр отключён" },
    )
    .enabled(false)
    .build(app)?;

    let sep1 = tauri::menu::PredefinedMenuItem::separator(app)?;

    let toggle = MenuItemBuilder::with_id(
        "toggle",
        if enabled { "Отключить фильтр…" } else { "Включить фильтр" },
    )
    .build(app)?;

    let sep2 = tauri::menu::PredefinedMenuItem::separator(app)?;

    let settings = MenuItemBuilder::with_id("settings", "Настройки…").build(app)?;
    let quit = MenuItemBuilder::with_id("quit", "Выйти").build(app)?;

    Ok(MenuBuilder::new(app)
        .items(&[&status, &sep1, &toggle, &sep2, &settings, &quit])
        .build()?)
}
