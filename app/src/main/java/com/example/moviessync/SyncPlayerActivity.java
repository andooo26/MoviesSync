package com.example.moviessync;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import android.content.SharedPreferences;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.StyledPlayerView;
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
    private StyledPlayerView playerView;
    private FrameLayout videoContainer;
    private ExoPlayer exoPlayer;
    private MediaItem selectedMediaItem;
    private SwitchCompat switchToastEnabled;

    private GroupSyncService groupService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateMemberCountRunnable;
    private Uri selectedVideoUri;
    private boolean isCoordinator;
    private BroadcastReceiver playCommandReceiver;

    // 動画選択用のActivityResultLauncher
    private ActivityResultLauncher<String> videoPickerLauncher;
    
    private static final String PREFS_NAME = "MoviesSyncPrefs";
    private static final String PREF_TOAST_ENABLED = "toast_enabled";

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
        playerView = findViewById(R.id.playerView);
        videoContainer = findViewById(R.id.videoContainer);
        switchToastEnabled = findViewById(R.id.switchToastEnabled);
        initializePlayer();
        
        // トースト表示設定を読み込み
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean toastEnabled = prefs.getBoolean(PREF_TOAST_ENABLED, true);
        switchToastEnabled.setChecked(toastEnabled);
        
        // トースト表示設定の変更を保存
        switchToastEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(PREF_TOAST_ENABLED, isChecked);
            editor.apply();
        });

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

                        prepareSelectedMediaItem();
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
        if (exoPlayer == null || !exoPlayer.isPlaying()) {
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
        }
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
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
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
        releasePlayer();
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
            tvStatus.setText(isCoordinator ? "参加待ち中" : "グループ接続中");
            
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
        android.util.Log.d("SyncPlayerActivity", "startPlayback called - selectedVideoUri: " + selectedVideoUri + ", exoPlayer: " + exoPlayer);
        if (selectedVideoUri != null && exoPlayer != null && playerView != null && videoContainer != null) {
            videoContainer.setVisibility(View.VISIBLE);
            hideControlUI();
            hideSystemUI();
            ensureMediaPrepared();
            exoPlayer.pause();
            exoPlayer.seekTo(0);
            exoPlayer.play();
            android.util.Log.d("SyncPlayerActivity", "ExoPlayer playback started");
        } else {
            android.util.Log.w("SyncPlayerActivity", "Cannot start playback - selectedVideoUri: " + selectedVideoUri + ", exoPlayer: " + exoPlayer);
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
        if (videoContainer != null) {
            videoContainer.setVisibility(View.GONE);
        }
    }

    // 再生コマンド受信用のBroadcastReceiverを登録
    private void registerPlayCommandReceiver() {
        playCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                android.util.Log.d("SyncPlayerActivity", "BroadcastReceiver received action: " + intent.getAction());
                if (GroupSyncService.ACTION_PLAY.equals(intent.getAction())) {
					long targetEpochMs = intent.getLongExtra(GroupSyncService.EXTRA_TARGET_EPOCH_MS, 0L);
					long delayMs = 0L;
					if (targetEpochMs > 0) {
						long serverNow = TimeSyncManager.getInstance(getApplicationContext()).nowServerMillis();
						delayMs = Math.max(0L, targetEpochMs - serverNow);
					}
					android.util.Log.d("SyncPlayerActivity", "ACTION_PLAY received, scheduling playback delayMs=" + delayMs + ", targetEpochMs=" + targetEpochMs);
					// 受信トースト
					try {
						String msg;
						if (targetEpochMs > 0) {
							String hhmmss = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(targetEpochMs));
							msg = "再生開始信号を受信（" + hhmmss + "）";
						} else {
							msg = "再生開始信号を受信";
						}
						showToastIfEnabled(msg);
					} catch (Exception ignore) {}
					ensureMediaPrepared();
					if (exoPlayer != null) {
						exoPlayer.pause();
						exoPlayer.seekTo(0);
					}
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							startPlayback();
						}
					}, delayMs);
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

    private void initializePlayer() {
        if (exoPlayer != null) {
            return;
        }
        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
        exoPlayer.setPlayWhenReady(false);
        exoPlayer.addListener(playerListener);
        if (playerView != null) {
            playerView.setPlayer(exoPlayer);
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.removeListener(playerListener);
            exoPlayer.release();
            exoPlayer = null;
        }
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        selectedMediaItem = null;
    }

    private void prepareSelectedMediaItem() {
        if (exoPlayer == null || selectedVideoUri == null) {
            return;
        }
        selectedMediaItem = MediaItem.fromUri(selectedVideoUri);
        exoPlayer.setMediaItem(selectedMediaItem, /* resetPosition= */ true);
        exoPlayer.prepare();
        exoPlayer.pause();
    }

    private void ensureMediaPrepared() {
        if (exoPlayer == null || selectedVideoUri == null) {
            return;
        }
        if (selectedMediaItem == null) {
            prepareSelectedMediaItem();
            return;
        }
        MediaItem currentItem = exoPlayer.getCurrentMediaItem();
        if (currentItem == null || currentItem != selectedMediaItem) {
            exoPlayer.setMediaItem(selectedMediaItem, /* resetPosition= */ true);
            exoPlayer.prepare();
        } else if (exoPlayer.getPlaybackState() == Player.STATE_IDLE) {
            exoPlayer.prepare();
        }
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                android.util.Log.d("SyncPlayerActivity", "Playback ended, notifying group service");
                if (exoPlayer != null) {
                    exoPlayer.pause();
                }
                if (groupService != null) {
                    groupService.notifyLoopFinished();
                }
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            android.util.Log.e("SyncPlayerActivity", "Playback error", error);
            showControlUI();
            showToastIfEnabled("再生エラーが発生しました");
        }
    };
    
    // トースト表示設定に基づいてトーストを表示
    private void showToastIfEnabled(String message) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean toastEnabled = prefs.getBoolean(PREF_TOAST_ENABLED, true);
        if (toastEnabled) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}

