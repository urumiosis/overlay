package dev.project.monika.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import dev.project.monika.shared.SupabaseClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** User-started foreground service. Normal OS controls and a Stop notification remain available. */
public final class OverlayService extends Service {
    private static final String STOP_ACTION = "dev.project.monika.receiver.STOP";
    private static final String CHANNEL = "monika_receiver";
    private static final int NOTICE_ID = 427;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService poller;
    private WindowManager manager;
    private FrameLayout overlay;
    private MediaPlayer chime;
    private SupabaseClient api;
    private boolean initialLoaded;
    private String lastId;
    private boolean screenOffRegistered;
    private boolean active;
    private long lastErrorNotice;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) dismissOverlay();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        api = new SupabaseClient(this);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Monika Receiver status", NotificationManager.IMPORTANCE_LOW));
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        screenOffRegistered = true;
        chime = MediaPlayer.create(this, R.raw.selection_chime);
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && STOP_ACTION.equals(intent.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        notice("Starting Supabase connection…");
        if (active) return START_NOT_STICKY;
        if (!Settings.canDrawOverlays(this) || !api.hasSession()) {
            notice("Sign in and allow overlay permission first."); stopSelf(); return START_NOT_STICKY;
        }
        active = true;
        initialLoaded = false;
        lastId = null;
        poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleWithFixedDelay(this::checkForCommand, 0, 3, TimeUnit.SECONDS);
        return START_NOT_STICKY;
    }

    private void checkForCommand() {
        try {
            SupabaseClient.Command command = api.latestCommand();
            if (!initialLoaded) {
                initialLoaded = true;
                lastId = command == null ? null : command.id;
                ui.post(() -> notice("Connected; listening for demo commands."));
                return; // Existing SHOW records must never replay after a restart.
            }
            if (command == null || command.id.equals(lastId)) return;
            lastId = command.id;
            if ("HIDE".equals(command.action)) ui.post(this::dismissOverlay);
            else if ("SHOW".equals(command.action)) ui.post(() -> {
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                if (power != null && power.isInteractive() && Settings.canDrawOverlays(this)) showOverlay();
            });
        } catch (Exception e) {
            if (System.currentTimeMillis() - lastErrorNotice > 18000) {
                lastErrorNotice = System.currentTimeMillis();
                ui.post(() -> notice("Connection issue; retrying: " + e.getMessage()));
            }
        }
    }

    private void showOverlay() {
        dismissOverlay();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClickable(true); // Only underlying app area; protected system controls remain reachable.
        root.setOnTouchListener((view, event) -> true);
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.monika);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Monika character visual prop");
        root.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.START;
        try {
            manager.addView(root, layout); overlay = root;
            if (chime != null) {
                try { if (chime.isPlaying()) chime.pause(); chime.seekTo(0); chime.start(); }
                catch (Exception ignored) { }
            }
            notice("Monika visible. Screen off dismisses the effect.");
        } catch (Exception e) { overlay = null; notice("Overlay failed: " + e.getMessage()); }
    }

    private void dismissOverlay() {
        if (overlay != null) {
            try { manager.removeViewImmediate(overlay); } catch (Exception ignored) { }
            overlay = null;
            if (active) notice("Waiting for demo commands.");
        }
        try { if (chime != null && chime.isPlaying()) chime.pause(); }
        catch (Exception ignored) { }
    }

    private void notice(String text) {
        Intent stop = new Intent(this, OverlayService.class).setAction(STOP_ACTION);
        PendingIntent stopIntent = PendingIntent.getService(this, 12, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent openIntent = PendingIntent.getActivity(this, 11,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Monika demo receiver")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(openIntent)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop receiver", stopIntent).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTICE_ID, n);
    }

    @Override public void onDestroy() {
        active = false;
        if (poller != null) { poller.shutdownNow(); poller = null; }
        dismissOverlay();
        if (screenOffRegistered) unregisterReceiver(screenOff);
        if (chime != null) { chime.release(); chime = null; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
