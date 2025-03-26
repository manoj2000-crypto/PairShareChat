package com.example.pairsharechat;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int TCP_PORT = 8888;
    private static final int FILE_SELECT_CODE = 101;
    private static final int FILE_TRANSFER_PORT = 8889;

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

    private String fileTransferAddress;

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

        MaterialButton btnSendFile = findViewById(R.id.btnSendFile);
        btnSendFile.setOnClickListener(v -> selectFile());

        MaterialButton btnDisconnect = findViewById(R.id.btnDisconnect);
        btnDisconnect.setOnClickListener(v -> disconnect());

        groupOwnerAddress = getIntent().getStringExtra("groupOwnerAddress");
        isGroupOwner = getIntent().getBooleanExtra("isGroupOwner", false);
        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show();

        // Start the file-transfer server on each device
        startFileTransferServer();

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

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select a file"), FILE_SELECT_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                sendFile(uri);
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void sendFile(Uri uri) {
        new Thread(() -> {
            Socket fileSocket = null;
            try {
                if (fileTransferAddress == null) {
                    handler.post(() -> Toast.makeText(ChatActivity.this, "File transfer address unknown.", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Connect to the remote device’s file transfer server.
                fileSocket = new Socket(fileTransferAddress, FILE_TRANSFER_PORT);
                DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream());
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    handler.post(() -> Toast.makeText(ChatActivity.this, "Unable to open file", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Get original file name and file size.
                String fileName = getFileName(uri);
                int fileSize = inputStream.available();
                Log.d(TAG, "Sending file: " + fileName + " of size: " + fileSize);

                // First, send the file name length and file name bytes.
                dos.writeInt(fileName.getBytes().length);
                dos.write(fileName.getBytes());
                dos.flush();

                // Next, send the file size.
                dos.writeInt(fileSize);
                dos.flush();

                // Send the file bytes.
                byte[] buffer = new byte[4096];
                int bytesRead;
                final int[] totalBytesSent = {0};
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                    totalBytesSent[0] += bytesRead;
                    Log.d(TAG, "Sent " + totalBytesSent[0] + " bytes");
                }
                dos.flush();
                inputStream.close();

                handler.post(() -> Toast.makeText(ChatActivity.this, "File Sent (" + totalBytesSent[0] + " bytes)", Toast.LENGTH_SHORT).show());
                Log.d(TAG, "File sent successfully: " + totalBytesSent[0] + " bytes");
            } catch (NullPointerException npe) {
                handler.post(() -> Toast.makeText(ChatActivity.this, "Null pointer error: " + npe.getMessage(), Toast.LENGTH_SHORT).show());
                Log.e(TAG, "NullPointerException sending file", npe);
            } catch (java.net.ConnectException ce) {
                handler.post(() -> Toast.makeText(ChatActivity.this, "Connection refused for file transfer.", Toast.LENGTH_SHORT).show());
                Log.e(TAG, "File transfer connection refused", ce);
            } catch (java.net.SocketException se) {
                if ("Socket closed".equals(se.getMessage())) {
                    Log.i(TAG, "File transfer socket closed gracefully.");
                } else {
                    handler.post(() -> Toast.makeText(ChatActivity.this, "Socket error: " + se.getMessage(), Toast.LENGTH_SHORT).show());
                    Log.e(TAG, "SocketException sending file", se);
                }
            } catch (IOException ioe) {
                handler.post(() -> Toast.makeText(ChatActivity.this, "I/O error: " + ioe.getMessage(), Toast.LENGTH_SHORT).show());
                Log.e(TAG, "IOException sending file", ioe);
            } catch (Exception e) {
                handler.post(() -> Toast.makeText(ChatActivity.this, "Unexpected error sending file", Toast.LENGTH_SHORT).show());
                Log.e(TAG, "Unexpected error sending file", e);
            } finally {
                if (fileSocket != null) {
                    try {
                        fileSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing file socket", e);
                    }
                }
            }
        }).start();
    }

    private void startFileTransferServer() {
        new Thread(() -> {
            ServerSocket fileServerSocket = null;
            try {
                fileServerSocket = new ServerSocket(FILE_TRANSFER_PORT);
                Log.d(TAG, "File Transfer Server started on port " + FILE_TRANSFER_PORT);
                while (!Thread.currentThread().isInterrupted()) {
                    // Accept file transfer connection.
                    Socket fileSocket = fileServerSocket.accept();
                    new Thread(() -> receiveFile(fileSocket)).start();
                }
            } catch (IOException e) {
                Log.e(TAG, "File Transfer Server error", e);
            } finally {
                if (fileServerSocket != null) {
                    try {
                        fileServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing file server socket", e);
                    }
                }
            }
        }).start();
    }

    private void receiveFile(Socket fileSocket) {
        try {
            DataInputStream dis = new DataInputStream(fileSocket.getInputStream());
            // Read file name length and then the file name.
            int fileNameLength = dis.readInt();
            byte[] fileNameBytes = new byte[fileNameLength];
            dis.readFully(fileNameBytes);
            String fileName = new String(fileNameBytes);

            // Read file size.
            int fileSize = dis.readInt();
            Log.d(TAG, "Receiving file: " + fileName + " of size: " + fileSize);

            // Create file in the Downloads folder
            File receivedFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            FileOutputStream fos = new FileOutputStream(receivedFile);

            byte[] buffer = new byte[4096];
            int totalBytesRead = 0;
            int bytesRead;
            while (totalBytesRead < fileSize && (bytesRead = dis.read(buffer, 0, Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                Log.d(TAG, "Received " + totalBytesRead + " bytes");
            }
            fos.close();
            Log.d(TAG, "File received successfully: " + receivedFile.getAbsolutePath());

            // Update UI about file receipt
            handler.post(() -> {
                messageList.add(new Message("[File Received] " + receivedFile.getName(), false, receivedFile.getAbsolutePath()));
                chatAdapter.notifyItemInserted(messageList.size() - 1);
                rvChat.scrollToPosition(messageList.size() - 1);
            });
        } catch (IOException e) {
            Log.e(TAG, "Error receiving file", e);
        } finally {
            try {
                fileSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing file socket", e);
            }
        }
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

                // Save the client's IP for file transfers.
                fileTransferAddress = client.getInetAddress().getHostAddress();
                Log.d(TAG, "Client connected from: " + fileTransferAddress);

                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                // Save output stream for sending messages
                clientOut = out;
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    handler.post(() -> {
                        messageList.add(new Message(finalLine, false, null));
                        chatAdapter.notifyItemInserted(messageList.size() - 1);
                        rvChat.scrollToPosition(messageList.size() - 1);
                    });
                }
                in.close();
                client.close();
            } catch (IOException e) {
                if (e instanceof java.net.SocketException && "Socket closed".equals(e.getMessage())) {
                    Log.i(TAG, "Messaging socket closed gracefully.");
                } else {
                    Log.e(TAG, "Server error", e);
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

                // For clients, use groupOwnerAddress for file transfers.
                fileTransferAddress = groupOwnerAddress;

                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    handler.post(() -> {
                        messageList.add(new Message(finalLine, false, null));
                        chatAdapter.notifyItemInserted(messageList.size() - 1);
                        rvChat.scrollToPosition(messageList.size() - 1);
                    });
                }
                in.close();
                clientSocket.close();
            } catch (java.net.ConnectException ce) {
                handler.post(() -> Toast.makeText(ChatActivity.this, "Connection refused. Please try again.", Toast.LENGTH_SHORT).show());
                Log.e(TAG, "Messaging connection refused", ce);
            } catch (java.net.SocketException se) {
                if ("Socket closed".equals(se.getMessage())) {
                    Log.i(TAG, "Messaging socket closed gracefully.");
                } else {
                    Log.e(TAG, "Messaging socket exception", se);
                }
            } catch (IOException e) {
                Log.e(TAG, "Client error", e);
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
                    messageList.add(new Message(message, true, null));
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