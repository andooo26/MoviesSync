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
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoSyncClientService extends Service {

    private static final String TAG = "VideoSyncClientService";
    private static final int HOST_PORT = 8888;

    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private ExecutorService executorService;
    private boolean isConnected = false;
    private String hostIp;

    // Binder
    public class LocalBinder extends Binder {
        VideoSyncClientService getService() {
            return VideoSyncClientService.this;
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
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * ホストに接続
     */
    public void connect(String hostIp) {
        if (isConnected) {
            Log.w(TAG, "Already connected");
            return;
        }

        this.hostIp = hostIp;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Connecting to host: " + hostIp + ":" + HOST_PORT);
                    socket = new Socket(hostIp, HOST_PORT);
                    
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                    // CONNECTメッセージを送信
                    MessageProtocol.sendSimpleMessage(writer, MessageType.CONNECT);
                    Log.d(TAG, "Sent CONNECT message");

                    // CONNECTEDメッセージを受信待機
                    MessageProtocol.Message response = MessageProtocol.receiveMessage(reader);
                    if (response != null && response.type == MessageType.CONNECTED) {
                        isConnected = true;
                        Log.d(TAG, "Connected to host successfully");

                        // メッセージ受信ループ
                        startMessageLoop();
                    } else {
                        Log.e(TAG, "Connection failed: Invalid response");
                        disconnect();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error connecting to host", e);
                    disconnect();
                }
            }
        });
    }

    /**
     * メッセージ受信ループ
     */
    private void startMessageLoop() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (isConnected && !socket.isClosed()) {
                        MessageProtocol.Message message = MessageProtocol.receiveMessage(reader);
                        if (message == null) {
                            // 接続が切断された
                            break;
                        }

                        // メッセージタイプに応じた処理
                        handleMessage(message);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error in message loop", e);
                } finally {
                    disconnect();
                }
            }
        });
    }

    /**
     * メッセージを処理
     */
    private void handleMessage(MessageProtocol.Message message) {
        switch (message.type) {
            case VIDEO_METADATA:
                Log.d(TAG, "Received VIDEO_METADATA");
                // TODO: 動画情報の処理
                break;
            case VIDEO_DATA:
                Log.d(TAG, "Received VIDEO_DATA");
                // TODO: 動画データの処理
                break;
            case PLAY_COMMAND:
                Log.d(TAG, "Received PLAY_COMMAND");
                // TODO: 再生開始の処理
                break;
            case PAUSE_COMMAND:
                Log.d(TAG, "Received PAUSE_COMMAND");
                // TODO: 一時停止の処理
                break;
            case SYNC_TIME:
                Log.d(TAG, "Received SYNC_TIME");
                // TODO: 時刻同期の処理
                break;
            default:
                Log.w(TAG, "Unknown message type: " + message.type);
        }
    }

    /**
     * メッセージを送信
     */
    public void sendMessage(MessageType type, org.json.JSONObject data) throws IOException {
        if (writer != null && isConnected) {
            MessageProtocol.sendMessage(writer, type, data);
        } else {
            throw new IOException("Not connected");
        }
    }

    /**
     * シンプルなメッセージを送信
     */
    public void sendSimpleMessage(MessageType type) throws IOException {
        if (writer != null && isConnected) {
            MessageProtocol.sendSimpleMessage(writer, type);
        } else {
            throw new IOException("Not connected");
        }
    }

    /**
     * 接続を切断
     */
    public void disconnect() {
        isConnected = false;
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
            Log.d(TAG, "Disconnected from host");
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting", e);
        }
    }

    /**
     * 接続状態を取得
     */
    public boolean isConnected() {
        return isConnected;
    }
}

