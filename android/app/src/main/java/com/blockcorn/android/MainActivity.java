package com.blockcorn.android;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 1;

    private TextView  statusText;
    private Button    toggleBtn;

    private final ActivityResultLauncher<Intent> vpnPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                startVpn();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        toggleBtn  = findViewById(R.id.toggleBtn);

        toggleBtn.setOnClickListener(v -> handleToggle());

        // First run: prompt for PIN setup
        if (!PinManager.isSet(this)) {
            startActivity(new Intent(this, PinActivity.class));
        }

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void handleToggle() {
        if (isVpnRunning()) {
            initiateDisable();
        } else {
            requestVpnAndStart();
        }
    }

    private void requestVpnAndStart() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent);
        } else {
            startVpn();
        }
    }

    private void startVpn() {
        Intent intent = new Intent(this, BlockVpnService.class);
        intent.setAction(BlockVpnService.ACTION_START);
        startForegroundService(intent);
        updateUI();
    }

    private void stopVpn() {
        Intent intent = new Intent(this, BlockVpnService.class);
        intent.setAction(BlockVpnService.ACTION_STOP);
        startService(intent);
        updateUI();
    }

    private void initiateDisable() {
        if (DelayGuard.isReadyToDisable(this)) {
            // Delay expired: ask for PIN
            showPinDialog();
        } else if (DelayGuard.isPending(this)) {
            long secs  = DelayGuard.secondsRemaining(this);
            long hours = secs / 3600;
            long mins  = (secs % 3600) / 60;
            new AlertDialog.Builder(this)
                .setTitle("BlockCorn")
                .setMessage("Запрос уже отправлен.\nОсталось: " + hours + "ч " + mins + "мин.")
                .setPositiveButton("OK", null)
                .show();
        } else {
            // Start 24h countdown
            DelayGuard.requestDisable(this);
            new AlertDialog.Builder(this)
                .setTitle("BlockCorn")
                .setMessage("Запрос на отключение принят.\nФильтр будет доступен для отключения через 24 часа.\nПосле этого потребуется PIN.")
                .setPositiveButton("OK", null)
                .show();
        }
    }

    private void showPinDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                         | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Введите PIN");

        new AlertDialog.Builder(this)
            .setTitle("Подтвердите отключение")
            .setView(input)
            .setPositiveButton("Отключить", (d, w) -> {
                String pin = input.getText().toString();
                if (PinManager.verify(this, pin)) {
                    DelayGuard.clear(this);
                    stopVpn();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Ошибка")
                        .setMessage("Неверный PIN")
                        .setPositiveButton("OK", null)
                        .show();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private boolean isVpnRunning() {
        // Simple heuristic: check if VPN interface is active
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface iface = ifaces.nextElement();
                if (iface.getName().startsWith("tun")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void updateUI() {
        boolean running = isVpnRunning();
        statusText.setText(running ? "Фильтр активен ●" : "Фильтр отключён ○");
        statusText.setTextColor(getColor(running ? R.color.status_active : R.color.status_inactive));
        toggleBtn.setText(running ? "Отключить фильтр…" : "Включить фильтр");
    }
}
