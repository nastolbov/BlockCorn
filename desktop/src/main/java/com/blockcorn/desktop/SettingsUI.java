package com.blockcorn.desktop;

import com.blockcorn.core.DelayState;
import com.blockcorn.core.PinManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.prefs.BackingStoreException;

/**
 * Main application window — shown on startup.
 * Contains a prominent enable/disable toggle button plus PIN and service management.
 */
public final class SettingsUI {

    // ---- Dark theme palette ----
    private static final Color BG       = new Color(15,  15,  19);
    private static final Color SURFACE  = new Color(26,  26,  36);
    private static final Color BORDER   = new Color(46,  46,  64);
    private static final Color ACCENT   = new Color(108, 52,  235);
    private static final Color GREEN    = new Color(34,  197, 94);
    private static final Color RED      = new Color(239, 68,  68);
    private static final Color MUTED    = new Color(124, 124, 154);
    private static final Color TEXT     = new Color(228, 228, 240);

    private static JFrame frame;

    private SettingsUI() {}

    /** Open (or bring to front) the main window. */
    public static void show(AppState state, TrayApp tray) {
        SwingUtilities.invokeLater(() -> {
            if (frame != null && frame.isDisplayable()) {
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();
                return;
            }
            frame = buildFrame(state, tray);
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
        });
    }

    private static JFrame buildFrame(AppState state, TrayApp tray) {
        JFrame f = new JFrame("BlockCorn");
        // EXIT_ON_CLOSE so the process ends when window is closed (no tray on macOS by default)
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setSize(400, 560);
        f.setLocationRelativeTo(null);
        f.setResizable(false);
        f.setAlwaysOnTop(true);
        f.getContentPane().setBackground(BG);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(24, 24, 24, 24));

        // ── Header ──
        root.add(buildHeader());
        root.add(vgap(20));

        // ── Big toggle button ──
        JButton toggleBtn = buildToggleButton(state);
        root.add(toggleBtn);
        root.add(vgap(8));

        // ── Status label ──
        JLabel statusLbl = buildStatusLabel(state);
        root.add(statusLbl);
        root.add(vgap(24));

        // ── Divider ──
        root.add(divider());
        root.add(vgap(20));

        // ── PIN section ──
        root.add(buildPinSection(f, state));
        root.add(vgap(20));

        // ── Divider ──
        root.add(divider());
        root.add(vgap(16));

        // ── Service / autostart buttons ──
        root.add(buildServiceSection(f));

        // Wire toggle button action
        toggleBtn.addActionListener(e -> handleToggle(state, tray, toggleBtn, statusLbl));

        f.setContentPane(root);
        return f;
    }

    // ---------------------------------------------------------------- UI builders

    private static JPanel buildHeader() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setBackground(BG);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel logo = new JLabel("🌽");
        logo.setFont(logo.getFont().deriveFont(28f));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBackground(BG);
        JLabel title = label("BlockCorn", TEXT, Font.BOLD, 17f);
        JLabel sub   = label("Фильтр контента", MUTED, Font.PLAIN, 12f);
        text.add(title);
        text.add(sub);

        p.add(logo);
        p.add(hgap(10));
        p.add(text);
        return p;
    }

    private static JButton buildToggleButton(AppState state) {
        boolean on = state.isEnabled();
        JButton btn = new JButton(on ? "Выключить фильтр" : "Включить фильтр");
        styleToggleBtn(btn, on);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 15f));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static JLabel buildStatusLabel(AppState state) {
        boolean on = state.isEnabled();
        JLabel lbl = new JLabel(on ? "● Фильтр активен" : "○ Фильтр отключён");
        lbl.setForeground(on ? GREEN : RED);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 13f));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private static JPanel buildPinSection(JFrame f, AppState state) {
        JPanel p = column();

        JLabel title = label(PinStore.isSet() ? "PIN установлен ✓" : "PIN не установлен", TEXT, Font.BOLD, 13f);
        title.setName("pinTitle");

        JLabel hint = label("Новый PIN (мин. 4 символа):", MUTED, Font.PLAIN, 11f);

        JPasswordField pinField = new JPasswordField();
        pinField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        pinField.setAlignmentX(Component.LEFT_ALIGNMENT);
        styleField(pinField);

        JButton setPinBtn = accentButton("Установить / изменить PIN");
        setPinBtn.addActionListener(e -> {
            String pin = new String(pinField.getPassword()).trim();
            if (pin.length() < 4) {
                JOptionPane.showMessageDialog(f, "PIN должен быть не менее 4 символов.", "Ошибка", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                PinStore.saveHash(PinManager.hash(pin));
                title.setText("PIN установлен ✓");
                pinField.setText("");
                JOptionPane.showMessageDialog(f, "PIN сохранён.", "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
            } catch (BackingStoreException ex) {
                JOptionPane.showMessageDialog(f, "Ошибка сохранения: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        p.add(title);
        p.add(vgap(8));
        p.add(hint);
        p.add(vgap(4));
        p.add(pinField);
        p.add(vgap(8));
        p.add(setPinBtn);
        return p;
    }

    private static JPanel buildServiceSection(JFrame f) {
        JPanel p = column();

        JButton autoStartBtn = ghostButton("↺  Включить автозапуск");
        autoStartBtn.addActionListener(e -> {
            AutoStart.enable(System.getProperty("java.class.path", "blockcorn-desktop.jar"));
            JOptionPane.showMessageDialog(f, "Автозапуск настроен.", "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel svcRow = new JPanel(new GridLayout(1, 2, 8, 0));
        svcRow.setBackground(BG);
        svcRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        svcRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JButton installBtn   = ghostButton("Установить службу");
        JButton uninstallBtn = ghostButton("Удалить службу");
        uninstallBtn.setForeground(RED);

        installBtn.addActionListener(e   -> installService(f));
        uninstallBtn.addActionListener(e -> uninstallService(f));

        svcRow.add(installBtn);
        svcRow.add(uninstallBtn);

        p.add(autoStartBtn);
        p.add(vgap(8));
        p.add(svcRow);
        return p;
    }

    // ---------------------------------------------------------------- Toggle logic

    private static void handleToggle(AppState state, TrayApp tray,
                                     JButton btn, JLabel statusLbl) {
        if (state.isEnabled()) {
            initiateDisable(state, tray, btn, statusLbl);
        } else {
            doEnable(state, tray, btn, statusLbl);
        }
    }

    private static void doEnable(AppState state, TrayApp tray,
                                  JButton btn, JLabel statusLbl) {
        try {
            HostsManager.applyBlocklist(HostsManager.hostsPath(), state.getDomains());
            state.setEnabled(true);
            styleToggleBtn(btn, true);
            btn.setText("Выключить фильтр");
            statusLbl.setText("● Фильтр активен");
            statusLbl.setForeground(GREEN);
            if (tray != null) tray.refreshMenu();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                "Не удалось изменить hosts файл: " + e.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void initiateDisable(AppState state, TrayApp tray,
                                         JButton btn, JLabel statusLbl) {
        DelayState pending = DelayStore.read();

        if (pending != null && !pending.isExpired()) {
            long hours = pending.secondsRemaining() / 3600;
            long mins  = (pending.secondsRemaining() % 3600) / 60;
            JOptionPane.showMessageDialog(null,
                "Запрос уже отправлен.\nОсталось: %dч %dмин.".formatted(hours, mins),
                "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (pending != null && pending.isExpired()) {
            // Delay elapsed — require PIN
            String pin = JOptionPane.showInputDialog(null,
                "Введите PIN для отключения фильтра:", "BlockCorn — PIN", JOptionPane.PLAIN_MESSAGE);
            if (pin == null) return;
            String hash = PinStore.loadHash();
            if (hash == null || !PinManager.verify(pin, hash)) {
                JOptionPane.showMessageDialog(null, "Неверный PIN", "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            doDisable(state, tray, btn, statusLbl);
            return;
        }

        // No pending request — start 24h countdown
        DelayState s = new DelayState();
        DelayStore.write(s);
        java.time.Instant deadline = s.getRequestedAt()
            .plus(DelayState.DELAY_HOURS, java.time.temporal.ChronoUnit.HOURS);
        JOptionPane.showMessageDialog(null,
            "Запрос принят.\nОтключение станет доступно:\n" + deadline
            + "\nПосле этого потребуется ввести PIN.",
            "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void doDisable(AppState state, TrayApp tray,
                                   JButton btn, JLabel statusLbl) {
        try {
            HostsManager.removeBlocklist(HostsManager.hostsPath());
            DelayStore.clear();
            state.setEnabled(false);
            styleToggleBtn(btn, false);
            btn.setText("Включить фильтр");
            statusLbl.setText("○ Фильтр отключён");
            statusLbl.setForeground(RED);
            if (tray != null) tray.refreshMenu();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                "Не удалось изменить hosts файл: " + e.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- Service helpers

    private static void installService(JFrame f) {
        try {
            String cp  = System.getProperty("java.class.path", "blockcorn-desktop.jar");
            String main = cp.split(java.io.File.pathSeparator)[0];
            String wd   = main.replace("desktop", "watchdog");
            String os   = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                ServiceInstaller.installWindows(main, wd);
            } else if (os.contains("mac")) {
                ServiceInstaller.installMac(main, wd);
            } else {
                JOptionPane.showMessageDialog(f, "Служба поддерживается только на Windows и macOS.",
                    "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(f, "Служба установлена.", "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(f, "Ошибка: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void uninstallService(JFrame f) {
        int ok = JOptionPane.showConfirmDialog(f,
            "Удалить BlockCorn из автозапуска системы?", "BlockCorn",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win"))      ServiceInstaller.uninstallWindows();
            else if (os.contains("mac")) ServiceInstaller.uninstallMac();
            JOptionPane.showMessageDialog(f, "Служба удалена.", "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(f, "Ошибка: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- Style helpers

    private static void styleToggleBtn(JButton btn, boolean enabled) {
        btn.setBackground(enabled ? RED : GREEN);
        btn.setForeground(Color.WHITE);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(new EmptyBorder(14, 20, 14, 20));
    }

    private static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(new EmptyBorder(8, 14, 8, 14));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton ghostButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(SURFACE);
        b.setForeground(MUTED);
        b.setBorder(BorderFactory.createLineBorder(BORDER));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void styleField(JTextField f) {
        f.setBackground(SURFACE);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private static JLabel label(String text, Color color, int style, float size) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(style, size));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JSeparator divider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sep;
    }

    private static Component vgap(int h) {
        return Box.createVerticalStrut(h);
    }

    private static Component hgap(int w) {
        return Box.createHorizontalStrut(w);
    }
}
