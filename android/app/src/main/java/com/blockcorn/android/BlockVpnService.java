package com.blockcorn.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Local VPN service that intercepts DNS queries (UDP port 53) and
 * returns NXDOMAIN for domains in the blocklist.
 *
 * Routes only DNS traffic through the tunnel — regular TCP/UDP is unaffected.
 */
public class BlockVpnService extends VpnService {

    public static final String ACTION_START = "com.blockcorn.android.START_VPN";
    public static final String ACTION_STOP  = "com.blockcorn.android.STOP_VPN";

    private static final String CHANNEL_ID   = "blockcorn_vpn";
    private static final int    NOTIF_ID     = 1;
    private static final String VPN_ADDRESS  = "10.111.222.1";
    private static final String DNS_INTERCEPT = "10.111.222.2";

    private ParcelFileDescriptor vpnInterface;
    private Thread               workerThread;
    private volatile boolean     running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    // ── VPN lifecycle ─────────────────────────────────────────────────────────

    private void startVpn() {
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Фильтр активен"));

        // Load blocklist (may trigger network if cache is stale)
        Thread loaderThread = new Thread(() -> {
            BlocklistManager.getInstance(this).loadOrFetch();
            startTunnel();
        }, "blockcorn-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    private void startTunnel() {
        try {
            Builder builder = new Builder();
            builder.setSession("BlockCorn")
                   .addAddress(VPN_ADDRESS, 24)
                   .addDnsServer(DNS_INTERCEPT)
                   .addRoute(DNS_INTERCEPT, 32) // only intercept DNS traffic
                   .setMtu(1500)
                   .setBlocking(false);

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                android.util.Log.e("BlockVpnService", "VPN establish() returned null — permission denied?");
                return;
            }

            running = true;
            workerThread = new Thread(this::runLoop, "blockcorn-vpn");
            workerThread.setDaemon(true);
            workerThread.start();

            android.util.Log.i("BlockVpnService", "VPN tunnel started");
        } catch (Exception e) {
            android.util.Log.e("BlockVpnService", "startTunnel failed", e);
        }
    }

    private void runLoop() {
        byte[] packet = new byte[32767];
        BlocklistManager blocklist = BlocklistManager.getInstance();

        try (FileInputStream  in  = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            while (running && !Thread.currentThread().isInterrupted()) {
                int len = in.read(packet);
                if (len <= 0) continue;

                byte[] response = DnsPacketProcessor.process(packet, len, blocklist);
                if (response != null) {
                    out.write(response);
                }
            }
        } catch (IOException e) {
            if (running) {
                android.util.Log.e("BlockVpnService", "VPN loop error", e);
            }
        }
    }

    private void stopVpn() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (IOException ignored) {}
            vpnInterface = null;
        }
        android.util.Log.i("BlockVpnService", "VPN tunnel stopped");
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private Notification buildNotification(String status) {
        Intent stopIntent = new Intent(this, BlockVpnService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockCorn")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPi)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "BlockCorn VPN", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Фильтр контента активен");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }
}
