package com.hatch.reeltracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One row per calendar day: reel counts + foreground seconds for IG and FB. */
public class StatsDb extends SQLiteOpenHelper {

    private static final String DB = "reelstats.db";
    private static final int VER = 4;

    public static class Day {
        public String date;
        public int igReels, fbReels, ttReels, igSecs, fbSecs, ttSecs;
        public int igRsecs, fbRsecs, ttRsecs; // seconds spent inside reels viewers
        public int totalReels() { return igReels + fbReels + ttReels; }
        public int totalSecs() { return igSecs + fbSecs + ttSecs; }
        public int reelsSecs() { return igRsecs + fbRsecs + ttRsecs; }
    }

    public StatsDb(Context c) { super(c, DB, null, VER); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE days(date TEXT PRIMARY KEY," +
                "ig_reels INTEGER DEFAULT 0, fb_reels INTEGER DEFAULT 0, tt_reels INTEGER DEFAULT 0," +
                "ig_secs INTEGER DEFAULT 0, fb_secs INTEGER DEFAULT 0, tt_secs INTEGER DEFAULT 0," +
                "ig_rsecs INTEGER DEFAULT 0, fb_rsecs INTEGER DEFAULT 0, tt_rsecs INTEGER DEFAULT 0)");
        d.execSQL("CREATE TABLE hours(date TEXT, hour INTEGER," +
                "ig INTEGER DEFAULT 0, fb INTEGER DEFAULT 0, tt INTEGER DEFAULT 0," +
                "PRIMARY KEY(date, hour))");
    }

    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) {
            d.execSQL("ALTER TABLE days ADD COLUMN tt_reels INTEGER DEFAULT 0");
            d.execSQL("ALTER TABLE days ADD COLUMN tt_secs INTEGER DEFAULT 0");
        }
        if (o < 3) {
            d.execSQL("CREATE TABLE IF NOT EXISTS hours(date TEXT, hour INTEGER," +
                    "ig INTEGER DEFAULT 0, fb INTEGER DEFAULT 0, tt INTEGER DEFAULT 0," +
                    "PRIMARY KEY(date, hour))");
        }
        if (o < 4) {
            d.execSQL("ALTER TABLE days ADD COLUMN ig_rsecs INTEGER DEFAULT 0");
            d.execSQL("ALTER TABLE days ADD COLUMN fb_rsecs INTEGER DEFAULT 0");
            d.execSQL("ALTER TABLE days ADD COLUMN tt_rsecs INTEGER DEFAULT 0");
        }
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String dateDaysAgo(int n) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -n);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    private static String col(String pkg, String kind) {
        String pre;
        if (ReelAccessibilityService.IG.equals(pkg)) pre = "ig_";
        else if (ReelAccessibilityService.FB.equals(pkg)) pre = "fb_";
        else pre = "tt_";
        return pre + kind;
    }

    private void ensureRow(SQLiteDatabase d, String date) {
        ContentValues v = new ContentValues();
        v.put("date", date);
        d.insertWithOnConflict("days", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized void add(String pkg, int reels, int secs) {
        if (reels == 0 && secs == 0) return;
        SQLiteDatabase d = getWritableDatabase();
        String t = today();
        ensureRow(d, t);
        StringBuilder sb = new StringBuilder("UPDATE days SET ");
        ArrayList<Object> args = new ArrayList<>();
        boolean first = true;
        if (reels > 0) { sb.append(col(pkg, "reels")).append("=").append(col(pkg, "reels")).append("+?"); args.add(reels); first = false; }
        if (secs > 0) { if (!first) sb.append(","); sb.append(col(pkg, "secs")).append("=").append(col(pkg, "secs")).append("+?"); args.add(secs); }
        sb.append(" WHERE date=?"); args.add(t);
        d.execSQL(sb.toString(), args.toArray());
        d.close();
    }

    public synchronized Day getDay(String date) {
        Day day = new Day();
        day.date = date;
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.rawQuery("SELECT ig_reels,fb_reels,tt_reels,ig_secs,fb_secs,tt_secs," +
                "ig_rsecs,fb_rsecs,tt_rsecs FROM days WHERE date=?",
                new String[]{date});
        if (c.moveToFirst()) {
            day.igReels = c.getInt(0); day.fbReels = c.getInt(1); day.ttReels = c.getInt(2);
            day.igSecs = c.getInt(3); day.fbSecs = c.getInt(4); day.ttSecs = c.getInt(5);
            day.igRsecs = c.getInt(6); day.fbRsecs = c.getInt(7); day.ttRsecs = c.getInt(8);
        }
        c.close(); d.close();
        return day;
    }

    /** Seconds spent inside reels viewers for one app today (smart estimate). */
    public synchronized void addReelsSecs(String pkg, int secs) {
        if (secs <= 0) return;
        SQLiteDatabase d = getWritableDatabase();
        ensureRow(d, today());
        String c = col(pkg, "rsecs");
        d.execSQL("UPDATE days SET " + c + "=" + c + "+? WHERE date=?",
                new Object[]{secs, today()});
        d.close();
    }

    /** Last 7 days, oldest first. */
    public synchronized List<Day> last7() {
        return lastNDays(7);
    }

    /** Last N days, oldest first, labels as short weekday names. */
    public synchronized List<Day> lastNDays(int n) {
        ArrayList<Day> out = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat lbl = new SimpleDateFormat("EEE", Locale.US);
        for (int i = n - 1; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String date = f.format(cal.getTime());
            Day d = getDay(date);
            d.date = lbl.format(cal.getTime()); // label for chart
            out.add(d);
        }
        return out;
    }

    /** Day N days ago (1 = yesterday), full date preserved in .date. */
    public synchronized Day getDayOffset(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        return getDay(date);
    }

    /** Log one reel into today's hourly bucket (for the Analytics day chart). */
    public synchronized void addHour(String pkg) {
        SQLiteDatabase d = getWritableDatabase();
        String t = today();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        d.execSQL("INSERT OR IGNORE INTO hours(date,hour) VALUES(?,?)",
                new Object[]{t, h});
        String c = col(pkg, "reels").replace("_reels", "");
        d.execSQL("UPDATE hours SET " + c + "=" + c + "+1 WHERE date=? AND hour=?",
                new Object[]{t, h});
        d.close();
    }

    /** Today's hourly reels: [3][24] rows = ig, fb, tt. */
    public synchronized long[][] getHoursToday() {
        return getHours(today());
    }

    /** Hourly reels for any date (yyyy-MM-dd): [3][24] = ig, fb, tt. */
    public synchronized long[][] getHours(String date) {
        long[][] out = new long[3][24];
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.rawQuery("SELECT hour,ig,fb,tt FROM hours WHERE date=?",
                new String[]{date});
        while (c.moveToNext()) {
            int h = c.getInt(0);
            if (h >= 0 && h < 24) {
                out[0][h] = c.getLong(1);
                out[1][h] = c.getLong(2);
                out[2][h] = c.getLong(3);
            }
        }
        c.close(); d.close();
        return out;
    }

    /**
     * N days ending {@code endAgo} days ago (0 = today), oldest first.
     * .date holds the short weekday label for charts.
     */
    public synchronized List<Day> getDaysRange(int endAgo, int n) {
        ArrayList<Day> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat lbl = new SimpleDateFormat("EEE", Locale.US);
        for (int i = n - 1; i >= 0; i--) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -(endAgo + i));
            String date = f.format(cal.getTime());
            Day d = getDay(date);
            d.date = lbl.format(cal.getTime()); // label for chart
            out.add(d);
        }
        return out;
    }

    /** Sum of a day-range (inclusive dates) into one Day, for period breakdowns. */
    public synchronized Day getRangeSum(String startDate, String endDate) {
        Day day = new Day();
        day.date = startDate;
        SQLiteDatabase d = getReadableDatabase();
        Cursor c = d.rawQuery("SELECT SUM(ig_reels),SUM(fb_reels),SUM(tt_reels)," +
                "SUM(ig_secs),SUM(fb_secs),SUM(tt_secs)," +
                "SUM(ig_rsecs),SUM(fb_rsecs),SUM(tt_rsecs)" +
                " FROM days WHERE date>=? AND date<=?",
                new String[]{startDate, endDate});
        if (c.moveToFirst()) {
            day.igReels = c.getInt(0); day.fbReels = c.getInt(1); day.ttReels = c.getInt(2);
            day.igSecs = c.getInt(3); day.fbSecs = c.getInt(4); day.ttSecs = c.getInt(5);
            day.igRsecs = c.getInt(6); day.fbRsecs = c.getInt(7); day.ttRsecs = c.getInt(8);
        }
        c.close(); d.close();
        return day;
    }
}
