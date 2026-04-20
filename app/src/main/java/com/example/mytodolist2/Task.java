package com.example.mytodolist2;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

public class Task implements Serializable {
    private String id;
    private String text;
    private Date date;
    private String fileUri;
    private String reaction;
    private boolean done;
    private int hour;
    private int minute;

    public Task(String text, Date date, String fileUri) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.date = date;
        this.fileUri = (fileUri != null && !fileUri.isEmpty() && !fileUri.equals("null") && fileUri.length() > 5) ? fileUri : null;
        this.reaction = null;
        this.done = false;
        this.hour = -1;
        this.minute = -1;
    }

    public Task(String text, Date date, String fileUri, String reaction, boolean done, int hour, int minute) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.date = date;
        this.fileUri = (fileUri != null && !fileUri.isEmpty() && !fileUri.equals("null") && fileUri.length() > 5) ? fileUri : null;
        this.reaction = (reaction != null && !reaction.isEmpty() && !reaction.equals("null")) ? reaction : null;
        this.done = done;
        this.hour = hour;
        this.minute = minute;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public Date getDate() { return date; }
    public String getFileUri() { return fileUri; }
    public String getReaction() { return reaction; }
    public boolean isDone() { return done; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public boolean hasTime() { return hour >= 0 && minute >= 0; }

    public void setReaction(String reaction) {
        this.reaction = (reaction != null && !reaction.isEmpty() && !reaction.equals("null")) ? reaction : null;
    }
    public void setDone(boolean done) { this.done = done; }
    public void setFileUri(String uri) {
        this.fileUri = (uri != null && !uri.isEmpty() && !uri.equals("null") && uri.length() > 5) ? uri : null;
    }
    public void setTime(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
    }

    public String getFormattedDate() {
        if (date == null) {
            if (hasTime()) {
                return String.format("%02d:%02d", hour, minute);
            }
            return "Без даты";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        String result = sdf.format(date);
        if (hasTime()) {
            result += " " + String.format("%02d:%02d", hour, minute);
        }
        return result;
    }

    public boolean hasFile() {
        return fileUri != null
                && !fileUri.isEmpty()
                && !fileUri.equals("null")
                && fileUri.length() > 5
                && (fileUri.startsWith("content://") || fileUri.startsWith("file://"));
    }

    public boolean hasValidReaction() {
        return reaction != null && !reaction.isEmpty() && !reaction.equals("null")
                && (reaction.equals("like") || reaction.equals("lightning") || reaction.equals("cat"));
    }

    public Calendar getNotificationCalendar() {
        Calendar cal = Calendar.getInstance();
        if (date != null) {
            cal.setTime(date);
        } else {
            cal.setTime(new Date());
        }
        if (hasTime()) {
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }

        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal;
    }
}