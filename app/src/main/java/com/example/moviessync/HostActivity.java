package com.example.moviessync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
            tvStatus.setText("サーバー待機中");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            tvStatus.setText("サーバー停止");
        }
    };

    private void startUpdatingClientCount() {
        updateClientCountRunnable = new Runnable() {
            @Override
            public void run() {
                tvConnectedClients.setText("接続クライアント: 0");
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
}

