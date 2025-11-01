package com.example.moviessync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ClientActivity extends AppCompatActivity {

    private TextView tvStatus;
    private EditText etHostIp;
    private Button btnConnect;
    private Button btnDisconnect;
    private TextView tvConnectionInfo;

    private VideoSyncClientService clientService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateStatusRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_client);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvStatus = findViewById(R.id.tvStatus);
        etHostIp = findViewById(R.id.etHostIp);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        tvConnectionInfo = findViewById(R.id.tvConnectionInfo);

        // Serviceを開始
        startService();

        // 接続ボタン
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String hostIp = etHostIp.getText().toString().trim();
                if (TextUtils.isEmpty(hostIp)) {
                    Toast.makeText(ClientActivity.this, "IPアドレスを入力してください", Toast.LENGTH_SHORT).show();
                    return;
                }
                connectToHost(hostIp);
            }
        });

        // 切断ボタン
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectFromHost();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUpdatingStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdatingStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clientServiceConnection != null) {
            unbindService(clientServiceConnection);
        }
        stopService(new Intent(this, VideoSyncClientService.class));
    }

    // service起動
    private void startService() {
        Intent intent = new Intent(this, VideoSyncClientService.class);
        startService(intent);
        bindService(intent, clientServiceConnection, Context.BIND_AUTO_CREATE);
    }

    // serviceの接続
    private ServiceConnection clientServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VideoSyncClientService.LocalBinder binder = (VideoSyncClientService.LocalBinder) service;
            clientService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            clientService = null;
        }
    };


    // ホストへ接続
    private void connectToHost(String hostIp) {
        if (clientService != null) {
            tvStatus.setText("接続中...");
            btnConnect.setEnabled(false);
            tvConnectionInfo.setText("接続先: " + hostIp);
            
            clientService.connect(hostIp);
        } else {
            Toast.makeText(this, "サービスが利用できません", Toast.LENGTH_SHORT).show();
        }
    }

    // ホストから切断
    private void disconnectFromHost() {
        if (clientService != null) {
            clientService.disconnect();
        }
        updateUI();
    }


    private void startUpdatingStatus() {
        updateStatusRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                handler.postDelayed(this, 500); // 0.5秒ごとに更新
            }
        };
        handler.post(updateStatusRunnable);
    }


    private void stopUpdatingStatus() {
        if (updateStatusRunnable != null) {
            handler.removeCallbacks(updateStatusRunnable);
        }
    }


    private void updateUI() {
        if (clientService != null) {
            if (clientService.isConnected()) {
                tvStatus.setText("接続済み");
                btnConnect.setEnabled(false);
                btnDisconnect.setEnabled(true);
            } else {
                tvStatus.setText("未接続");
                btnConnect.setEnabled(true);
                btnDisconnect.setEnabled(false);
            }
        } else {
            tvStatus.setText("サービス未接続");
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(false);
        }
    }
}

