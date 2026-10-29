package dev.project.monika.receiver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

/** Consent-based demo setup screen. The receiver never runs before START is tapped. */
public final class MainActivity extends Activity {
    private static final int NOTIF_REQUEST = 99;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SupabaseClient api;
    private EditText email, password;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        api = new SupabaseClient(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true); scroll.setBackgroundColor(0xff161e30);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(22), dp(30), dp(22), dp(24));
        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        col.addView(label("MONIKA / RECEIVER", 25, Color.WHITE));
        col.addView(label("For a consenting test phone. Sign in with the same demo account as Admin, grant Display over other apps, and press START RECEIVER.", 15, 0xffcbd6e7));
        col.addView(label("DEMO ACCOUNT EMAIL", 12, 0xffb4c7f9));
        email = input("demo@example.com", getPreferences(MODE_PRIVATE).getString("email", ""), false);
        col.addView(email);
        col.addView(label("PASSWORD", 12, 0xffb4c7f9));
        password = input("Demo account password", "", true); col.addView(password);
        col.addView(button("SIGN IN", this::signIn));
        col.addView(button("GRANT OVERLAY PERMISSION", () -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        col.addView(button("START RECEIVER", this::startReceiver));
        col.addView(button("STOP RECEIVER", () -> {
            stopService(new Intent(this, OverlayService.class));
            status.setText("Receiver stopped. Overlay cleared.");
        }));
        col.addView(button("SIGN OUT AND STOP", () -> {
            stopService(new Intent(this, OverlayService.class));
            api.signOut(); status.setText("Signed out. Receiver stopped.");
        }));
        status = label(api.hasSession() ? "Signed in as " + api.getEmail() : "Not signed in.", 14, Color.WHITE);
        col.addView(status);
        col.addView(label("No on-image exit button. Press power to turn the screen off and remove the character. Android navigation and the receiver's Stop notification remain available.", 13, 0xff9ca8bd));
        setContentView(scroll);
    }
    private void signIn() {
        String mail = email.getText().toString().trim();
        String pass = password.getText().toString();
        if (mail.isEmpty() || pass.isEmpty()) { status.setText("Enter your email and password."); return; }
        getPreferences(MODE_PRIVATE).edit().putString("email", mail).apply();
        status.setText("Signing in…");
        worker.execute(() -> {
            try { api.signIn(mail, pass); report("Signed in. Grant overlay permission, then start Receiver."); }
            catch (Exception e) { report("Sign in failed: " + e.getMessage()); }
        });
    }
    private void startReceiver() {
        if (!api.hasSession()) { status.setText("Sign in first."); return; }
        if (!Settings.canDrawOverlays(this)) { status.setText("Please grant Display over other apps first."); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_REQUEST); return;
        }
        startNow();
    }
    @Override public void onRequestPermissionsResult(int code, String[] names, int[] grants) {
        super.onRequestPermissionsResult(code, names, grants);
        if (code == NOTIF_REQUEST) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startNow();
            else status.setText("Allow notifications so you can stop the foreground receiver.");
        }
    }
    private void startNow() {
        try {
            startForegroundService(new Intent(this, OverlayService.class));
            status.setText("Receiver started. Check persistent notification for connection status.");
        } catch (Exception e) { status.setText("Start failed: " + e.getMessage()); }
    }
    private void report(String value) { runOnUiThread(() -> status.setText(value)); }
    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(7), 0, dp(7)); return t;
    }
    private EditText input(String hint, String initial, boolean hidden) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(initial); e.setHint(hint);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xff919db1);
        e.setInputType(hidden ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD :
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(55)); p.bottomMargin = dp(12);
        e.setLayoutParams(p); return e;
    }
    private Button button(String value, Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(Color.BLACK);
        b.setBackgroundTintList(ColorStateList.valueOf(0xffb4c7f9));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52)); p.bottomMargin = dp(10);
        b.setLayoutParams(p); b.setOnClickListener(v -> action.run()); return b;
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
