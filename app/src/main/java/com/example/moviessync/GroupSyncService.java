package com.example.moviessync;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

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

public class GroupSyncService extends Service {

    private static final String TAG = "GroupSyncService";
    private static final int SERVER_PORT = 8888;
    public static final String ACTION_PLAY = "com.example.moviessync.ACTION_PLAY";

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private boolean isRunning = false;
    private ArrayList<MemberConnection> connectedMembers = new ArrayList<>();
    private boolean isCoordinator = false;
    private String coordinatorIp = null;
    private Socket coordinatorSocket;
    private BufferedWriter coordinatorWriter;
    private BufferedReader coordinatorReader;

    // Binder
    public class LocalBinder extends Binder {
        GroupSyncService getService() {
            return GroupSyncService.this;
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
        stopService();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // サービスを開始（コーディネーターとして、またはメンバーとして）
    public void startService(boolean isCoordinator, String coordinatorIp) {
        this.isCoordinator = isCoordinator;
        this.coordinatorIp = coordinatorIp;

        if (isCoordinator) {
            startAsCoordinator();
        } else {
            startAsMember();
        }
    }

    // コーディネーターとして開始
    private void startAsCoordinator() {
        isRunning = true;
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(SERVER_PORT);
                    Log.d(TAG, "Started as coordinator on port " + SERVER_PORT);

                    while (isRunning && !serverSocket.isClosed()) {
                        try {
                            Socket memberSocket = serverSocket.accept();
                            Log.d(TAG, "Member connected: " + memberSocket.getRemoteSocketAddress());

                            MemberConnection member = new MemberConnection(memberSocket);
                            connectedMembers.add(member);
                            executorService.execute(member);
                        } catch (IOException e) {
                            if (isRunning) {
                                Log.e(TAG, "Error accepting member connection", e);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error starting coordinator", e);
                }
            }
        });
    }

    // メンバーとして開始
    private void startAsMember() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Connecting to coordinator: " + coordinatorIp);
                    coordinatorSocket = new Socket(coordinatorIp, SERVER_PORT);

                    coordinatorWriter = new BufferedWriter(new OutputStreamWriter(coordinatorSocket.getOutputStream(), "UTF-8"));
                    coordinatorReader = new BufferedReader(new InputStreamReader(coordinatorSocket.getInputStream(), "UTF-8"));

                    // JOINメッセージを送信
                    MessageProtocol.sendSimpleMessage(coordinatorWriter, MessageType.CONNECT);
                    Log.d(TAG, "Sent JOIN message to coordinator");

                    // CONNECTEDメッセージを受信
                    MessageProtocol.Message response = MessageProtocol.receiveMessage(coordinatorReader);
                    if (response != null && response.type == MessageType.CONNECTED) {
                        isRunning = true;
                        Log.d(TAG, "Connected to coordinator");

                        // メッセージ受信ループ
                        startMessageLoop();
                    } else {
                        Log.e(TAG, "Failed to connect to coordinator");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error connecting to coordinator", e);
                }
            }
        });
    }

    // メッセージ受信ループ（メンバー用）
    private void startMessageLoop() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (isRunning && coordinatorSocket != null && !coordinatorSocket.isClosed()) {
                        MessageProtocol.Message message = MessageProtocol.receiveMessage(coordinatorReader);
                        if (message == null) {
                            break;
                        }
                        handleMessage(message);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error in message loop", e);
                }
            }
        });
    }

    // メッセージを処理
    private void handleMessage(MessageProtocol.Message message) {
        switch (message.type) {
            case PLAY_COMMAND:
                Log.d(TAG, "Received PLAY_COMMAND");
                // Activityに再生開始を通知
                Intent playIntent = new Intent(ACTION_PLAY);
                playIntent.setPackage(getPackageName()); // パッケージ名を設定してアプリ内でのみ受信できるようにする
                sendBroadcast(playIntent);
                Log.d(TAG, "ACTION_PLAY broadcast sent from handleMessage");
                break;
            default:
                Log.w(TAG, "Unknown message type: " + message.type);
        }
    }

    // 全メンバーに再生コマンドをブロードキャスト
    public void broadcastPlayCommand() {
        // バックグラウンドスレッドで実行
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                if (isCoordinator) {
                    // コーディネーターは全メンバーにブロードキャスト
                    try {
                        synchronized (connectedMembers) {
                            for (MemberConnection member : new ArrayList<>(connectedMembers)) {
                                try {
                                    member.sendSimpleMessage(MessageType.PLAY_COMMAND);
                                } catch (IOException e) {
                                    Log.e(TAG, "Error sending play command to member", e);
                                }
                            }
                        }
                        // コーディネーター自身にもブロードキャストを送信
                        Intent playIntent = new Intent(ACTION_PLAY);
                        playIntent.setPackage(getPackageName()); // パッケージ名を設定してアプリ内でのみ受信できるようにする
                        sendBroadcast(playIntent);
                        Log.d(TAG, "Play command broadcasted to " + connectedMembers.size() + " members (including self) - ACTION_PLAY sent: " + ACTION_PLAY);
                    } catch (Exception e) {
                        Log.e(TAG, "Error broadcasting play command", e);
                    }
                } else {
                    // メンバーはコーディネーターに再生コマンドを送信
                    try {
                        if (coordinatorWriter != null) {
                            MessageProtocol.sendSimpleMessage(coordinatorWriter, MessageType.PLAY_COMMAND);
                            Log.d(TAG, "Play command sent to coordinator");
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending play command to coordinator", e);
                    }
                }
            }
        });
    }

    // 接続メンバー数を取得
    public int getConnectedMemberCount() {
        if (isCoordinator) {
            return connectedMembers.size() + 1; // メンバー数 + 自分自身
        } else {
            return isRunning ? 1 : 0; // 接続中なら自分自身がいる
        }
    }

    // サービスを停止
    private void stopService() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (coordinatorSocket != null && !coordinatorSocket.isClosed()) {
                coordinatorSocket.close();
            }
            for (MemberConnection member : connectedMembers) {
                member.close();
            }
            connectedMembers.clear();
        } catch (IOException e) {
            Log.e(TAG, "Error stopping service", e);
        }
    }

    // メンバー接続を管理する内部クラス
    private class MemberConnection implements Runnable {
        private Socket socket;
        private BufferedWriter writer;
        private BufferedReader reader;

        public MemberConnection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                MessageProtocol.Message message = MessageProtocol.receiveMessage(reader);
                if (message != null && message.type == MessageType.CONNECT) {
                    MessageProtocol.sendSimpleMessage(writer, MessageType.CONNECTED);

                    while (!socket.isClosed() && isRunning) {
                        message = MessageProtocol.receiveMessage(reader);
                        if (message == null) {
                            break;
                        }
                        // メンバーからの再生コマンドを受信したら全員にブロードキャスト
                        if (message.type == MessageType.PLAY_COMMAND) {
                            Log.d(TAG, "Received play command from member, broadcasting to all");
                            // broadcastPlayCommand()は既にコーディネーター自身にもブロードキャストを送信するため、
                            // ここで再度送信する必要はない
                            broadcastPlayCommand();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling member connection", e);
            } finally {
                close();
            }
        }

        public void sendSimpleMessage(MessageType type) throws IOException {
            if (writer != null) {
                MessageProtocol.sendSimpleMessage(writer, type);
            }
        }

        public void close() {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null && !socket.isClosed()) socket.close();
                connectedMembers.remove(this);
            } catch (IOException e) {
                Log.e(TAG, "Error closing member connection", e);
            }
        }
    }
}

