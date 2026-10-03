package studio.ocean.app.mcp;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import studio.ocean.app.PluginCenterActivity;
import studio.ocean.app.R;

/** Keeps the native OAuth callback listener alive while consent opens in the browser. */
public final class McpAuthorizationService extends Service {
    @Override public void onCreate() {
        super.onCreate();
        String channel = "ocean_mcp_authorization";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(channel,
                "Ocean account authorization", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, PluginCenterActivity.class).putExtra("hub_section", "mcps");
        PendingIntent pending = PendingIntent.getActivity(this, 443, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        startForeground(443, new Notification.Builder(this, channel).setSmallIcon(R.drawable.ic_connections)
                .setContentTitle("Connecting your MCP account")
                .setContentText("Complete authorization in your browser. Ocean will reconnect automatically.")
                .setContentIntent(pending).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { stopForeground(true); super.onDestroy(); }
}
