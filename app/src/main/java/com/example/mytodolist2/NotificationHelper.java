package com.example.mytodolist2;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import java.util.Calendar;

public class NotificationHelper {

    private static final String CHANNEL_ID = "todo_reminder_channel";
    private static final String CHANNEL_NAME = "Напоминания о задачах";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о задачах");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            channel.enableLights(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setShowBadge(true);
            channel.setBypassDnd(true);
            manager.createNotificationChannel(channel);
        }
    }

    public static void scheduleNotification(Context context, Task task) {
        try {
            createNotificationChannel(context);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, NotificationReceiver.class);
            intent.putExtra("task_text", task.getText());
            intent.putExtra("task_id", task.getId());
            intent.putExtra("task_hour", task.getHour());
            intent.putExtra("task_minute", task.getMinute());

            int requestCode = task.getId().hashCode();
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags);

            Calendar calendar = task.getNotificationCalendar();
            if (calendar == null) {
                calendar = Calendar.getInstance();
                calendar.add(Calendar.MINUTE, 1);
            }

            long triggerTime = calendar.getTimeInMillis();

            // Если время в прошлом - ставим на завтра
            if (triggerTime <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                triggerTime = calendar.getTimeInMillis();
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                }

                android.util.Log.d("NotificationHelper", "Уведомление запланировано на: " + triggerTime);

            } catch (SecurityException e) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelNotification(Context context, String taskId) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, NotificationReceiver.class);
            int requestCode = taskId.hashCode();
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags);
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showNotification(Context context, String taskText, String taskId, PendingIntent pendingIntent) {
        try {
            createNotificationChannel(context);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            // Wake up screen
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
                    ? pm.isInteractive()
                    : pm.isScreenOn();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("🔔 НАПОМИНАНИЕ")
                    .setContentText(taskText)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 500, 200, 500, 200, 500})
                    .setLights(0xFF4CAF50, 1000, 1000)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(false)
                    .setFullScreenIntent(pendingIntent, true)
                    .setTimeoutAfter(30000);

            manager.notify(taskId.hashCode(), builder.build());

            android.util.Log.d("NotificationHelper", "Уведомление показано: " + taskText);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}