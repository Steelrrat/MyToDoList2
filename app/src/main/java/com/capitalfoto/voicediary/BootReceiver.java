package com.capitalfoto.voicediary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Date;

import java.io.File;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();

        boolean supportedAction =
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (!supportedAction) {
            return;
        }

        SharedPreferences prefs =
                context.getSharedPreferences("TodoPrefs", Context.MODE_PRIVATE);

        int count = prefs.getInt("count", 0);

        for (int i = 0; i < count; i++) {
            String text = prefs.getString("task_" + i, "");
            long dateMillis = prefs.getLong("date_" + i, 0);
            String filePath = prefs.getString("file_" + i, null);
            String reaction = prefs.getString("reaction_" + i, null);
            boolean done = prefs.getBoolean("done_" + i, false);
            int hour = prefs.getInt("hour_" + i, -1);
            int minute = prefs.getInt("minute_" + i, -1);
            String id = prefs.getString("id_" + i, "");

            if (text == null || text.trim().isEmpty()) continue;
            if (done) continue;
            if (hour < 0 || minute < 0) continue;
            if (id == null || id.isEmpty()) continue;

            Date date = dateMillis > 0
                    ? new Date(dateMillis)
                    : null;

            Task task = new Task(
                    text,
                    date,
                    filePath,
                    reaction,
                    false,
                    hour,
                    minute
            );

            task.setId(id);

            NotificationHelper.scheduleNotification(context, task);
        }
    }
}
