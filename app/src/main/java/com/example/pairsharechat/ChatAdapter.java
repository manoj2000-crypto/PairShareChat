package com.example.pairsharechat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
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
            SentViewHolder sentHolder = (SentViewHolder) holder;
            if (message.getFilePath() != null) {
                sentHolder.txtSent.setVisibility(View.GONE);
                sentHolder.imgPreview.setVisibility(View.VISIBLE);

                File file = new File(message.getFilePath());
                Glide.with(sentHolder.itemView.getContext()).load(file).placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_dialog_alert).into(sentHolder.imgPreview);
            } else {
                sentHolder.txtSent.setVisibility(View.VISIBLE);
                sentHolder.imgPreview.setVisibility(View.GONE);
                sentHolder.txtSent.setText(message.getText());
            }
        } else if (holder instanceof ReceivedViewHolder) {
            ReceivedViewHolder receivedHolder = (ReceivedViewHolder) holder;
            if (message.getFilePath() != null) {
                receivedHolder.txtReceived.setVisibility(View.GONE);
                receivedHolder.imgPreview.setVisibility(View.VISIBLE);

                File file = new File(message.getFilePath());
                Glide.with(receivedHolder.itemView.getContext()).load(file).placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_dialog_alert).into(receivedHolder.imgPreview);
            } else {
                receivedHolder.txtReceived.setVisibility(View.VISIBLE);
                receivedHolder.imgPreview.setVisibility(View.GONE);
                receivedHolder.txtReceived.setText(message.getText());
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        TextView txtSent;
        ImageView imgPreview;

        SentViewHolder(View itemView) {
            super(itemView);
            txtSent = itemView.findViewById(R.id.tvSent);
            imgPreview = itemView.findViewById(R.id.imgSentPreview);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        TextView txtReceived;
        ImageView imgPreview;

        ReceivedViewHolder(View itemView) {
            super(itemView);
            txtReceived = itemView.findViewById(R.id.tvReceived);
            imgPreview = itemView.findViewById(R.id.imgReceivedPreview);
        }
    }
}