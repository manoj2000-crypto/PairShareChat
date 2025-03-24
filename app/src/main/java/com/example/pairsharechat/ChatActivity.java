package com.example.pairsharechat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int TCP_PORT = 8888;

    private RecyclerView rvChat;
    private EditText etMessage;
    private ChatAdapter chatAdapter;
    private final List<Message> messageList = new ArrayList<>();

    private String groupOwnerAddress;
    private boolean isGroupOwner;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter clientOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        MaterialButton btnSend = findViewById(R.id.btnSend);

        chatAdapter = new ChatAdapter(messageList);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        MaterialButton btnDisconnect = findViewById(R.id.btnDisconnect);
        btnDisconnect.setOnClickListener(v -> disconnect());

        groupOwnerAddress = getIntent().getStringExtra("groupOwnerAddress");
        isGroupOwner = getIntent().getBooleanExtra("isGroupOwner", false);
        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();

        if (isGroupOwner) {
            startServer();
        } else {
            startClient();
        }

        btnSend.setOnClickListener(v -> {
            String message = etMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                etMessage.setText("");
            }
        });
    }

    private void disconnect() {
        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error during disconnection", e);
        }
        finish(); // Optionally close the activity
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                handler.post(() -> Toast.makeText(this, "Server started, waiting for client...", Toast.LENGTH_SHORT).show());
                Socket client = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                // Save output stream for sending messages
                clientOut = out;
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    handler.post(() -> {
                        messageList.add(new Message(finalLine, false));
                        chatAdapter.notifyItemInserted(messageList.size() - 1);
                        rvChat.scrollToPosition(messageList.size() - 1);
                    });
                }
                in.close();
                client.close();
            } catch (IOException e) {
                if (e instanceof java.net.SocketException && e.getMessage().equals("Socket closed")) {
                    Log.i(TAG, "Socket closed gracefully.");
                } else {
                    Log.e(TAG, "Client error", e);
                }
            } finally {
                try {
                    if (serverSocket != null) serverSocket.close();
                } catch (IOException ignored) {
                }
            }
        }).start();
    }

    private void startClient() {
        new Thread(() -> {
            try {
                clientSocket = new Socket(groupOwnerAddress, TCP_PORT);
                clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    handler.post(() -> {
                        messageList.add(new Message(finalLine, false));
                        chatAdapter.notifyItemInserted(messageList.size() - 1);
                        rvChat.scrollToPosition(messageList.size() - 1);
                    });
                }
                in.close();
                clientSocket.close();
            } catch (IOException e) {
                if (e instanceof java.net.SocketException && e.getMessage().equals("Socket closed")) {
                    Log.i(TAG, "Socket closed gracefully.");
                } else {
                    Log.e(TAG, "Client error", e);
                }
            }
        }).start();
    }

    private void sendMessage(String message) {
        new Thread(() -> {
            try {
                if (clientOut != null) {
                    clientOut.println(message);
                }
                handler.post(() -> {
                    messageList.add(new Message(message, true));
                    chatAdapter.notifyItemInserted(messageList.size() - 1);
                    rvChat.scrollToPosition(messageList.size() - 1);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (serverSocket != null) serverSocket.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {
        }
    }
}