package com.example.mytodolist2;

import java.io.Serializable;
import java.util.Date;

public class Task implements Serializable {
    private String text;
    private Date date;
    private String fileUri;
    private String reaction;
    private boolean done;

    public Task(String text, Date date, String fileUri) {
        this.text = text;
        this.date = date;
        this.fileUri = fileUri;
        this.reaction = null;
        this.done = false;
    }
    
    public Task(String text, Date date, String fileUri, String reaction) {
        this.text = text;
        this.date = date;
        this.fileUri = fileUri;
        this.reaction = reaction;
        this.done = false;
    }
    
    public Task(String text, Date date, String fileUri, String reaction, boolean done) {
        this.text = text;
        this.date = date;
        this.fileUri = fileUri;
        this.reaction = reaction;
        this.done = done;
    }

    public String getText() { return text; }
    public Date getDate() { return date; }
    public String getFileUri() { return fileUri; }
    public String getReaction() { return reaction; }
    public boolean isDone() { return done; }
    
    public void setReaction(String reaction) { this.reaction = reaction; }
    public void setDone(boolean done) { this.done = done; }
    public void setFileUri(String uri) { this.fileUri = uri; }

    public String getFormattedDate() {
        if (date == null) return "Без даты";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault());
        return sdf.format(date);
    }

    public boolean hasFile() {
        return fileUri != null && !fileUri.isEmpty();
    }
}