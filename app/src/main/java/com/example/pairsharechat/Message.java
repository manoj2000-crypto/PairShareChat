package com.example.pairsharechat;

public class Message {
    private final String text;
    private final boolean sentByMe;

    public Message(String text, boolean sentByMe) {
        this.text = text;
        this.sentByMe = sentByMe;
    }

    public String getText() {
        return text;
    }

    public boolean isSentByMe() {
        return sentByMe;
    }
}