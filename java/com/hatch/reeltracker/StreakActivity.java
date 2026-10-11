package com.hatch.reeltracker;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Streak milestone celebration — Figma 05. */
public class StreakActivity extends Activity {

    private Prefs prefs;
    private StatsDb db;
    private StreakManager sm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        int bg = getColor(R.color.td_bg);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean dark = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int flags = w.getDecorView().getSystemUiVisibility();
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        setContentView(R.layout.activity_streak);

        prefs = new Prefs(this);
        db = new StatsDb(this);
        sm = new StreakManager(this);

        int milestone = getIntent().getIntExtra("milestone", 7);

        ((TextView) findViewById(R.id.tv_streak_pill))
                .setText(milestone + "-day streak");
        ((TextView) findViewById(R.id.tv_headline)).setText(headlineFor(milestone));
        ((TextView) findViewById(R.id.tv_sub)).setText(
                "You stayed within your goal for " + milestone
                        + " days. That's real progress, one day at a time.");

        ((TextView) findViewById(R.id.tv_week_range)).setText(weekRange());
        buildWeekRow();

        ((TextView) findViewById(R.id.tv_reclaimed_big))
                .setText(fmtShortMins((int) (sm.reclaimedThisWeekSecs() / 60)));

        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btn_keep).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private String headlineFor(int m) {
        if (m >= 100) return "100 days. Legendary.";
        if (m >= 30) return "A whole month. Unstoppable.";
        return "A whole week. All you.";
    }

    private String weekRange() {
        SimpleDateFormat f = new SimpleDateFormat("MMM d", Locale.US);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -6);
        String a = f.format(c.getTime());
        c.add(Calendar.DAY_OF_YEAR, 6);
        String b = f.format(c.getTime());
        // "Oct 5–11"
        int sp = b.indexOf(' ');
        return a + "–" + (sp > 0 ? b.substring(sp + 1) : b);
    }

    private void buildWeekRow() {
        LinearLayout row = findViewById(R.id.week_row);
        row.removeAllViews();
        SimpleDateFormat dayF = new SimpleDateFormat("EEE", Locale.US);
        int checkGreen = getColor(R.color.day_clean_check);
        int checkGray = getColor(R.color.day_broken_check);
        for (int k = 6; k >= 0; k--) {
            String date = StatsDb.dateDaysAgo(k);
            boolean isToday = (k == 0);
            boolean clean = sm.isClean(db.getDay(date));

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            item.setLayoutParams(ip);

            FrameLayout circle = new FrameLayout(this);
            int cs = dp(44);
            circle.setLayoutParams(new LinearLayout.LayoutParams(cs, cs));
            if (isToday) {
                circle.setBackgroundResource(R.drawable.day_today);
            } else if (clean) {
                circle.setBackgroundResource(R.drawable.day_clean);
            } else {
                circle.setBackgroundResource(R.drawable.day_broken);
            }
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            int chk = isToday ? 0xFFFFFFFF : (clean ? checkGreen : checkGray);
            check.setColorFilter(chk, PorterDuff.Mode.SRC_IN);
            int chs = dp(20);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(chs, chs, Gravity.CENTER);
            check.setLayoutParams(clp);
            circle.addView(check);
            item.addView(circle);

            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -k);
            TextView letter = new TextView(this);
            letter.setText(dayF.format(c.getTime()).substring(0, 1));
            letter.setTextSize(12);
            letter.setTextColor(getColor(R.color.td_sub));
            letter.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(6);
            letter.setLayoutParams(llp);
            item.addView(letter);

            row.addView(item);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String fmtShortMins(int mins) {
        if (mins < 60) return mins + "m";
        int h = mins / 60, m = mins % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
