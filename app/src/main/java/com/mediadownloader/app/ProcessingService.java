package com.mediadownloader.app;

import android.app.*;
import android.content.*;
import android.os.*;

/** Mantém o processo em primeiro plano durante tarefas iniciadas na Activity.
 * Não impede Forçar parada, nem substitui um motor de jobs independente. */
public class ProcessingService extends Service {
  private static final String CHANNEL = "tubtok_processing";
  @Override public void onCreate() {
    super.onCreate();
    if(Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL, "Processamento do TubTok", NotificationManager.IMPORTANCE_LOW);
      ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
  }
  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String label = intent == null ? "Processando" : intent.getStringExtra("label");
    if(label == null) label = "Processando";
    Intent open = new Intent(this, MainActivity.class);
    open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
    Notification notification = builder.setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("TubTok — tarefa em andamento")
      .setContentText(label + " • toque para voltar")
      .setContentIntent(pending).setOngoing(true).build();
    startForeground(1209, notification);
    return START_NOT_STICKY;
  }
  @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
