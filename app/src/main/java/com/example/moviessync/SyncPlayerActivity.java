package com.example.moviessync;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SyncPlayerActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvConnectedMembers;
    private ImageView ivQrCode;
    private Button btnSelectVideo;
    private Button btnPlay;
    private TextView tvVideoInfo;
    private VideoView videoView;
    private FrameLayout videoContainer;
    private MediaPlayer currentMediaPlayer;

    private GroupSyncService groupService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateMemberCountRunnable;
    private Uri selectedVideoUri;
    private boolean isCoordinator;
    private BroadcastReceiver playCommandReceiver;

    // 動画選択用のActivityResultLauncher
    private ActivityResultLauncher<String> videoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sync_player);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvConnectedMembers = findViewById(R.id.tvConnectedMembers);
        ivQrCode = findViewById(R.id.ivQrCode);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnPlay = findViewById(R.id.btnPlay);
        tvVideoInfo = findViewById(R.id.tvVideoInfo);
        videoView = findViewById(R.id.videoView);
        videoContainer = findViewById(R.id.videoContainer);

        // Intentから情報を取得
        isCoordinator = getIntent().getBooleanExtra("is_coordinator", false);
        String coordinatorIp = getIntent().getStringExtra("coordinator_ip");

        // ActivityResultLauncherを初期化
        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedVideoUri = uri;
                        String fileName = getFileName(uri);
                        tvVideoInfo.setText("動画: " + fileName);
                        btnPlay.setEnabled(true);
                        
                        // VideoViewにURIを設定
                        videoView.setVideoURI(uri);
                        // 動画のアスペクト比を保ったまま全画面表示するためのリスナーを設定
                        setupVideoAspectRatio();
                    }
                }
        );

        registerPlayCommandReceiver();
        startService(isCoordinator, coordinatorIp);
        if (isCoordinator) {
            generateAndDisplayQrCode();
        }

        // 動画選択ボタン
        btnSelectVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                videoPickerLauncher.launch("video/*");
            }
        });

        // 再生ボタン
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedVideoUri != null && groupService != null) {
                    groupService.broadcastPlayCommand();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView == null || !videoView.isPlaying()) {
            showSystemUI();
        } else {
            hideSystemUI();
        }
        startUpdatingMemberCount();
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
            adjustVideoSize();
        }
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                adjustVideoSize();
            }
        }, 100);
    }
    
    // システムUIを隠す
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    
    // システムUIを表示
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdatingMemberCount();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (playCommandReceiver != null) {
            unregisterReceiver(playCommandReceiver);
        }
        if (groupServiceConnection != null) {
            unbindService(groupServiceConnection);
        }
        stopService(new Intent(this, GroupSyncService.class));
    }

    // Serviceを開始
    private void startService(boolean isCoordinator, String coordinatorIp) {
        Intent intent = new Intent(this, GroupSyncService.class);
        startService(intent);
        bindService(intent, groupServiceConnection, Context.BIND_AUTO_CREATE);
        
        // Serviceに開始情報を渡す
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (groupService != null) {
                    groupService.startService(isCoordinator, coordinatorIp);
                } else {
                    handler.postDelayed(this, 100);
                }
            }
        }, 100);
    }

    // Serviceとの接続を管理
    private ServiceConnection groupServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GroupSyncService.LocalBinder binder = (GroupSyncService.LocalBinder) service;
            groupService = binder.getService();
            tvStatus.setText(isCoordinator ? "グループ待機中（コーディネーター）" : "グループ接続中");
            
            // コーディネーターの場合、QRコードを再生成（Service接続後にIPアドレスが確定しているため）
            if (isCoordinator) {
                generateAndDisplayQrCode();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            groupService = null;
            tvStatus.setText("接続切断");
        }
    };

    // メンバー数更新を開始
    private void startUpdatingMemberCount() {
        updateMemberCountRunnable = new Runnable() {
            @Override
            public void run() {
                if (groupService != null) {
                    int count = groupService.getConnectedMemberCount();
                    tvConnectedMembers.setText("接続メンバー: " + count);
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateMemberCountRunnable);
    }

    // メンバー数更新を停止
    private void stopUpdatingMemberCount() {
        if (updateMemberCountRunnable != null) {
            handler.removeCallbacks(updateMemberCountRunnable);
        }
    }

    // 動画再生開始
    private void startPlayback() {
        android.util.Log.d("SyncPlayerActivity", "startPlayback called - selectedVideoUri: " + selectedVideoUri + ", videoView: " + videoView);
        if (selectedVideoUri != null && videoView != null && videoContainer != null) {
            videoContainer.setVisibility(View.VISIBLE);
            hideControlUI();
            hideSystemUI();
            adjustVideoSize();
            videoView.start();
            android.util.Log.d("SyncPlayerActivity", "Video playback started");
        } else {
            android.util.Log.w("SyncPlayerActivity", "Cannot start playback - selectedVideoUri: " + selectedVideoUri + ", videoView: " + videoView);
        }
    }
    
    // コントロールUIを非表示
    private void hideControlUI() {
        if (tvStatus != null) tvStatus.setVisibility(View.GONE);
        if (tvConnectedMembers != null) tvConnectedMembers.setVisibility(View.GONE);
        if (ivQrCode != null) ivQrCode.setVisibility(View.GONE);
        if (btnSelectVideo != null) btnSelectVideo.setVisibility(View.GONE);
        if (tvVideoInfo != null) tvVideoInfo.setVisibility(View.GONE);
        if (btnPlay != null) btnPlay.setVisibility(View.GONE);
    }
    
    // コントロールUIを表示
    private void showControlUI() {
        if (tvStatus != null) tvStatus.setVisibility(View.VISIBLE);
        if (tvConnectedMembers != null) tvConnectedMembers.setVisibility(View.VISIBLE);
        if (btnSelectVideo != null) btnSelectVideo.setVisibility(View.VISIBLE);
        if (tvVideoInfo != null) tvVideoInfo.setVisibility(View.VISIBLE);
        if (btnPlay != null) btnPlay.setVisibility(View.VISIBLE);
        // QRコードはコーディネーターの場合のみ表示
        if (ivQrCode != null && isCoordinator) {
            ivQrCode.setVisibility(View.VISIBLE);
        }
    }
    
    // 動画のアスペクト比を保ったまま全画面表示するためのリスナーを設定
    private void setupVideoAspectRatio() {
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                currentMediaPlayer = mp;
                adjustVideoSize();
                mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    @Override
                    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                        adjustVideoSize();
                    }
                });
            }
        });
    }
    
    // 動画のサイズを調整）
    private void adjustVideoSize() {
        if (videoView == null || videoContainer == null || currentMediaPlayer == null) {
            return;
        }
        
        int videoWidth = currentMediaPlayer.getVideoWidth();
        int videoHeight = currentMediaPlayer.getVideoHeight();
        
        if (videoWidth == 0 || videoHeight == 0) {
            return;
        }
        
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        
        float videoAspectRatio = (float) videoWidth / videoHeight;
        float screenAspectRatio = (float) screenWidth / screenHeight;
        
        ViewGroup.LayoutParams videoParams = videoView.getLayoutParams();
        
        if (videoAspectRatio > screenAspectRatio) {
            // 動画が横長：幅を画面幅に合わせる
            videoParams.width = screenWidth;
            videoParams.height = (int) (screenWidth / videoAspectRatio);
        } else {
            // 動画が縦長：高さを画面高さに合わせる
            videoParams.width = (int) (screenHeight * videoAspectRatio);
            videoParams.height = screenHeight;
        }
        
        videoView.setLayoutParams(videoParams);
    }

    // 再生コマンド受信用のBroadcastReceiverを登録
    private void registerPlayCommandReceiver() {
        playCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                android.util.Log.d("SyncPlayerActivity", "BroadcastReceiver received action: " + intent.getAction());
                if (GroupSyncService.ACTION_PLAY.equals(intent.getAction())) {
                    // 再生コマンドを受信したら自分の動画を再生
                    android.util.Log.d("SyncPlayerActivity", "ACTION_PLAY received, starting playback on main thread");
                    // メインスレッドで実行を保証
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            startPlayback();
                        }
                    });
                }
            }
        };
        IntentFilter filter = new IntentFilter(GroupSyncService.ACTION_PLAY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playCommandReceiver, filter);
        }
    }

    // QRコードを生成して表示
    private void generateAndDisplayQrCode() {
        String ipAddress = NetworkUtils.getLocalIpAddress(this);
        if (ipAddress == null || ipAddress.isEmpty()) {
            return;
        }

        try {
            java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(ipAddress, BarcodeFormat.QR_CODE, 512, 512, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);
        } catch (WriterException e) {
            android.util.Log.e("SyncPlayerActivity", "Error generating QR code", e);
        }
    }

    // URIからファイル名を取得
    private String getFileName(Uri uri) {
        try {
            String scheme = uri.getScheme();
            if ("content".equals(scheme)) {
                String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
                try (android.database.Cursor cursor = getContentResolver().query(
                        uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            String fileName = cursor.getString(nameIndex);
                            if (fileName != null && !fileName.isEmpty()) {
                                return fileName;
                            }
                        }
                    }
                }
            }
            String lastPath = uri.getLastPathSegment();
            return (lastPath != null && !lastPath.isEmpty()) ? lastPath : "動画ファイル";
        } catch (Exception e) {
            return "動画ファイル";
        }
    }
}

