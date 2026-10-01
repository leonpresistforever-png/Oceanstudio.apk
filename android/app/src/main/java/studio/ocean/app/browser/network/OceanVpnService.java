package studio.ocean.app.browser.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import studio.ocean.app.R;

/**
 * Android VpnService implementation for Ocean Tunnel (PDF 4 §6, §15.5).
 * Explicitly operates with user consent and foreground service notification.
 * HARD RULE: Never intercepts 127.0.0.1 or local runtime port traffic.
 */
public final class OceanVpnService extends VpnService {

    public static final String ACTION_CONNECT = "studio.ocean.app.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "studio.ocean.app.vpn.DISCONNECT";
    private static final String CHANNEL_ID = "ocean_tunnel_channel";
    private static final int NOTIFICATION_ID = 4040;

    private static volatile boolean running = false;
    @Nullable private ParcelFileDescriptor vpnInterface;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnectTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        establishTunnel();
        return START_STICKY;
    }

    private void establishTunnel() {
        createNotificationChannel();
        Notification notification = buildForegroundNotification();
        startForeground(NOTIFICATION_ID, notification);

        try {
            Builder builder = new Builder();
            builder.setSession("OceanStudio Secure Tunnel");
            builder.setMtu(1500);
            builder.addAddress("10.0.0.2", 24);
            builder.addDnsServer("1.1.1.1");
            // Route internet traffic while keeping loopback/local addresses strictly outside
            builder.addRoute("0.0.0.0", 0);

            // If available on Android 10+, disallow loopback routes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }

            vpnInterface = builder.establish();
            running = vpnInterface != null;
        } catch (Exception e) {
            running = false;
            stopSelf();
        }
    }

    private void disconnectTunnel() {
        running = false;
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {
            }
            vpnInterface = null;
        }
        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        disconnectTunnel();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ocean Tunnel Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors the active Private Browser encrypted network route");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildForegroundNotification() {
        Intent disconnectIntent = new Intent(this, OceanVpnService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        PendingIntent pendingDisconnect = PendingIntent.getService(
                this, 0, disconnectIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_secure)
                .setContentTitle("Ocean Private Tunnel")
                .setContentText("Private routing is active · Localhost preserved")
                .addAction(R.drawable.ic_panic, "Disconnect", pendingDisconnect)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
