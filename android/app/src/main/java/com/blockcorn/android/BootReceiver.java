package com.blockcorn.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the VPN filter after device reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent vpnIntent = new Intent(context, BlockVpnService.class);
            vpnIntent.setAction(BlockVpnService.ACTION_START);
            context.startForegroundService(vpnIntent);
        }
    }
}
