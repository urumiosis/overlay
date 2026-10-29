package dev.project.monika.admin;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.project.monika.shared.SupabaseClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Admin controls only the signed-in demo account's command row. */
public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SupabaseClient api;
    private EditText email, password;
    private TextView status;
    private Button show, hide;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new SupabaseClient(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(0xff161e30);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(30), dp(22), dp(24));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        col.addView(label("MONIKA / ADMIN", 25, Color.WHITE));
        col.addView(label("Supabase is already configured. Sign in with the same dedicated demo account on both phones. Different Wi-Fi networks are supported.", 15, 0xffcbd6e7));
        col.addView(label("DEMO ACCOUNT EMAIL", 12, 0xffb4c7f9));
        email = input("demo@example.com", getPreferences(MODE_PRIVATE).getString("email", ""), false);
        col.addView(email);
        col.addView(label("PASSWORD", 12, 0xffb4c7f9));
        password = input("Demo account password", "", true);
        col.addView(password);
        col.addView(button("CREATE ACCOUNT", () -> authenticate(true)));
        col.addView(button("SIGN IN", () -> authenticate(false)));
        col.addView(button("SIGN OUT", () -> { api.signOut(); showStatus("Signed out."); update(); }));
        show = button("SHOW MONIKA", () -> send("SHOW")); col.addView(show);
        hide = button("HIDE MONIKA", () -> send("HIDE")); col.addView(hide);
        status = label(api.hasSession() ? "Signed in as " + api.getEmail() : "Sign in before sending commands.", 14, Color.WHITE);
        col.addView(status);
        col.addView(label("The Receiver must be started, online and signed into this same demo account. Commands usually reach it within a few seconds.", 13, 0xff9ca8bd));
        setContentView(scroll); update();
    }

    private void authenticate(boolean register) {
        String mail = email.getText().toString().trim();
        String pass = password.getText().toString();
        if (!mail.contains("@") || pass.length() < 6) { showStatus("Enter a valid email and password (6+ characters)."); return; }
        getPreferences(MODE_PRIVATE).edit().putString("email", mail).apply();
        showStatus(register ? "Creating account…" : "Signing in…");
        worker.execute(() -> {
            try {
                if (register) {
                    boolean loggedIn = api.signUp(mail, pass);
                    showStatus(loggedIn ? "Account created and signed in." : "Account created. Check your email for a confirmation link, then SIGN IN.");
                } else {
                    api.signIn(mail, pass);
                    showStatus("Signed in as " + api.getEmail());
                }
            } catch (Exception e) { showStatus("Authentication error: " + e.getMessage()); }
            runOnUiThread(this::update);
        });
    }

    private void send(String action) {
        if (!api.hasSession()) { showStatus("Sign in first."); return; }
        show.setEnabled(false); hide.setEnabled(false);
        showStatus("Sending " + action + "…");
        worker.execute(() -> {
            try {
                api.sendCommand(action);
                showStatus(action + " saved. Receiver checks every 3 seconds.");
            } catch (Exception e) { showStatus("Command failed: " + e.getMessage()); }
            runOnUiThread(this::update);
        });
    }

    private void showStatus(String message) { runOnUiThread(() -> { if (status != null) status.setText(message); }); }
    private void update() {
        if (show != null) show.setEnabled(api.hasSession());
        if (hide != null) hide.setEnabled(api.hasSession());
    }
    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(7), 0, dp(7)); return t;
    }
    private EditText input(String hint, String initial, boolean hidden) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true); edit.setText(initial); edit.setHint(hint); edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0xff919db1);
        edit.setInputType(hidden ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD :
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(55)); p.bottomMargin = dp(12);
        edit.setLayoutParams(p); return edit;
    }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(Color.BLACK);
        b.setBackgroundTintList(ColorStateList.valueOf(value.contains("SHOW") ? 0xffe58bac : 0xffb4c7f9));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52)); p.bottomMargin = dp(10);
        b.setLayoutParams(p); b.setOnClickListener(v -> action.run()); return b;
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
