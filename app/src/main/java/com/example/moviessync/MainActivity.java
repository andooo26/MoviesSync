package com.example.moviessync;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvIpAddress;

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

        Button btnHost = findViewById(R.id.btnHost);
        Button btnClient = findViewById(R.id.btnClient);
        tvIpAddress = findViewById(R.id.tvIpAddress);

        displayIpAddress();

        // ホストボタン
        btnHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "ホストモード", Toast.LENGTH_SHORT).show();
            }
        });

        // クライアントボタン
        btnClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "クライアントモード", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void displayIpAddress() {
        String ipAddress = NetworkUtils.getLocalIpAddress(this);
        if (ipAddress != null) {
            tvIpAddress.setText("IPアドレス: " + ipAddress);
        } else {
            tvIpAddress.setText("IPアドレス: 取得できませんでした");
        }
    }
}