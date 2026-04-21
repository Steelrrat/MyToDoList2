package com.capitalfoto.voicediary;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String taskText = intent.getStringExtra("task_text");
        String taskId = intent.getStringExtra("task_id");
        
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mainIntent.putExtra("open_task_id", taskId);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, taskId.hashCode(), mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        if (taskText != null && !taskText.isEmpty()) {
            NotificationHelper.showNotification(context, taskText, taskId, pendingIntent);
        }
    }
}