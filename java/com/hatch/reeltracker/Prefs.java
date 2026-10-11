package com.hatch.reeltracker;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getSharedPreferences("reel_prefs", Context.MODE_PRIVATE);
    }

    public int getLimitReels() { return p.getInt("limit_reels", 50); }
    public int getLimitMinutes() { return p.getInt("limit_minutes", 60); }

    public void setLimits(int reels, int minutes) {
        p.edit().putInt("limit_reels", reels).putInt("limit_minutes", minutes).apply();
    }

    /** One alert per limit type per day. */
    public boolean alreadyNotified(String key) {
        return p.getString("notified_" + key, "").equals(StatsDb.today());
    }

    public void markNotified(String key) {
        p.edit().putString("notified_" + key, StatsDb.today()).apply();
    }

    /** Last UsageStats foreground seconds synced per package+day. */
    public long getLastFg(String pkg, String date) {
        return p.getLong("fg_" + pkg + "_" + date, 0);
    }

    public void setLastFg(String pkg, String date, long secs) {
        p.edit().putLong("fg_" + pkg + "_" + date, secs).apply();
    }

    /** Last time the tracker saw any accessibility event (watchdog). */
    public long getLastEventAt() { return p.getLong("last_event_at", 0); }

    public void setLastEventAt(long t) { p.edit().putLong("last_event_at", t).apply(); }

    /** First-run onboarding (Figma 01). */
    public boolean isOnboardingDone() { return p.getBoolean("onboarding_done", false); }

    public void setOnboardingDone() { p.edit().putBoolean("onboarding_done", true).apply(); }

    // ---- per-app limits (Figma 04) ----
    /** app = "ig" | "tt" | "fb" */
    public int getAppLimitMins(String app) {
        int def = "ig".equals(app) ? 45 : 30;
        return p.getInt("applimit_" + app, def);
    }

    public void setAppLimitMins(String app, int mins) {
        p.edit().putInt("applimit_" + app, mins).apply();
    }

    public boolean isAppLimitOn(String app) {
        boolean def = !"fb".equals(app); // Figma shows Facebook nudges off
        return p.getBoolean("applimit_on_" + app, def);
    }

    public void setAppLimitOn(String app, boolean on) {
        p.edit().putBoolean("applimit_on_" + app, on).apply();
    }

    // ---- streak (Figma 05) ----
    public int getStreak() { return p.getInt("streak", 0); }
    public void setStreak(int s) { p.edit().putInt("streak", s).apply(); }
    public int getStreakLongest() { return p.getInt("streak_longest", 0); }
    public void setStreakLongest(int s) { p.edit().putInt("streak_longest", s).apply(); }
    /** Last calendar day (yyyy-MM-dd) that was folded into the streak. */
    public String getStreakLastEval() { return p.getString("streak_last_eval", ""); }
    public void setStreakLastEval(String d) { p.edit().putString("streak_last_eval", d).apply(); }
    public boolean isMilestoneShown(int m) { return p.getBoolean("milestone_" + m, false); }
    public void setMilestoneShown(int m) { p.edit().putBoolean("milestone_" + m, true).apply(); }
}
