package com.hatch.reeltracker;

import android.accessibilityservice.AccessibilityService;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.format.DateFormat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.2:
 *  - Reel counting gated by real Reels-viewer detection (view ids / right-rail),
 *    not by widget class names.
 *  - Screen time from UsageStatsManager (bulletproof; no reliance on
 *    window-change events surviving MIUI).
 *  - Ongoing "tracking..." notification while a target app is foreground.
 */
public class ReelAccessibilityService extends AccessibilityService {

    public static final String IG = "com.instagram.android";
    public static final String FB = "com.facebook.katana";
    public static final String TT1 = "com.zhiliaoapp.musically";
    public static final String TT2 = "com.ss.android.ugc.trill";
    private static final String CHANNEL_LIMITS = "reel_limits";
    private static final String CHANNEL_TRACK = "reel_tracking";
    private static final int NOTIF_TRACK_ID = 1001;

    // ---- diagnostics (read by DiagnosticsActivity) ----
    public static volatile long serviceStartedAt = 0;
    public static volatile int eventsSeen = 0;
    public static volatile int scrollEventsSeen = 0;
    public static volatile int reelsCounted = 0;
    public static volatile String lastReelInfo = "-";
    public static final Map<String, Boolean> reelsModeState =
            Collections.synchronizedMap(new HashMap<String, Boolean>());
    public static final List<String> eventLog =
            Collections.synchronizedList(new ArrayList<String>());

    public static void dlog(String s) {
        synchronized (eventLog) {
            eventLog.add(0, DateFormat.format("HH:mm:ss", System.currentTimeMillis()) + " " + s);
            while (eventLog.size() > 50) eventLog.remove(eventLog.size() - 1);
        }
    }

    private StatsDb db;
    private Prefs prefs;
    private NotificationManager nm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final HashMap<String, Long> lastScan = new HashMap<>();
    private String trackingPkg = null;
    private long lastEventWrite = 0L;

    private long lastReelAt = 0L;
    private int lastToIndex = -1;
    private String lastToPkg = null;
    /** Last counted-reel time per app: gaps between reels ≈ reels watching time. */
    private final HashMap<String, Long> lastReelTime = new HashMap<>();

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                syncTimeFromUsageStats();
                checkLimits();
            } catch (Exception ignored) {}
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    public void onServiceConnected() {
        db = new StatsDb(this);
        prefs = new Prefs(this);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch1 = new NotificationChannel(CHANNEL_LIMITS,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        ch1.setDescription(getString(R.string.channel_desc));
        nm.createNotificationChannel(ch1);
        NotificationChannel ch2 = new NotificationChannel(CHANNEL_TRACK,
                getString(R.string.track_channel_name), NotificationManager.IMPORTANCE_LOW);
        ch2.setDescription(getString(R.string.track_channel_desc));
        nm.createNotificationChannel(ch2);
        serviceStartedAt = System.currentTimeMillis();
        dlog("service connected v1.3");
        startPersistent();
        seedCurrentWindow();
        syncTimeFromUsageStats();
        handler.post(tick);
    }

    /**
     * Become a foreground service so MIUI/OEMs don't kill tracking when the
     * app is swiped away. Falls back to a plain notification if the
     * platform refuses.
     */
    private void startPersistent() {
        Notification n = buildNotif("ScrollTrack active", "Background me chal raha hai");
        try {
            startForeground(NOTIF_TRACK_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Exception e1) {
            try {
                startForeground(NOTIF_TRACK_ID, n);
            } catch (Exception e2) {
                try { nm.notify(NOTIF_TRACK_ID, n); } catch (Exception ignored) {}
            }
        }
    }

    private Notification buildNotif(String title, String text) {
        return new Notification.Builder(this, CHANNEL_TRACK)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void updateNotif(String title, String text) {
        try { nm.notify(NOTIF_TRACK_ID, buildNotif(title, text)); }
        catch (Exception ignored) {}
    }

    /**
     * If the service was (re)bound while a target app is already in the
     * foreground, the system won't send a window-change event — seed
     * tracking from the current window directly.
     */
    private void seedCurrentWindow() {
        AccessibilityNodeInfo root = null;
        try { root = getRootInActiveWindow(); } catch (Exception ignored) {}
        if (root == null) return;
        String pkg = "";
        try {
            CharSequence p = root.getPackageName();
            if (p != null) pkg = p.toString();
        } catch (Exception ignored) {}
        try { root.recycle(); } catch (Exception ignored) {}
        if (isTarget(pkg)) {
            onWindow(pkg);
            scanReelsMode(pkg, true);
            dlog("seeded foreground " + shortPkg(pkg));
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        int type = e.getEventType();

        // Watchdog heartbeat (throttled): proves the service is alive.
        long now = System.currentTimeMillis();
        if (prefs != null && now - lastEventWrite > 30000) {
            lastEventWrite = now;
            try { prefs.setLastEventAt(now); } catch (Exception ignored) {}
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            onWindow(pkg);
            if (isTarget(pkg)) scanReelsMode(pkg, true);
            return;
        }
        if (!isTarget(pkg)) return;

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            scanReelsMode(pkg, false);
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            eventsSeen++;
            scrollEventsSeen++;
            Boolean mode = reelsModeState.get(pkg);
            if (Boolean.TRUE.equals(mode)) {
                maybeCountReel(pkg, e);
            }
        }
    }

    private static boolean isTarget(String pkg) {
        return IG.equals(pkg) || FB.equals(pkg) || isTikTok(pkg);
    }

    public static boolean isTikTok(String pkg) {
        return TT1.equals(pkg) || TT2.equals(pkg);
    }

    private static String shortPkg(String pkg) {
        if (IG.equals(pkg)) return "IG";
        if (FB.equals(pkg)) return "FB";
        return "TT";
    }

    /** Throttled Reels-viewer detection. */
    private void scanReelsMode(String pkg, boolean force) {
        long now = System.currentTimeMillis();
        Long last = lastScan.get(pkg);
        if (!force && last != null && now - last < 2000) return;
        lastScan.put(pkg, now);
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
        } catch (Exception ignored) {}
        if (root == null) return;
        boolean mode;
        try {
            mode = ReelsModeDetector.isReelsMode(root, pkg);
        } catch (Exception ex) {
            mode = false;
        } finally {
            try { root.recycle(); } catch (Exception ignored) {}
        }
        Boolean old = reelsModeState.get(pkg);
        reelsModeState.put(pkg, mode);
        if (old == null || old != mode) {
            dlog("reelsMode " + shortPkg(pkg) + " = " + mode);
        }
    }

    private void onWindow(String pkg) {
        if (isTarget(pkg)) {
            if (!pkg.equals(trackingPkg)) {
                trackingPkg = pkg;
                lastScan.remove(pkg);
                reelsModeState.remove(pkg);
                scanReelsMode(pkg, true);
                showTracking(pkg);
                dlog("foreground " + shortPkg(pkg));
            }
        } else if (trackingPkg != null) {
            trackingPkg = null;
            hideTracking();
        }
    }

    /** Count one reel per page change, only for fullscreen scrolled views. */
    private void maybeCountReel(String pkg, AccessibilityEvent e) {
        boolean full = false;
        AccessibilityNodeInfo src = null;
        try {
            src = e.getSource();
        } catch (Exception ignored) {}
        if (src != null) {
            try {
                Rect b = new Rect();
                src.getBoundsInScreen(b);
                int sh = getResources().getDisplayMetrics().heightPixels;
                full = b.height() > sh * 0.65;
            } catch (Exception ignored) {}
        }
        if (!full) return;

        int toIndex = -1;
        try { toIndex = e.getToIndex(); } catch (Exception ignored) {}
        long now = System.currentTimeMillis();
        boolean countIt = false;
        if (toIndex >= 0) {
            boolean changed = !pkg.equals(lastToPkg) || toIndex != lastToIndex;
            if (changed && now - lastReelAt > 900) {
                countIt = true;
                lastToIndex = toIndex;
            } else if (!changed) {
                lastToIndex = toIndex;
            }
            lastToPkg = pkg;
        } else if (now - lastReelAt > 2500) {
            countIt = true;
            lastToPkg = pkg;
        }
        if (countIt) {
            lastReelAt = now;
            // Attribute the gap since the previous reel to reels-watching time
            // (capped: a long pause is not watching).
            Long prevT = lastReelTime.get(pkg);
            if (prevT != null) {
                long delta = (now - prevT) / 1000;
                if (delta > 0 && delta <= 120) db.addReelsSecs(pkg, (int) delta);
            }
            lastReelTime.put(pkg, now);
            reelsCounted++;
            db.add(pkg, 1, 0);
            db.addHour(pkg);
            lastReelInfo = shortPkg(pkg) + " idx=" + toIndex;
            dlog("REEL #" + reelsCounted + " " + lastReelInfo);
            checkLimits();
        }
    }

    /** Bulletproof screen time: UsageStatsManager is the single source of truth. */
    private void syncTimeFromUsageStats() {
        if (db == null || prefs == null) return;
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        try {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
        } catch (Exception ex) {
            return;
        }
        if (mode != AppOpsManager.MODE_ALLOWED) return;
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(c.getTimeInMillis(), now);
            if (map == null) return;
            String today = StatsDb.today();
            for (String pkg : new String[]{IG, FB, TT1, TT2}) {
                UsageStats s = map.get(pkg);
                long fgSecs = (s == null) ? 0 : s.getTotalTimeInForeground() / 1000;
                long prev = prefs.getLastFg(pkg, today);
                if (fgSecs > prev) {
                    db.add(pkg, 0, (int) (fgSecs - prev));
                    prefs.setLastFg(pkg, today, fgSecs);
                }
            }
        } catch (Exception ignored) {}
    }

    private void showTracking(String pkg) {
        String app = IG.equals(pkg) ? "Instagram" : (FB.equals(pkg) ? "Facebook" : "TikTok");
        updateNotif("ScrollTrack chal raha hai", app + " • reels gin raha hai");
    }

    private void hideTracking() {
        // Keep the persistent notification, just reset to idle text.
        updateNotif("ScrollTrack active", "Background me chal raha hai");
    }

    private void checkLimits() {
        if (db == null || prefs == null) return;
        StatsDb.Day d = db.getDay(StatsDb.today());
        int limitReels = prefs.getLimitReels();
        if (limitReels > 0 && d.totalReels() >= limitReels && !prefs.alreadyNotified("reels")) {
            prefs.markNotified("reels");
            alert("Daily reel limit reached",
                    "Aaj " + d.totalReels() + " reels dekh li (limit " + limitReels + "). Thoda break lo!");
        }
        int limitMin = prefs.getLimitMinutes();
        if (limitMin > 0 && d.totalSecs() / 60 >= limitMin && !prefs.alreadyNotified("mins")) {
            prefs.markNotified("mins");
            alert("Daily screen-time limit reached",
                    "Instagram + Facebook + TikTok par aaj " + (d.totalSecs() / 60) + " min ho gaye (limit " + limitMin + " min).");
        }
        checkAppLimit("ig", "Instagram", d.igSecs);
        checkAppLimit("tt", "TikTok", d.ttSecs);
        checkAppLimit("fb", "Facebook", d.fbSecs);
    }

    /** Per-app nudge (Figma 04): fires once per day when an enabled app limit is hit. */
    private void checkAppLimit(String app, String appName, int secs) {
        if (db == null || prefs == null) return;
        if (!prefs.isAppLimitOn(app)) return;
        int lim = prefs.getAppLimitMins(app);
        if (lim > 0 && secs / 60 >= lim && !prefs.alreadyNotified("applimit_" + app)) {
            prefs.markNotified("applimit_" + app);
            alert(appName + " limit reached",
                    appName + " par aaj " + (secs / 60) + " min ho gaye (limit " + lim + " min). Thoda break lo!");
        }
    }

    private void alert(String title, String text) {
        try {
            Notification n = new Notification.Builder(this, CHANNEL_LIMITS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .build();
            nm.notify(title.hashCode(), n);
        } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() { hideTracking(); }

    @Override public boolean onUnbind(android.content.Intent intent) {
        dlog("service unbound");
        try { stopForeground(true); } catch (Exception ignored) {}
        handler.removeCallbacks(tick);
        return super.onUnbind(intent);
    }
}
