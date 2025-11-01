package com.example.moviessync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class HostActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvConnectedClients;
    private Button btnSelectVideo;
    private Button btnStartPlayback;
    private TextView tvVideoInfo;

    private VideoSyncHostService hostService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateClientCountRunnable;
    private Uri selectedVideoUri;
    private String selectedVideoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_host);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvConnectedClients = findViewById(R.id.tvConnectedClients);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnStartPlayback = findViewById(R.id.btnStartPlayback);
        tvVideoInfo = findViewById(R.id.tvVideoInfo);

        startService();

        // 動画選択
        btnSelectVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectVideo();
            }
        });

        // 再生開始
        btnStartPlayback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // クライアント数の更新
        startUpdatingClientCount();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //続クライアント数の更新停止
        stopUpdatingClientCount();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Serviceを停止
        if (hostServiceConnection != null) {
            unbindService(hostServiceConnection);
        }
        stopService(new Intent(this, VideoSyncHostService.class));
    }


    private void startService() {
        Intent intent = new Intent(this, VideoSyncHostService.class);
        startService(intent);
        bindService(intent, hostServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection hostServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VideoSyncHostService.LocalBinder binder = (VideoSyncHostService.LocalBinder) service;
            hostService = binder.getService();
            tvStatus.setText("サーバー待機中(8888)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            hostService = null;
            tvStatus.setText("サーバー停止");
        }
    };

    private void startUpdatingClientCount() {
        updateClientCountRunnable = new Runnable() {
            @Override
            public void run() {
                if (hostService != null) {
                    int count = hostService.getConnectedClientCount();
                    tvConnectedClients.setText("接続クライアント: " + count);
                } else {
                    tvConnectedClients.setText("接続クライアント: -");
                }
                handler.postDelayed(this, 1000); // 1秒ごとに更新
            }
        };
        handler.post(updateClientCountRunnable);
    }

    private void stopUpdatingClientCount() {
        if (updateClientCountRunnable != null) {
            handler.removeCallbacks(updateClientCountRunnable);
        }
    }

    // 動画選択用のActivityResultLauncher
    private final ActivityResultLauncher<String> videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedVideoUri = uri;
                    selectedVideoPath = uri.toString();
                    tvVideoInfo.setText("動画: " + getFileName(uri));
                    
                    // Serviceに動画情報を設定
                    if (hostService != null) {
                        hostService.setVideoUri(uri);
                        // 接続中のクライアントにメタデータを送信
                        hostService.broadcastVideoMetadata();
                        btnStartPlayback.setEnabled(true);
                    }
                }
            }
    );

    // 動画ファイルを選択
    private void selectVideo() {
        videoPickerLauncher.launch("video/*");
    }

    // URIからファイル名を取得
    private String getFileName(Uri uri) {
        String fileName = "不明なファイル";
        try {
            String scheme = uri.getScheme();
            if ("content".equals(scheme)) {
                try (android.database.Cursor cursor = getContentResolver().query(
                        uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                }
            } else if ("file".equals(scheme)) {
                fileName = uri.getLastPathSegment();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }
}

