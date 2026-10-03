package studio.ocean.app.models.local;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import studio.ocean.app.R;

/** Keeps the owned llama-server process alive while the user switches apps. */
public final class LocalInferenceService extends Service {
    public final class LocalBinder extends android.os.Binder { LocalInferenceService service() { return LocalInferenceService.this; } }
    private final LocalBinder binder = new LocalBinder();
    private android.os.PowerManager.WakeLock wakeLock;
    @Override public void onCreate() {
        super.onCreate();
        String channel = "ocean_local_inference";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                new NotificationChannel(channel, "Ocean local model", NotificationManager.IMPORTANCE_LOW));
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "OceanStudio:LocalInference");
        wakeLock.setReferenceCounted(false); wakeLock.acquire();
        updateState("Preparing the local inference server…");
    }
    public void updateState(String state) {
        String channel = "ocean_local_inference";
        Intent open = new Intent(this, LocalModelsActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 442, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        startForeground(442, builder.setSmallIcon(R.drawable.ic_spark)
                .setContentTitle("Ocean local inference")
                .setContentText(state.length() > 140 ? state.substring(0, 140) : state)
                .setContentIntent(pending).setOngoing(true).build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) LocalModelManager.getInstance(this).restoreAfterProcessDeath();
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onDestroy() {
        LocalModelManager manager = LocalModelManager.existingInstance();
        if (manager != null) manager.serviceDestroyed(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(true);
        super.onDestroy();
    }
}
