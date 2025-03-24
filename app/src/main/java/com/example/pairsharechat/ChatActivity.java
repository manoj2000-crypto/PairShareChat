package com.example.pairsharechat;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int TCP_PORT = 8888;

    private TextView tvChat;
    private EditText etMessage;
    private String groupOwnerAddress;
    private boolean isGroupOwner;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // For the server socket (if group owner)
    private ServerSocket serverSocket;

    // For client connection
    private Socket clientSocket;
    private PrintWriter clientOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        tvChat = findViewById(R.id.tvChat);
        etMessage = findViewById(R.id.etMessage);
        Button btnSend = findViewById(R.id.btnSend);

        // Retrieve connection info from intent
        groupOwnerAddress = getIntent().getStringExtra("groupOwnerAddress");
        isGroupOwner = getIntent().getBooleanExtra("isGroupOwner", false);
        Log.d(TAG, "onCreate: isGroupOwner = " + isGroupOwner + ", groupOwnerAddress = " + groupOwnerAddress);
        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();

        // Start appropriate connection mode
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

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                Log.d(TAG, "Server started on port " + TCP_PORT);
                handler.post(() -> Toast.makeText(ChatActivity.this, "Server started, waiting for client...", Toast.LENGTH_SHORT).show());
                Socket client = serverSocket.accept();
                Log.d(TAG, "Client connected from " + client.getInetAddress().getHostAddress());
                handler.post(() -> Toast.makeText(ChatActivity.this, "Client connected", Toast.LENGTH_SHORT).show());
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    Log.d(TAG, "Received from client: " + finalLine);
                    handler.post(() -> tvChat.append("Client: " + finalLine + "\n"));
                }
                in.close();
                client.close();
                Log.d(TAG, "Client connection closed.");
            } catch (IOException e) {
                Log.e(TAG, "Server error", e);
                handler.post(() -> Toast.makeText(ChatActivity.this, "Server error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        serverSocket.close();
                        Log.d(TAG, "Server socket closed.");
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing server socket", e);
                    }
                }
            }
        }).start();
    }

    private void startClient() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Client attempting connection to " + groupOwnerAddress + ":" + TCP_PORT);
                clientSocket = new Socket(groupOwnerAddress, TCP_PORT);
                clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
                Log.d(TAG, "Client connected to server");
                handler.post(() -> Toast.makeText(ChatActivity.this, "Connected to server", Toast.LENGTH_SHORT).show());
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    Log.d(TAG, "Received from server: " + finalLine);
                    handler.post(() -> tvChat.append("Server: " + finalLine + "\n"));
                }
                in.close();
                clientSocket.close();
                Log.d(TAG, "Client socket closed.");
            } catch (IOException e) {
                Log.e(TAG, "Client error", e);
                handler.post(() -> Toast.makeText(ChatActivity.this, "Failed to connect: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void sendMessage(String message) {
        new Thread(() -> {
            try {
                if (isGroupOwner) {
                    // In a real implementation, as a server you may send to all connected clients.
                    // Here, we log the message since a client output stream is not maintained.
                    Log.d(TAG, "Server sending message: " + message);
                    handler.post(() -> tvChat.append("Me (Server): " + message + "\n"));
                } else {
                    if (clientOut != null) {
                        clientOut.println(message);
                        Log.d(TAG, "Client sent message: " + message);
                        handler.post(() -> tvChat.append("Me (Client): " + message + "\n"));
                    } else {
                        Log.w(TAG, "Client output stream is null. Message not sent.");
                        handler.post(() -> Toast.makeText(ChatActivity.this, "Not connected to server.", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                handler.post(() -> Toast.makeText(ChatActivity.this, "Error sending message: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any open sockets
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.d(TAG, "Server socket closed in onDestroy.");
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                Log.d(TAG, "Client socket closed in onDestroy.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket in onDestroy", e);
        }
    }
}