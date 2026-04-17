package com.blockcorn.desktop;

import com.blockcorn.core.PinManager;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.BackingStoreException;

/**
 * Swing settings window — PIN setup, whitelist management, filter toggle.
 */
public final class SettingsUI {

    private SettingsUI() {}

    public static void show(AppState state, TrayApp tray) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("BlockCorn — Настройки");
            frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            frame.setSize(480, 360);
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);

            JPanel root = new JPanel(new BorderLayout(10, 10));
            root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            root.setBackground(new Color(15, 15, 19));

            root.add(buildPinPanel(state, tray, frame), BorderLayout.CENTER);

            frame.setContentPane(root);
            frame.setVisible(true);
        });
    }

    private static JPanel buildPinPanel(AppState state, TrayApp tray, JFrame frame) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(15, 15, 19));

        // Status row
        JLabel statusLabel = new JLabel(
            state.isEnabled() ? "Фильтр активен ●" : "Фильтр отключён ○"
        );
        statusLabel.setForeground(state.isEnabled() ? new Color(34, 197, 94) : new Color(239, 68, 68));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 15f));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // PIN section
        JLabel pinTitle = new JLabel(PinStore.isSet() ? "PIN установлен" : "PIN не установлен");
        pinTitle.setForeground(Color.WHITE);
        pinTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField pinField = new JPasswordField(20);
        pinField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        pinField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton setPinBtn = styledButton("Установить / изменить PIN");
        setPinBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        setPinBtn.addActionListener(e -> {
            String pin = new String(pinField.getPassword()).trim();
            if (pin.length() < 4) {
                JOptionPane.showMessageDialog(frame, "PIN должен быть не менее 4 символов.",
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                PinStore.saveHash(PinManager.hash(pin));
                pinTitle.setText("PIN установлен ✓");
                pinField.setText("");
                JOptionPane.showMessageDialog(frame, "PIN сохранён.", "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
            } catch (BackingStoreException ex) {
                JOptionPane.showMessageDialog(frame, "Ошибка сохранения PIN: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Assemble
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(20));
        panel.add(pinTitle);
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("Новый PIN:") {{ setForeground(Color.LIGHT_GRAY); setAlignmentX(LEFT_ALIGNMENT); }});
        panel.add(Box.createVerticalStrut(4));
        panel.add(pinField);
        panel.add(Box.createVerticalStrut(8));
        panel.add(setPinBtn);
        panel.add(Box.createVerticalStrut(24));

        // Autostart row
        JButton autoStartBtn = styledButton("Включить автозапуск");
        autoStartBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoStartBtn.addActionListener(e -> {
            String jar = System.getProperty("java.class.path");
            AutoStart.enable(jar);
            JOptionPane.showMessageDialog(frame, "Автозапуск настроен.", "BlockCorn", JOptionPane.INFORMATION_MESSAGE);
        });
        panel.add(autoStartBtn);

        return panel;
    }

    private static JButton styledButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(108, 52, 235));
        b.setForeground(Color.WHITE);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        return b;
    }
}
