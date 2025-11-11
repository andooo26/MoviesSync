package com.example.moviessync;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.zxing.BarcodeFormat;

public class MainActivity extends AppCompatActivity {

    private Button btnCreateGroup;
    private Button btnScanQr;

    // QRスキャン用
    private ActivityResultLauncher<ScanOptions> qrScanLauncher;
    // カメラ権限
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnCreateGroup = findViewById(R.id.btnCreateGroup);
        btnScanQr = findViewById(R.id.btnScanQr);

        // 初期化
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchQrScanner();
                    } else {
                        Toast.makeText(MainActivity.this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // QRスキャンの初期化
        qrScanLauncher = registerForActivityResult(new ScanContract(), result -> {
            try {
                if (result.getContents() != null) {
                    String scannedText = result.getContents();
                    Log.d("MainActivity", "QRコードスキャン結果: " + scannedText);
                    if (scannedText.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                        Intent intent = new Intent(MainActivity.this, SyncPlayerActivity.class);
                        intent.putExtra("is_coordinator", false);
                        intent.putExtra("coordinator_ip", scannedText);
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "無効なQRコードです: " + scannedText, Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "QRコードスキャン処理でエラー", e);
                Toast.makeText(MainActivity.this, "エラーが発生しました: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // グループ作成ボタン
        btnCreateGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SyncPlayerActivity.class);
                intent.putExtra("is_coordinator", true);
                startActivity(intent);
            }
        });

        // QRコードスキャンボタン
        btnScanQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanQrCode();
            }
        });
    }

    // QRコードをスキャン
    private void scanQrCode() {
        // カメラ権限をチェック
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED) {
            // 権限がある場合は直接スキャンを開始
            launchQrScanner();
        } else {
            // 権限がない場合は要求
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // QRコードスキャナーを起動
    private void launchQrScanner() {
        try {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name());
            options.setPrompt("QRコードをスキャンしてください");
            options.setCameraId(0);
            options.setBeepEnabled(false);
            options.setBarcodeImageEnabled(true);
            // 縦画面にロック
            options.setOrientationLocked(true);
			options.setCaptureActivity(PortraitCaptureActivity.class);
            qrScanLauncher.launch(options);
        } catch (Exception e) {
            Log.e("MainActivity", "QRコードスキャナーの起動でエラー", e);
            Toast.makeText(this, "QRコードスキャナーを起動できませんでした: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}