package com.capitalfoto.voicediary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("TodoPrefs", Context.MODE_PRIVATE);
            int count = prefs.getInt("count", 0);

            for (int i = 0; i < count; i++) {
                String text = prefs.getString("task_" + i, "");
                long date = prefs.getLong("date_" + i, 0);
                int hour = prefs.getInt("hour_" + i, -1);
                int minute = prefs.getInt("minute_" + i, -1);
                String id = prefs.getString("id_" + i, "");
                String filePath = prefs.getString("file_" + i, null);

                // Проверяем, существует ли файл
                if (filePath != null) {
                    File file = new File(filePath);
                    if (!file.exists()) {
                        filePath = null;
                    }
                }

                if (hour >= 0 && minute >= 0 && !text.isEmpty()) {
                    Task task = new Task(text, date > 0 ? new java.util.Date(date) : null, filePath, null, false, hour, minute);
                    task.setId(id);
                    NotificationHelper.scheduleNotification(context, task);
                }
            }
        }
    }
}