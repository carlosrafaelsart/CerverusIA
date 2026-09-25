package com.carlosart.cerberusia;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {

    private static final String SYSTEM_PROMPT = 
        "Eres Cerberus IA, creada por CarlosArt. " +
        "Contacto: carlosrafaelsart@gmail.com. Responde en español.";

    private static final String[] FREE_MODELS = {
        "qwen/qwen3.8-27b:free",
        "z-ai/glm-5.2:free",
        "nvidia/nemotron-3.5-lightning:free"
    };

    private LinearLayout chatContainer;
    private EditText inputMessage;
    private ScrollView scrollView;
    private TextToSpeech tts;
    private String apiKey;
    private int modelIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatContainer = findViewById(R.id.chat_container);
        inputMessage = findViewById(R.id.input_message);
        scrollView = findViewById(R.id.scroll_chat);
        Button btnSend = findViewById(R.id.btn_send);
        Button btnTTS = findViewById(R.id.btn_tts);
        Button btnPlus = findViewById(R.id.btn_plus);
        Button btnCam = findViewById(R.id.btn_camera);

        apiKey = getSharedPreferences("CerberusPrefs", MODE_PRIVATE)
            .getString("api_key", "");

        tts = new TextToSpeech(this, status -> {});

        btnSend.setOnClickListener(v -> sendMessage());
        btnTTS.setOnClickListener(v -> toggleTTS());
        btnPlus.setOnClickListener(v -> showAttachMenu());
        btnCam.setOnClickListener(v -> openCamera());
        
        if (apiKey.isEmpty()) {
            showApiKeyDialog();
        }
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ingresa tu clave de OpenRouter");
        final EditText input = new EditText(this);
        input.setHint("sk-or-v1-...");
        builder.setView(input);
        builder.setPositiveButton("Guardar", (d, w) -> {
            String key = input.getText().toString().trim();
            if (!key.isEmpty()) {
                getSharedPreferences("CerberusPrefs", MODE_PRIVATE).edit()
                    .putString("api_key", key).apply();
                apiKey = key;
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void sendMessage() {
        String msg = inputMessage.getText().toString().trim();
        if (msg.isEmpty()) return;
        if (apiKey.isEmpty()) {
            showApiKeyDialog();
            return;
        }
        
        addMessageBubble(msg, true);
        inputMessage.setText("");
        showTypingIndicator();

        Executors.newSingleThreadExecutor().execute(() -> {
            String response = getAIResponse(msg);
            runOnUiThread(() -> {
                hideTypingIndicator();
                addMessageBubble(response, false);
                if (tts != null && !tts.isSpeaking()) {
                    tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        });
    }

    private String getAIResponse(String userMsg) {
        for (int attempt = 0; attempt < FREE_MODELS.length; attempt++) {
            String model = FREE_MODELS[(modelIndex + attempt) % FREE_MODELS.length];
            try {
                URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(30000);
                conn.setDoOutput(true);

                String body = new JSONObject()
                    .put("model", model)
                    .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        .put(new JSONObject().put("role", "user").put("content", userMsg))
                    ).toString();

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder resp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) resp.append(line);
                    JSONObject json = new JSONObject(resp.toString());
                    return json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                }
            } catch (Exception e) { continue; }
        }
        return "Error: Verifica tu clave API en openrouter.ai/keys";
    }

    private void addMessageBubble(String text, boolean isUser) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setPadding(32, 24, 32, 24);
        bubble.setBackgroundColor(isUser ? 0xFFB8860B : 0xFF2F2F4F);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 16, 0, 16);
        bubble.setLayoutParams(lp);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xFFFFFFFF);
        bubble.addView(tv);
        chatContainer.addView(bubble);
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    private void showTypingIndicator() {
        TextView dots = new TextView(this);
        dots.setText("● ● ●");
        dots.setTextSize(22);
        dots.setTextColor(0xFFFFD700);
        dots.setPadding(32, 24, 32, 24);
        chatContainer.addView(dots);
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    private void hideTypingIndicator() {
        for (int i = chatContainer.getChildCount() - 1; i >= 0; i--) {
            View v = chatContainer.getChildAt(i);
            if (v instanceof TextView && 
                ((TextView)v).getText().equals("● ● ●")) {
                chatContainer.removeView(v);
                break;
            }
        }
    }

    private void toggleTTS() { 
        if (tts.isSpeaking()) tts.stop(); 
    }
    private void showAttachMenu() { 
        Toast.makeText(this, "Adjuntar archivos - Próximamente", Toast.LENGTH_SHORT).show(); 
    }
    private void openCamera() { 
        Toast.makeText(this, "Cámara - Próximamente", Toast.LENGTH_SHORT).show(); 
    }
    
    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
