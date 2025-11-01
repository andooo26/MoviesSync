package com.example.moviessync;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoSyncHostService extends Service {

    private static final String TAG = "VideoSyncHostService";
    private static final int SERVER_PORT = 8888;

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private boolean isRunning = false;
    private ArrayList<ClientConnection> connectedClients = new ArrayList<>();
    private Uri videoUri;
    private long videoSize = 0;

    // Binder
    public class LocalBinder extends Binder {
        VideoSyncHostService getService() {
            return VideoSyncHostService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newCachedThreadPool();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            startServer();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // サーバ開始
    private void startServer() {
        isRunning = true;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(SERVER_PORT);
                    Log.d(TAG, "Server started on port " + SERVER_PORT);

                    while (isRunning && !serverSocket.isClosed()) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            Log.d(TAG, "Client connected: " + clientSocket.getRemoteSocketAddress());
                            
                            ClientConnection client = new ClientConnection(clientSocket);
                            connectedClients.add(client);
                            executorService.execute(client);
                            
                        } catch (IOException e) {
                            if (isRunning) {
                                Log.e(TAG, "Error accepting client connection", e);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error starting server", e);
                }
            }
        });
    }

    // サーバ停止
    private void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
        
        // 全クライアント接続を閉じる
        for (ClientConnection client : connectedClients) {
            client.close();
        }
        connectedClients.clear();
    }

    // クライアント取得
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    // 動画URIを設定
    public void setVideoUri(Uri uri) {
        this.videoUri = uri;
        if (uri != null) {
            try {
                ContentResolver resolver = getContentResolver();
                InputStream is = resolver.openInputStream(uri);
                if (is != null) {
                    videoSize = is.available();
                    is.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error getting video size", e);
                videoSize = 0;
            }
        }
        Log.d(TAG, "Video URI set: " + uri + ", size: " + videoSize);
    }

    // 全クライアントに動画メタデータを送信
    public void broadcastVideoMetadata() {
        if (videoUri == null) {
            Log.w(TAG, "No video selected");
            return;
        }

        try {
            JSONObject metadata = new JSONObject();
            metadata.put("fileName", getFileName(videoUri));
            metadata.put("fileSize", videoSize);

            synchronized (connectedClients) {
                for (ClientConnection client : new ArrayList<>(connectedClients)) {
                    try {
                        client.sendMessage(MessageType.VIDEO_METADATA, metadata);
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending metadata to client", e);
                    }
                }
            }
            Log.d(TAG, "Video metadata broadcasted to " + connectedClients.size() + " clients");
        } catch (JSONException e) {
            Log.e(TAG, "Error creating metadata", e);
        }
    }

    // URIからファイル名を取得
    private String getFileName(Uri uri) {
        String fileName = "video.mp4";
        try {
            ContentResolver resolver = getContentResolver();
            try (android.database.Cursor cursor = resolver.query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
        }
        return fileName;
    }

    private class ClientConnection implements Runnable {
        private Socket socket;
        private BufferedWriter writer;
        private BufferedReader reader;

        public ClientConnection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // 入出力ストリームを初期化
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                // クライアントからの接続メッセージを待機
                MessageProtocol.Message message = MessageProtocol.receiveMessage(reader);
                if (message != null && message.type == MessageType.CONNECT) {
                    Log.d(TAG, "Received CONNECT message from client");
                    
                    // 接続確認を送信
                    MessageProtocol.sendSimpleMessage(writer, MessageType.CONNECTED);
                    Log.d(TAG, "Sent CONNECTED message to client");

                    // メッセージループ
                    while (!socket.isClosed() && isRunning) {
                        message = MessageProtocol.receiveMessage(reader);
                        if (message == null) {
                            // 接続が切断された
                            break;
                        }

                        // メッセージタイプに応じた処理
                        handleMessage(message);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling client connection", e);
            } finally {
                close();
            }
        }

        private void handleMessage(MessageProtocol.Message message) {
            switch (message.type) {
                case READY:
                    Log.d(TAG, "Client is ready");
                    // TODO: クライアント準備完了時の処理
                    break;
                case ERROR:
                    Log.e(TAG, "Client error: " + message.getString("error"));
                    break;
                default:
                    Log.w(TAG, "Unknown message type: " + message.type);
            }
        }


        public void sendMessage(MessageType type, org.json.JSONObject data) throws IOException {
            if (writer != null) {
                MessageProtocol.sendMessage(writer, type, data);
            }
        }

        public void sendSimpleMessage(MessageType type) throws IOException {
            if (writer != null) {
                MessageProtocol.sendSimpleMessage(writer, type);
            }
        }

        public void close() {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (writer != null) {
                    writer.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                connectedClients.remove(this);
                Log.d(TAG, "Client connection closed");
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }
        }
    }
}

