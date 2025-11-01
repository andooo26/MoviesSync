package com.example.moviessync;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
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

        /**
         * メッセージを処理
         */
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

        /**
         * メッセージを送信
         */
        public void sendMessage(MessageType type, org.json.JSONObject data) throws IOException {
            if (writer != null) {
                MessageProtocol.sendMessage(writer, type, data);
            }
        }

        /**
         * シンプルなメッセージを送信
         */
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

