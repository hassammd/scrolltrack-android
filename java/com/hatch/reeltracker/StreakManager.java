package com.hatch.reeltracker;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Streak system (Figma 05).
 * A day is "clean" when the user stayed within their daily screen-time goal.
 * A missed day never erases progress: the streak simply doesn't grow that day.
 * Milestones: 7, 30, 100 days.
 */
public class StreakManager {
    private final Prefs prefs;
    private final StatsDb db;
    private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public StreakManager(Context c) {
        prefs = new Prefs(c);
        db = new StatsDb(c);
    }

    /**
     * Folds every unevaluated calendar day through yesterday into the streak.
     * @return the milestone (7/30/100) newly reached in this pass, or 0.
     */
    public int rollForward() {
        String lastEval = prefs.getStreakLastEval();
        String yesterday = StatsDb.dateDaysAgo(1);
        if (lastEval.isEmpty()) {
            // First run: start counting from today, never backfill phantom days.
            prefs.setStreakLastEval(yesterday);
            return 0;
        }
        int streak = prefs.getStreak();
        int longest = prefs.getStreakLongest();
        int newMilestone = 0;

        Calendar c = Calendar.getInstance();
        try {
            c.setTime(DF.parse(lastEval));
        } catch (Exception e) {
            prefs.setStreakLastEval(yesterday);
            return 0;
        }
        c.add(Calendar.DAY_OF_YEAR, 1); // day after last evaluated

        Calendar yest = Calendar.getInstance();
        try {
            yest.setTime(DF.parse(yesterday));
        } catch (Exception e) {
            return 0;
        }

        // Cap the backfill window: very old unevaluated days are skipped,
        // not auto-counted as clean.
        Calendar floor = Calendar.getInstance();
        floor.add(Calendar.DAY_OF_YEAR, -7);
        if (c.before(floor)) c = (Calendar) floor.clone();

        while (!c.after(yest)) {
            StatsDb.Day d = db.getDay(DF.format(c.getTime()));
            if (isClean(d)) {
                streak++;
                if (streak > longest) {
                    longest = streak;
                    prefs.setStreakLongest(longest);
                }
                if ((streak == 7 || streak == 30 || streak == 100)
                        && !prefs.isMilestoneShown(streak)) {
                    prefs.setMilestoneShown(streak);
                    newMilestone = streak;
                }
            }
            // Broken day: streak is kept, just doesn't grow (Figma: "A missed
            // day never erases your progress.").
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        prefs.setStreak(streak);
        prefs.setStreakLastEval(yesterday);
        return newMilestone;
    }

    /** Clean day = stayed within the daily screen-time goal (the Figma "goal"). */
    public boolean isClean(StatsDb.Day d) {
        int goalSecs = Math.max(1, prefs.getLimitMinutes()) * 60;
        return d.totalSecs() <= goalSecs;
    }

    /** Total seconds reclaimed over the last 7 days vs the same days a week earlier. */
    public long reclaimedThisWeekSecs() {
        long sum = 0;
        for (int k = 0; k < 7; k++) {
            StatsDb.Day d = db.getDay(StatsDb.dateDaysAgo(k));
            StatsDb.Day p = db.getDay(StatsDb.dateDaysAgo(k + 7));
            sum += Math.max(0, p.totalSecs() - d.totalSecs());
        }
        return sum;
    }
}
