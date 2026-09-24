package com.capitalfoto.voicediary;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME = "TodoPrefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskText = intent.getStringExtra("task_text");
        String taskId = intent.getStringExtra("task_id");

        if (taskId == null || taskId.isEmpty()) {
            return;
        }

        /*
         * Do not trust an old PendingIntent blindly.
         * Check the current saved state before showing a notification.
         *
         * This protects against:
         * - a task completed just before the alarm fires;
         * - a task deleted just before the alarm fires;
         * - a stale PendingIntent left by an older app version.
         */
        if (!isTaskStillActive(context, taskId)) {
            return;
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );
        mainIntent.putExtra("open_task_id", taskId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                taskId.hashCode(),
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );

        if (taskText != null && !taskText.isEmpty()) {
            NotificationHelper.showNotification(
                    context,
                    taskText,
                    taskId,
                    pendingIntent
            );
        }
    }

    private boolean isTaskStillActive(Context context, String taskId) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int count = prefs.getInt("count", 0);

        for (int i = 0; i < count; i++) {
            String savedId = prefs.getString("id_" + i, null);

            if (taskId.equals(savedId)) {
                return !prefs.getBoolean("done_" + i, false);
            }
        }

        // ID not found means the task was deleted.
        return false;
    }
}
