package com.example.moviessync;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class MessageProtocol {
    private static final String TAG = "MessageProtocol";
    private static final String ENCODING = "UTF-8";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_DATA = "data";

    // メッセージ送信
    public static void sendMessage(BufferedWriter writer, MessageType type, JSONObject data) throws IOException {
        try {
            JSONObject message = new JSONObject();
            message.put(FIELD_TYPE, type.name());
            if (data != null) {
                message.put(FIELD_DATA, data);
            }

            String jsonString = message.toString();
            writer.write(jsonString);
            writer.newLine();
            writer.flush();
            
            Log.d(TAG, "Sent message: " + type.name());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating message", e);
            throw new IOException("Failed to create message", e);
        }
    }

    /**
     * シンプルなメッセージを送信（dataなし）
     */
    public static void sendSimpleMessage(BufferedWriter writer, MessageType type) throws IOException {
        sendMessage(writer, type, null);
    }

    /**
     * メッセージを受信
     */
    public static Message receiveMessage(BufferedReader reader) throws IOException {
        try {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }

            JSONObject messageJson = new JSONObject(line);
            String typeString = messageJson.getString(FIELD_TYPE);
            MessageType type = MessageType.valueOf(typeString);
            
            JSONObject data = null;
            if (messageJson.has(FIELD_DATA) && !messageJson.isNull(FIELD_DATA)) {
                data = messageJson.getJSONObject(FIELD_DATA);
            }

            Log.d(TAG, "Received message: " + type.name());
            return new Message(type, data);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing message", e);
            throw new IOException("Failed to parse message", e);
        }
    }

    /**
     * メッセージクラス
     */
    public static class Message {
        public final MessageType type;
        public final JSONObject data;

        public Message(MessageType type, JSONObject data) {
            this.type = type;
            this.data = data;
        }

        public String getString(String key) {
            try {
                return data != null && data.has(key) ? data.getString(key) : null;
            } catch (JSONException e) {
                return null;
            }
        }

        public long getLong(String key) {
            try {
                return data != null && data.has(key) ? data.getLong(key) : 0;
            } catch (JSONException e) {
                return 0;
            }
        }

        public int getInt(String key) {
            try {
                return data != null && data.has(key) ? data.getInt(key) : 0;
            } catch (JSONException e) {
                return 0;
            }
        }
    }
}

