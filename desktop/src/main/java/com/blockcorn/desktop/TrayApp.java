package com.blockcorn.desktop;

import com.blockcorn.core.DelayState;
import com.blockcorn.core.PinManager;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.BackingStoreException;

/**
 * System-tray icon and menu for Windows and macOS.
 * Uses java.awt.SystemTray — no extra dependencies, ships with every JDK.
 */
public final class TrayApp {

    private final AppState state;
    private TrayIcon trayIcon;
    private MenuItem toggleItem;
    private MenuItem statusItem;

    public TrayApp(AppState state) {
        this.state = state;
    }

    /** Must be called on the AWT Event Dispatch Thread. */
    public void init() throws AWTException {
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException("System tray is not supported on this platform");
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image icon = loadIcon();

        PopupMenu menu = buildMenu();
        trayIcon = new TrayIcon(icon, "BlockCorn", menu);
        trayIcon.setImageAutoSize(true);
        tray.add(trayIcon);

        refreshMenu();
    }

    public void refreshMenu() {
        boolean enabled = state.isEnabled();
        if (statusItem != null) {
            statusItem.setLabel(enabled ? "● Фильтр активен" : "○ Фильтр отключён");
        }
        if (toggleItem != null) {
            toggleItem.setLabel(enabled ? "Отключить фильтр…" : "Включить фильтр");
        }
        if (trayIcon != null) {
            trayIcon.setToolTip(enabled ? "BlockCorn — фильтр активен" : "BlockCorn — отключён");
        }
    }

    private PopupMenu buildMenu() {
        statusItem = new MenuItem("…");
        statusItem.setEnabled(false);

        toggleItem = new MenuItem("…");
        toggleItem.addActionListener(e -> handleToggle());

        MenuItem settingsItem = new MenuItem("Настройки…");
        settingsItem.addActionListener(e -> SettingsUI.show(state, this));

        MenuItem quitItem = new MenuItem("Выйти");
        quitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        });

        PopupMenu menu = new PopupMenu();
        menu.add(statusItem);
        menu.addSeparator();
        menu.add(toggleItem);
        menu.addSeparator();
        menu.add(settingsItem);
        menu.add(quitItem);
        return menu;
    }

    private void handleToggle() {
        if (state.isEnabled()) {
            initiateDisable();
        } else {
            doEnable();
        }
    }

    private void doEnable() {
        try {
            HostsManager.applyBlocklist(HostsManager.hostsPath(), state.getDomains());
            state.setEnabled(true);
            refreshMenu();
        } catch (IOException e) {
            showError("Не удалось изменить hosts файл: " + e.getMessage());
        }
    }

    private void initiateDisable() {
        DelayState pending = DelayStore.read();

        if (pending != null && pending.isExpired()) {
            // Delay done — ask for PIN
            String pin = JOptionPane.showInputDialog(null,
                "Введите PIN для отключения фильтра:", "BlockCorn — PIN", JOptionPane.PLAIN_MESSAGE);
            if (pin == null) return;

            String storedHash = PinStore.loadHash();
            if (storedHash == null || !PinManager.verify(pin, storedHash)) {
                showError("Неверный PIN");
                return;
            }

            confirmDisable();

        } else if (pending != null) {
            long hours = pending.secondsRemaining() / 3600;
            long mins  = (pending.secondsRemaining() % 3600) / 60;
            JOptionPane.showMessageDialog(null,
                "Запрос уже отправлен.\nОсталось: %dч %dмин.".formatted(hours, mins),
                "BlockCorn", JOptionPane.INFORMATION_MESSAGE);

        } else {
            // Start 24h countdown
            DelayState s = new DelayState();
            DelayStore.write(s);
            java.time.Instant deadline = s.getRequestedAt()
                .plus(DelayState.DELAY_HOURS, java.time.temporal.ChronoUnit.HOURS);
            JOptionPane.showMessageDialog(null,
                "Запрос принят.\nОтключение будет доступно:\n" + deadline + "\nПосле этого потребуется PIN.",
                "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void confirmDisable() {
        try {
            HostsManager.removeBlocklist(HostsManager.hostsPath());
            DelayStore.clear();
            state.setEnabled(false);
            refreshMenu();
        } catch (IOException e) {
            showError("Не удалось изменить hosts файл: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(null, msg, "BlockCorn — Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private Image loadIcon() {
        URL url = getClass().getResource("/icons/tray.png");
        if (url != null) return Toolkit.getDefaultToolkit().getImage(url);
        // Fallback: 16×16 plain image
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(108, 52, 235));
        g.fillOval(1, 1, 14, 14);
        g.dispose();
        return img;
    }
}
