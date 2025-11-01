package com.example.moviessync;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
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

        public ClientConnection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                
            } catch (Exception e) {
                Log.e(TAG, "Error handling client connection", e);
            } finally {
                close();
            }
        }

        public void close() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                connectedClients.remove(this);
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }
        }
    }
}

