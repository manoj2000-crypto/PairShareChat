package com.example.pairsharechat;

public class Message {
    private final String text;
    private final boolean sentByMe;
    private final String filePath;

    public Message(String text, boolean sentByMe, String filePath) {
        this.text = text;
        this.sentByMe = sentByMe;
        this.filePath = filePath;
    }

    public String getText() {
        return text;
    }

    public boolean isSentByMe() {
        return sentByMe;
    }

    public String getFilePath() {
        return filePath;
    }
}