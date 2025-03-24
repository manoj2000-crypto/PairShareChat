package com.example.pairsharechat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Message> messages;
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isSentByMe() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).txtSent.setText(message.getText());
        } else {
            ((ReceivedViewHolder) holder).txtReceived.setText(message.getText());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView txtSent;

        SentViewHolder(View itemView) {
            super(itemView);
            txtSent = itemView.findViewById(R.id.tvSent);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView txtReceived;

        ReceivedViewHolder(View itemView) {
            super(itemView);
            txtReceived = itemView.findViewById(R.id.tvReceived);
        }
    }
}