package com.example.moviessync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

public final class TimeSyncManager {
	private static final String PREF = "time_sync_prefs";
	private static final String KEY_OFFSET_MS = "offset_ms";
	private static final String KEY_HAS_SAMPLE = "has_sample";
	private static final double ALPHA = 0.25;

	private static volatile TimeSyncManager instance;

	private final SharedPreferences prefs;
	private final AtomicLong offsetMs = new AtomicLong(0);
	private volatile boolean hasSample = false;

	private TimeSyncManager(Context context) {
		this.prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
		if (prefs.getBoolean(KEY_HAS_SAMPLE, false)) {
			offsetMs.set(prefs.getLong(KEY_OFFSET_MS, 0L));
			hasSample = true;
		}
	}

	public static TimeSyncManager getInstance(Context context) {
		if (instance == null) {
			synchronized (TimeSyncManager.class) {
				if (instance == null) {
					instance = new TimeSyncManager(context);
				}
			}
		}
		return instance;
	}

	public long nowServerMillis() {
		return System.currentTimeMillis() + offsetMs.get();
	}

	public long toServerTime(long clientTimeMillis) {
		return clientTimeMillis + offsetMs.get();
	}

	public boolean hasValidSample() {
		return hasSample;
	}

	// Member側で使用：サーバから受け取ったserverNowMillisとRTT(tRecv-tSend)からオフセットを更新
	public void updateOffsetSample(long tSendElapsedMs, long tRecvElapsedMs, long serverNowMillis) {
		long rttMs = Math.max(0, tRecvElapsedMs - tSendElapsedMs);
		long clientMidpoint = System.currentTimeMillis() - rttMs / 2;
		long sampleOffset = serverNowMillis - clientMidpoint;

		long prev = offsetMs.get();
		long updated;
		if (hasSample) {
			updated = (long) Math.round(prev * (1.0 - ALPHA) + sampleOffset * ALPHA);
		} else {
			updated = sampleOffset;
		}
		offsetMs.set(updated);
		hasSample = true;
		prefs.edit().putLong(KEY_OFFSET_MS, updated).putBoolean(KEY_HAS_SAMPLE, true).apply();
	}
}


