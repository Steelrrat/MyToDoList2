package com.capitalfoto.voicediary;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

public class Task implements Serializable {
    private String id;
    private String text;
    private Date date;
    private String filePath;  // ИЗМЕНЕНО: fileUri -> filePath
    private String reaction;
    private boolean done;
    private int hour;
    private int minute;

    private String selectedEmoji;

    public Task(String text, Date date, String filePath) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.date = date;
        this.filePath = (filePath != null && !filePath.isEmpty() && !filePath.equals("null") && filePath.length() > 5) ? filePath : null;
        this.reaction = null;
        this.done = false;
        this.hour = -1;
        this.minute = -1;
        this.selectedEmoji = null;
    }

    public Task(String text, Date date, String filePath, String reaction, boolean done, int hour, int minute) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.date = date;
        this.filePath = (filePath != null && !filePath.isEmpty() && !filePath.equals("null") && filePath.length() > 5) ? filePath : null;
        this.reaction = (reaction != null && !reaction.isEmpty() && !reaction.equals("null")) ? reaction : null;
        this.done = done;
        this.hour = hour;
        this.minute = minute;
        this.selectedEmoji = null;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public Date getDate() { return date; }
    public String getFilePath() { return filePath; }  // ИЗМЕНЕНО
    public String getReaction() { return reaction; }
    public boolean isDone() { return done; }
    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public boolean hasTime() { return hour >= 0 && minute >= 0; }

    public void setReaction(String reaction) {
        // Сохраняем как есть - это может быть эмодзи или null
        this.reaction = (reaction != null && !reaction.isEmpty() && !reaction.equals("null")) ? reaction : null;
    }
    public void setDone(boolean done) { this.done = done; }
    public void setFilePath(String path) {  // ИЗМЕНЕНО
        this.filePath = (path != null && !path.isEmpty() && !path.equals("null") && path.length() > 5) ? path : null;
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
        return filePath != null
                && !filePath.isEmpty()
                && !filePath.equals("null")
                && filePath.length() > 5;
    }

    public boolean hasValidReaction() {
        if (reaction == null || reaction.isEmpty() || reaction.equals("null")) {
            return false;
        }
        return reaction.equals("like") || reaction.equals("lightning") || reaction.equals("cat");
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

    public String getSelectedEmoji() {
        return selectedEmoji;
    }

    public void setSelectedEmoji(String selectedEmoji) {
        this.selectedEmoji = selectedEmoji;
    }
}