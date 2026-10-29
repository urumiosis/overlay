package dev.project.monika.shared;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minimal Java Supabase Auth + PostgREST client; network methods MUST run off the UI thread. */
public final class SupabaseClient {
    private final SharedPreferences prefs;
    public SupabaseClient(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("supabase_session_v1", Context.MODE_PRIVATE);
    }

    public boolean hasSession() {
        return !prefs.getString("refresh_token", "").isEmpty() && !getUserId().isEmpty();
    }
    public String getUserId() { return prefs.getString("user_id", ""); }
    public String getEmail() { return prefs.getString("email", ""); }
    public void signOut() { prefs.edit().clear().apply(); }

    public void signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject(); body.put("email", email); body.put("password", password);
        JSONObject response = request("POST", "/auth/v1/token?grant_type=password", body, null, null);
        saveSession(response, email);
        if (!hasSession()) throw new IOException("No session returned; check your email verification.");
    }

    /** If email verification is on, signup can return a user but no access token. */
    public boolean signUp(String email, String password) throws Exception {
        JSONObject body = new JSONObject(); body.put("email", email); body.put("password", password);
        JSONObject response = request("POST", "/auth/v1/signup", body, null, null);
        if (response.optString("access_token").isEmpty()) return false;
        saveSession(response, email);
        return true;
    }

    private synchronized void saveSession(JSONObject response, String email) throws JSONException {
        String access = response.optString("access_token", "");
        String refresh = response.optString("refresh_token", "");
        JSONObject user = response.optJSONObject("user");
        String userId = user != null ? user.optString("id", getUserId()) : getUserId();
        if (access.isEmpty() || refresh.isEmpty() || userId.isEmpty()) return;
        long seconds = response.optLong("expires_in", 3600);
        prefs.edit().putString("access_token", access)
                .putString("refresh_token", refresh).putString("user_id", userId)
                .putString("email", email.isEmpty() ? getEmail() : email)
                .putLong("expires_at", System.currentTimeMillis() / 1000 + seconds)
                .commit();
    }

    private synchronized String validToken() throws Exception {
        if (!hasSession()) throw new IOException("Not signed in; return to the app and sign in.");
        String token = prefs.getString("access_token", "");
        if (!token.isEmpty() && System.currentTimeMillis() / 1000 < prefs.getLong("expires_at", 0) - 90) return token;
        String oldRefresh = prefs.getString("refresh_token", "");
        JSONObject body = new JSONObject().put("refresh_token", oldRefresh);
        JSONObject refreshed = request("POST", "/auth/v1/token?grant_type=refresh_token", body, null, null);
        saveSession(refreshed, getEmail());
        token = prefs.getString("access_token", "");
        if (token.isEmpty()) throw new IOException("Session refresh failed. Sign in again.");
        return token;
    }

    public void sendCommand(String action) throws Exception {
        if (!"SHOW".equals(action) && !"HIDE".equals(action)) throw new IllegalArgumentException("Bad command");
        JSONObject record = new JSONObject()
                .put("user_id", getUserId())
                .put("command_id", UUID.randomUUID().toString())
                .put("action", action);
        request("POST", "/rest/v1/monika_commands?on_conflict=user_id", record, validToken(), "resolution=merge-duplicates,return=minimal");
    }

    /** Returns null if no commands have been sent by this account yet. */
    public Command latestCommand() throws Exception {
        String path = "/rest/v1/monika_commands?select=command_id,action&user_id=eq." + getUserId() + "&limit=1";
        String raw = requestRaw("GET", path, null, validToken(), null);
        JSONArray rows = new JSONArray(raw);
        if (rows.length() == 0) return null;
        JSONObject row = rows.getJSONObject(0);
        return new Command(row.getString("command_id"), row.getString("action"));
    }

    public static final class Command {
        public final String id;
        public final String action;
        Command(String id, String action) { this.id = id; this.action = action; }
    }

    private JSONObject request(String method, String path, JSONObject data, String token, String prefer) throws Exception {
        String raw = requestRaw(method, path, data, token, prefer);
        return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private String requestRaw(String method, String path, JSONObject data, String token, String prefer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(SupabaseConfig.URL + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY);
        conn.setRequestProperty("Accept", "application/json");
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        if (prefer != null) conn.setRequestProperty("Prefer", prefer);
        try {
            if (data != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] payload = data.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream stream = conn.getOutputStream()) { stream.write(payload); }
            }
            int code = conn.getResponseCode();
            String raw = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                String detail = raw;
                try {
                    JSONObject error = new JSONObject(raw);
                    detail = error.optString("msg", error.optString("error_description", error.optString("message", raw)));
                } catch (JSONException ignored) { }
                throw new IOException("Supabase HTTP " + code + ": " + detail);
            }
            return raw;
        } finally { conn.disconnect(); }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }
}
