package com.example.moviessync;

public enum MessageType {
    CONNECT,           // クライアント接続通知
    CONNECTED,         // 接続確認応答
    VIDEO_METADATA,    // 動画情報
    VIDEO_DATA,        // 動画データチャンク
    READY,            // 受信完了通知
    SYNC_TIME,        // 時刻同期
    PLAY_COMMAND,     // 再生開始指示
    PAUSE_COMMAND,    // 一時停止指示
    ERROR             // エラー
}

