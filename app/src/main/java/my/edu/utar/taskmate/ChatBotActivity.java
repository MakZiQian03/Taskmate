package my.edu.utar.taskmate;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatBotActivity extends BaseActivity {

    private EditText input;
    private RecyclerView rv;
    private ChatAdapter adapter;
    private java.util.ArrayList<ChatMessage> data = new java.util.ArrayList<>();
    private ProgressBar progress;
    private Button sendButton;
    private Button clearButton;
    private String historyKey = "chat_history";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .writeTimeout(java.time.Duration.ofSeconds(60))
            .callTimeout(java.time.Duration.ofSeconds(75))
            .retryOnConnectionFailure(true)
            .build();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ImageView icon = findViewById(R.id.ivIcon);
        input = findViewById(R.id.etPrompt);
        sendButton = findViewById(R.id.btnSend);
        clearButton = findViewById(R.id.btnClear);
        rv = findViewById(R.id.rvMessages);
        progress = findViewById(R.id.progress);

        icon.setImageResource(R.drawable.ic_chatbot);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(data);
        rv.setAdapter(adapter);

        // Use per-user history key if logged in
        try {
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null && auth.getCurrentUser().getUid() != null) {
                historyKey = "chat_history_" + auth.getCurrentUser().getUid();
            }
        } catch (Exception ignored) {}

        sendButton.setOnClickListener(v -> doSend());
        clearButton.setOnClickListener(v -> doClear());

        // Load persisted history
        loadHistoryFromPrefs();
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_chatbot;
    }

    @Override
    protected int getBottomNavMenuId() {
        return R.id.nav_chatbot;
    }

    private void doSend() {
        String prompt = input.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) {
            Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show();
            return;
        }

        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "GEMINI_API_KEY not configured", Toast.LENGTH_LONG).show();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        sendButton.setEnabled(false);

        // Append user message to UI/history first
        ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, prompt, System.currentTimeMillis());
        data.add(userMsg);
        adapter.notifyItemInserted(data.size() - 1);
        rv.scrollToPosition(data.size() - 1);
        saveHistoryToPrefs();

        // Clear input after sending
        input.setText("");
        input.requestFocus();

        try {
            // Build contents with history context
            JSONArray contentsArr = new JSONArray();
            for (ChatMessage m : data) {
                JSONArray hParts = new JSONArray().put(new JSONObject().put("text", m.text));
                String role = m.role == ChatMessage.Role.USER ? "user" : "model";
                contentsArr.put(new JSONObject().put("role", role).put("parts", hParts));
            }

            Request request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                    .post(RequestBody.create(new JSONObject().put("contents", contentsArr).toString(), JSON))
                    .build();

            enqueueWithRetry(request, 0);
        } catch (Exception ex) {
            progress.setVisibility(View.GONE);
            sendButton.setEnabled(true);
            data.add(new ChatMessage(ChatMessage.Role.BOT, "Request build failed: " + ex.getMessage(), System.currentTimeMillis()));
            adapter.notifyItemInserted(data.size() - 1);
            rv.scrollToPosition(data.size() - 1);
        }
    }

    private void enqueueWithRetry(Request request, int attempt) {
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (attempt < 1) {
                    new android.os.Handler(getMainLooper()).postDelayed(() -> enqueueWithRetry(request, attempt + 1), 1500);
                } else {
                    runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        sendButton.setEnabled(true);
                        data.add(new ChatMessage(ChatMessage.Role.BOT, "Request failed: " + e.getMessage(), System.currentTimeMillis()));
                        adapter.notifyItemInserted(data.size() - 1);
                        rv.scrollToPosition(data.size() - 1);
                        saveHistoryToPrefs();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    sendButton.setEnabled(true);
                    if (!response.isSuccessful()) {
                        data.add(new ChatMessage(ChatMessage.Role.BOT, "Error: " + response.code() + "\n" + body, System.currentTimeMillis()));
                        adapter.notifyItemInserted(data.size() - 1);
                        rv.scrollToPosition(data.size() - 1);
                        saveHistoryToPrefs();
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(body);
                        JSONArray candidates = json.optJSONArray("candidates");
                        String text = "";
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject c0 = candidates.getJSONObject(0);
                            JSONArray cParts = c0.getJSONObject("content").optJSONArray("parts");
                            if (cParts != null && cParts.length() > 0) {
                                text = cParts.getJSONObject(0).optString("text", "");
                            }
                        }
                        String reply = text.isEmpty() ? "(No response)" : text;
                        data.add(new ChatMessage(ChatMessage.Role.BOT, reply, System.currentTimeMillis()));
                        adapter.notifyItemInserted(data.size() - 1);
                        rv.scrollToPosition(data.size() - 1);
                        saveHistoryToPrefs();
                    } catch (Exception ex) {
                        data.add(new ChatMessage(ChatMessage.Role.BOT, "Parsing failed: " + ex.getMessage(), System.currentTimeMillis()));
                        adapter.notifyItemInserted(data.size() - 1);
                        rv.scrollToPosition(data.size() - 1);
                        saveHistoryToPrefs();
                    }
                });
            }
        });
    }

    private void loadHistoryFromPrefs() {
        try {
            String json = prefs.getString(historyKey, null);
            if (json == null || json.isEmpty()) return;
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                String roleStr = o.optString("role", "user");
                ChatMessage.Role role = "bot".equals(roleStr) ? ChatMessage.Role.BOT : ChatMessage.Role.USER;
                String text = o.optString("text", "");
                long ts = o.optLong("ts", System.currentTimeMillis());
                data.add(new ChatMessage(role, text, ts));
            }
            adapter.notifyDataSetChanged();
            rv.scrollToPosition(Math.max(0, data.size() - 1));
        } catch (Exception ignored) {}
    }

    private void saveHistoryToPrefs() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (ChatMessage m : data) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("role", m.role == ChatMessage.Role.BOT ? "bot" : "user");
                o.put("text", m.text);
                o.put("ts", m.timestamp);
                arr.put(o);
            }
            prefs.edit().putString(historyKey, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void doClear() {
        data.clear();
        adapter.notifyDataSetChanged();
        prefs.edit().remove(historyKey).apply();
        Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
    }
}
