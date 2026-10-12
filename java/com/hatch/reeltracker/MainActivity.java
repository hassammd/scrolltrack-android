package com.hatch.reeltracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** ScrollTrack v2.0: Home / Analytics / History / Goals tabs. */
public class MainActivity extends Activity {

    private static final int C_PRIMARY = 0xFF0E3A45;
    private static final int C_SUB = 0xFF777B8B;
    private static final int C_OK = 0xFF24A47F;
    private static final int C_BAD = 0xFFE45C68;
    private static final int C_IG = 0xFFE1306C;
    private static final int C_FB = 0xFF1877F2;
    private static final int C_TT = 0xFF111111;

    private StatsDb db;
    private Prefs prefs;

    private View tabHome, tabAnalytics, tabHistory, tabGoals, tabYou;
    private View scrollMain;
    private ImageView navTodayIcon, navInsightsIcon, navLimitsIcon, navYouIcon;
    private TextView navTodayLabel, navInsightsLabel, navLimitsLabel, navYouLabel;
    private int currentTab = 0;

    // Today (redesign)
    private TextView tvGreeting, tvDateLine, tvBalancePill, tvRingBig, tvRingSub,
            tvHomeReels, tvReelsSub, tvHomeReclaimed, tvReclaimedSub,
            tvAppIgSub, tvAppFbSub, tvAppTtSub, tvBannerSub;
    private GoalRingView ringHome;
    private LinearLayout dateStrip;
    private ProgressBar barIg, barFb, barTt;
    private View bannerStreak;
    private TextView tvBannerTitle;

    // Insights (Figma 03)
    private TextView segDay, segWeek, segMonth, tvAnRange, tvAnTotal, tvAnPill,
            tvAnCaption, tvOtherApps, tvTotals;
    private View legendRow;
    private LinearLayout rowsContainer;
    private BarsView chartAnalytics;
    private int anMode = 1; // Week selected, like the Figma
    private int anOffset = 0; // 0 = current period; negative = past periods
    private TextView tvBreakdownRange;

    // History
    private TextView tvHistAvg, tvHistDelta, tvHistIg, tvHistFb, tvHistTt;
    private BarsView chartHistory;

    // Limits (Figma 04)
    private TextView tvGoalValue;
    private LinearLayout appRows;
    private TextView chip5, chip15, chip30;
    private Button btnStartBreak;
    private int breakMins = 15;
    private int goalMins = 60;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        db = new StatsDb(this);
        prefs = new Prefs(this);

        tabHome = findViewById(R.id.tab_home);
        tabAnalytics = findViewById(R.id.tab_analytics);
        tabHistory = findViewById(R.id.tab_history);
        tabGoals = findViewById(R.id.tab_goals);
        tabYou = findViewById(R.id.tab_you);
        scrollMain = findViewById(R.id.scroll_main);

        bindNav();
        bindHome();
        bindAnalytics();
        bindHistory();
        bindGoals();
        bindYou();
        showTab(0);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        goalMins = prefs.getLimitMinutes();
        int milestone = new StreakManager(this).rollForward();
        refreshAll();
        if (milestone > 0) {
            Intent i = new Intent(this, StreakActivity.class);
            i.putExtra("milestone", milestone);
            startActivity(i);
        }
    }

    // ---------------- navigation ----------------

    private void bindNav() {
        navTodayIcon = findViewById(R.id.nav_today_icon);
        navInsightsIcon = findViewById(R.id.nav_insights_icon);
        navLimitsIcon = findViewById(R.id.nav_limits_icon);
        navYouIcon = findViewById(R.id.nav_you_icon);
        navTodayLabel = findViewById(R.id.nav_today_label);
        navInsightsLabel = findViewById(R.id.nav_insights_label);
        navLimitsLabel = findViewById(R.id.nav_limits_label);
        navYouLabel = findViewById(R.id.nav_you_label);
        findViewById(R.id.nav_today).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(0); }
        });
        findViewById(R.id.nav_insights).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        });
        findViewById(R.id.nav_limits).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(2); }
        });
        findViewById(R.id.nav_you).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(3); }
        });
    }

    private void showTab(int t) {
        currentTab = t;
        tabHome.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        tabAnalytics.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        tabHistory.setVisibility(View.GONE); // legacy screen, not in the new nav
        tabGoals.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        tabYou.setVisibility(t == 3 ? View.VISIBLE : View.GONE);
        tintNav(navTodayIcon, navTodayLabel, t == 0);
        tintNav(navInsightsIcon, navInsightsLabel, t == 1);
        tintNav(navLimitsIcon, navLimitsLabel, t == 2);
        tintNav(navYouIcon, navYouLabel, t == 3);
        // All tabs share the Figma spacing: no extra ScrollView padding.
        scrollMain.setBackgroundColor(getColor(R.color.td_bg));
        scrollMain.setPadding(0, 0, 0, 0);
        paintStatusBar();
    }

    private void tintNav(ImageView icon, TextView label, boolean active) {
        int c = active ? getColor(R.color.td_primary) : getColor(R.color.td_nav_inactive);
        icon.setColorFilter(c, PorterDuff.Mode.SRC_IN);
        label.setTextColor(c);
        label.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void paintStatusBar() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = getColor(R.color.td_bg);
        getWindow().setStatusBarColor(bg);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (night) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    // ---------------- home (redesign) ----------------

    private void bindHome() {
        tvGreeting = findViewById(R.id.tv_greeting);
        tvDateLine = findViewById(R.id.tv_date_line);
        tvBalancePill = findViewById(R.id.tv_balance_pill);
        ringHome = findViewById(R.id.ring_home);
        tvRingBig = findViewById(R.id.tv_ring_big);
        tvRingSub = findViewById(R.id.tv_ring_sub);
        tvHomeReels = findViewById(R.id.tv_home_reels);
        tvReelsSub = findViewById(R.id.tv_reels_sub);
        tvHomeReclaimed = findViewById(R.id.tv_home_reclaimed);
        tvReclaimedSub = findViewById(R.id.tv_reclaimed_sub);
        tvAppIgSub = findViewById(R.id.tv_app_ig_sub);
        tvAppFbSub = findViewById(R.id.tv_app_fb_sub);
        tvAppTtSub = findViewById(R.id.tv_app_tt_sub);
        barIg = findViewById(R.id.bar_app_ig);
        barFb = findViewById(R.id.bar_app_fb);
        barTt = findViewById(R.id.bar_app_tt);
        dateStrip = findViewById(R.id.date_strip);
        bannerStreak = findViewById(R.id.banner_streak);
        tvBannerTitle = findViewById(R.id.tv_banner_title);
        tvBannerSub = findViewById(R.id.tv_banner_sub);

        buildDateStrip();

        findViewById(R.id.tv_edit_goal).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editGlobalGoal(); }
        });
        findViewById(R.id.btn_bell).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DiagnosticsActivity.class));
            }
        });
        View.OnClickListener goInsights = new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(1); }
        };
        findViewById(R.id.row_app_ig).setOnClickListener(goInsights);
        findViewById(R.id.row_app_fb).setOnClickListener(goInsights);
        findViewById(R.id.row_app_tt).setOnClickListener(goInsights);
        bannerStreak.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(3); } // You tab has the live streak card
        });
        findViewById(R.id.btn_break).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTab(2); } // break controls live on Limits
        });
    }

    /** Date strip: today + next 6 days, like the Figma. Tapping jumps to Insights. */
    private void buildDateStrip() {
        dateStrip.removeAllViews();
        final float dp = getResources().getDisplayMetrics().density;
        Calendar base = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("d", Locale.US);
        int pill = (int) (56 * dp + 0.5f);
        // Force perfect circles: re-measure as squares once the strip is laid out
        dateStrip.post(new Runnable() {
            @Override public void run() {
                int total = dateStrip.getWidth();
                if (total <= 0) return;
                float d = getResources().getDisplayMetrics().density;
                int gap = (int) (9 * d + 0.5f);
                int size = (total - gap * 6) / 7;
                for (int i = 0; i < dateStrip.getChildCount(); i++) {
                    View ch = dateStrip.getChildAt(i);
                    if (ch instanceof FrameLayout) {
                        LinearLayout.LayoutParams lp =
                                (LinearLayout.LayoutParams) ch.getLayoutParams();
                        lp.width = size;
                        lp.height = size;
                        ch.setLayoutParams(lp);
                    }
                }
            }
        });
        for (int k = 0; k < 7; k++) {
            final int daysFrom = k;
            Calendar day = (Calendar) base.clone();
            day.add(Calendar.DAY_OF_YEAR, k);
            FrameLayout f = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, pill, 1f);
            f.setLayoutParams(lp);
            boolean today = k == 0;
            f.setBackgroundResource(today ? R.drawable.date_pill_on : R.drawable.date_pill_off);
            if (!today && Build.VERSION.SDK_INT >= 21) f.setElevation(3 * dp);
            TextView t = new TextView(this);
            t.setText(df.format(day.getTime()));
            t.setTextSize(17);
            t.setTypeface(null, Typeface.BOLD);
            t.setTextColor(today ? 0xFFFFFFFF : getColor(R.color.td_text));
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            tp.gravity = Gravity.CENTER;
            f.addView(t, tp);
            f.setClickable(true);
            f.setFocusable(true);
            f.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { jumpToInsightsDay(daysFrom); }
            });
            dateStrip.addView(f);
            if (k < 6) {
                Space sp = new Space(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams((int) (9 * dp + 0.5f), 1));
                dateStrip.addView(sp);
            }
        }
    }

    /** Date strip pill -> Insights Day view for the tapped date (0 = today). */
    private void jumpToInsightsDay(int daysFromToday) {
        anMode = 0;
        anOffset = daysFromToday; // positive = future (anEndAgo() negates)
        showTab(1);
        refreshAnalytics();
    }

    private void refreshHome() {
        // Time-aware greeting
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = hour < 12 ? "Good morning"
                : (hour < 17 ? "Good afternoon" : "Good evening");
        tvGreeting.setText(g + " \uD83D\uDC4B");
        String dateStr = new SimpleDateFormat("EEEE, MMM d", Locale.US).format(new Date());
        tvDateLine.setText(dateStr + " \u00B7 You\u2019ve got this.");

        StatsDb.Day d = db.getDay(StatsDb.today());
        int goal = Math.max(1, goalMins);
        int usedSecs = d.totalSecs();
        int usedMins = usedSecs / 60;
        int leftMins = Math.max(0, goal - usedMins);

        // Daily balance ring: used fraction of the daily goal
        ringHome.setProgress(usedSecs / (float) (goal * 60));
        tvRingBig.setText(fmtShortMins(leftMins));
        tvRingSub.setText("left of your\n" + fmtShortMins(goal) + " goal");

        boolean over = usedMins > goalMins;
        tvBalancePill.setText(over ? "Over limit" : "On track");
        tvBalancePill.setBackgroundResource(over ? R.drawable.pill_danger : R.drawable.pill_track);
        tvBalancePill.setTextColor(getColor(over ? R.color.danger : R.color.td_pill_text));

        // Stat cards
        tvHomeReels.setText(String.valueOf(d.totalReels()));
        tvReelsSub.setText(fmtShortMins(usedMins) + " of your day");

        StatsDb.Day lastWeek = db.getDayOffset(7);
        int reclaimedSecs = Math.max(0, lastWeek.totalSecs() - usedSecs);
        tvHomeReclaimed.setText(fmtShortMins(reclaimedSecs / 60));
        String wd = new SimpleDateFormat("EEEE", Locale.US).format(
                new Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000));
        tvReclaimedSub.setText("vs. last " + wd);

        // Your apps rows
        int denom = Math.max(1, usedSecs);
        bindAppRow(tvAppIgSub, barIg, d.igSecs, d.igReels, denom);
        bindAppRow(tvAppFbSub, barFb, d.fbSecs, d.fbReels, denom);
        bindAppRow(tvAppTtSub, barTt, d.ttSecs, d.ttReels, denom);

        // Streak banner: live once the streak system runs
        int streak = prefs.getStreak();
        if (streak >= 1) {
            bannerStreak.setVisibility(View.VISIBLE);
            tvBannerTitle.setText(streak + "-day streak \u00B7 Look at you!");
            tvBannerSub.setText("Small steps, kinder routine.");
        } else {
            bannerStreak.setVisibility(View.GONE);
        }
    }

    private void bindAppRow(TextView sub, ProgressBar bar, int secs, int reels, int totalSecs) {
        sub.setText(fmtHm(secs / 60) + " \u00B7 " + reels + " reels");
        bar.setMax(1000);
        bar.setProgress((int) (1000L * secs / totalSecs));
    }

    /** "42m" / "1h 02m" / "2h" style (Figma apps rows). */
    private static String fmtHm(int mins) {
        if (mins < 60) return mins + "m";
        if (mins % 60 == 0) return (mins / 60) + "h";
        return (mins / 60) + "h " + String.format(Locale.US, "%02d", mins % 60) + "m";
    }

    /** "28m" / "1h 32m" / "2h" style. */
    private static String fmtShortMins(int mins) {
        if (mins < 60) return mins + "m";
        if (mins % 60 == 0) return (mins / 60) + "h";
        return (mins / 60) + "h " + (mins % 60) + "m";
    }

    // ---------------- you (redesign) ----------------

    private void bindYou() {
        View.OnClickListener goDiag = new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DiagnosticsActivity.class));
            }
        };
        findViewById(R.id.btn_you_diag).setOnClickListener(goDiag);
        findViewById(R.id.row_you_about).setOnClickListener(goDiag);
        refreshYou();
    }

    private void refreshYou() {
        int streak = prefs.getStreak();
        int longest = prefs.getStreakLongest();
        TextView tvStreak = findViewById(R.id.tv_you_streak);
        TextView tvStreakSub = findViewById(R.id.tv_you_streak_sub);
        if (streak >= 1) {
            tvStreak.setText(streak + "-day streak");
            tvStreak.setTextSize(30);
            tvStreakSub.setText("Longest: " + longest + " days \u00B7 "
                    + "A missed day never erases your progress.");
        } else {
            tvStreak.setText("Start your streak");
            tvStreak.setTextSize(24);
            tvStreakSub.setText("Every clean day grows your flame."
                    + (longest > 0 ? " Longest: " + longest + " days." : ""));
        }

        // Monthly balance summary: clean days in the last 30 days
        StreakManager sm = new StreakManager(this);
        int clean = 0;
        for (int k = 0; k < 30; k++) {
            if (sm.isClean(db.getDay(StatsDb.dateDaysAgo(k)))) clean++;
        }
        double ratio = clean / 30.0;
        String verdict = ratio >= 0.8 ? "Excellent"
                : ratio >= 0.5 ? "Good"
                : ratio >= 0.25 ? "Getting there" : "Fresh start";
        ((TextView) findViewById(R.id.tv_you_verdict)).setText(verdict);
        ((TextView) findViewById(R.id.tv_you_month_sub)).setText(
                "You stayed within your goal " + clean + " of 30 days.");

        // Reels cut + time reclaimed: last 30 days vs the previous 30
        StatsDb.Day cur = db.getRangeSum(StatsDb.dateDaysAgo(29), StatsDb.today());
        StatsDb.Day prev = db.getRangeSum(StatsDb.dateDaysAgo(59), StatsDb.dateDaysAgo(30));
        TextView tvCut = findViewById(R.id.tv_you_reels_cut);
        if (prev.totalReels() > 0) {
            int pct = (int) Math.round(
                    (prev.totalReels() - cur.totalReels()) * 100.0 / prev.totalReels());
            tvCut.setText((pct >= 0 ? "\u2193 " : "\u2191 ") + Math.abs(pct) + "%");
        } else {
            tvCut.setText("\u2014");
        }
        long reclaimedSecs = Math.max(0, prev.totalSecs() - cur.totalSecs());
        ((TextView) findViewById(R.id.tv_you_reclaimed))
                .setText(fmtShortMins((int) (reclaimedSecs / 60)));
        ((TextView) findViewById(R.id.tv_you_clean)).setText(String.valueOf(clean));

        try {
            String v = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.tv_you_version)).setText(
                    "Version " + v + " \u00B7 Made to beat scrolling addiction.");
        } catch (Exception e) {
            // keep fallback text
        }
    }

    // ---------------- analytics ----------------

    private void bindAnalytics() {
        segDay = findViewById(R.id.seg_day);
        segWeek = findViewById(R.id.seg_week);
        segMonth = findViewById(R.id.seg_month);
        tvAnRange = findViewById(R.id.tv_an_range);
        tvAnTotal = findViewById(R.id.tv_an_total);
        tvAnPill = findViewById(R.id.tv_an_pill);
        tvAnCaption = findViewById(R.id.tv_an_caption);
        tvOtherApps = findViewById(R.id.tv_other_apps);
        tvTotals = findViewById(R.id.tv_totals);
        legendRow = findViewById(R.id.legend_row);
        rowsContainer = findViewById(R.id.rows_container);
        chartAnalytics = findViewById(R.id.chart_analytics);
        tvBreakdownRange = findViewById(R.id.tv_breakdown_range);
        View.OnClickListener segClick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (v.getId() == R.id.seg_day) anMode = 0;
                else if (v.getId() == R.id.seg_week) anMode = 1;
                else anMode = 2;
                anOffset = 0;
                refreshAnalytics();
            }
        };
        segDay.setOnClickListener(segClick);
        segWeek.setOnClickListener(segClick);
        segMonth.setOnClickListener(segClick);
        findViewById(R.id.btn_an_prev).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                anOffset--;
                refreshAnalytics();
            }
        });
        findViewById(R.id.btn_an_next).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (anOffset < 0) {
                    anOffset++;
                    refreshAnalytics();
                }
            }
        });
        findViewById(R.id.btn_calendar).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAnDatePicker(); }
        });
    }

    /** Jump the chart to the period containing the picked date. */
    private void showAnDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dlg = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker v, int y, int m, int d) {
                        Calendar picked = Calendar.getInstance();
                        picked.set(y, m, d, 0, 0, 0);
                        picked.set(Calendar.MILLISECOND, 0);
                        Calendar today = Calendar.getInstance();
                        today.set(Calendar.HOUR_OF_DAY, 0);
                        today.set(Calendar.MINUTE, 0);
                        today.set(Calendar.SECOND, 0);
                        today.set(Calendar.MILLISECOND, 0);
                        long diffDays = (today.getTimeInMillis()
                                - picked.getTimeInMillis()) / 86400000L;
                        if (diffDays < 0) diffDays = 0;
                        int span = anMode == 0 ? 1 : (anMode == 1 ? 7 : 30);
                        anOffset = -(int) (diffDays / span);
                        refreshAnalytics();
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
        dlg.show();
    }

    /** Days-ago of the last day in the selected window (0 = today). */
    private int anEndAgo() {
        int span = anMode == 0 ? 1 : (anMode == 1 ? 7 : 30);
        return -anOffset * span;
    }

    private void paintSeg() {
        styleSeg(segDay, anMode == 0);
        styleSeg(segWeek, anMode == 1);
        styleSeg(segMonth, anMode == 2);
    }

    private void styleSeg(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.seg_selected_new : 0);
        tv.setTextColor(active ? 0xFFFFFFFF : getColor(R.color.td_sub));
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void refreshAnalytics() {
        paintSeg();
        if (anMode == 0) {
            refreshDayChart();
        } else {
            refreshRangeChart(anMode == 1 ? 7 : 30);
        }
        refreshBreakdown();
    }

    /** Day mode: reels for the selected day, hour by hour. */
    private void refreshDayChart() {
        int ago = anEndAgo();
        String date = StatsDb.dateDaysAgo(ago);
        long[][] hours = db.getHours(date);
        String[] labels = new String[24];
        long[][] segs = new long[24][1];
        for (int h = 0; h < 24; h++) {
            labels[h] = (h % 3 == 0) ? (h + ":00") : "";
            segs[h][0] = hours[0][h] + hours[1][h] + hours[2][h];
        }
        chartAnalytics.setYAxis(false);
        chartAnalytics.setData(labels, segs, new int[]{getColor(R.color.td_primary)}, -1, false, false);
        chartAnalytics.setMaxTooltip(true);
        chartAnalytics.setAvgLine(false);
        chartAnalytics.setLetterPills(false);
        legendRow.setVisibility(View.GONE);

        StatsDb.Day d = db.getDay(date);
        StatsDb.Day y = db.getDay(StatsDb.dateDaysAgo(ago + 1));
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -ago);
        SimpleDateFormat mf = new SimpleDateFormat("MMM d", Locale.US);
        String lbl;
        if (ago == 0) lbl = "Today · " + mf.format(c.getTime());
        else if (ago == 1) lbl = "Yesterday · " + mf.format(c.getTime());
        else lbl = new SimpleDateFormat("EEEE, MMM d", Locale.US).format(c.getTime());
        tvAnRange.setText(lbl);
        tvAnTotal.setText(d.totalReels() + " reels");
        setDeltaPill(d.totalReels(), y.totalReels());
        int diff = d.totalReels() - y.totalReels();
        if (diff == 0) {
            tvAnCaption.setText("Same as the previous day.");
        } else if (diff < 0) {
            tvAnCaption.setText((-diff) + " fewer reels than the previous day.");
        } else {
            tvAnCaption.setText(diff + " more reels than the previous day.");
        }
    }

    /** Week/Month: daily screen time stacked as reels-time + other time. */
    private void refreshRangeChart(int n) {
        int endAgo = anEndAgo();
        List<StatsDb.Day> days = db.getDaysRange(endAgo, n);
        String[] labels = new String[n];
        long[][] segs = new long[n][2];
        long sumSecs = 0;
        int otherColor = getColor(R.color.td_other_bar);
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat wdFmt = new SimpleDateFormat("EEE", Locale.US);
        for (int i = 0; i < n; i++) {
            StatsDb.Day dd = days.get(i);
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -(endAgo + (n - 1 - i)));
            if (n == 7) {
                labels[i] = wdFmt.format(cal.getTime()).substring(0, 1).toUpperCase(Locale.US);
            } else {
                labels[i] = (i % 5 == 0)
                        ? String.valueOf(cal.get(Calendar.DAY_OF_MONTH)) : "";
            }
            long rs = dd.reelsSecs();
            long other = Math.max(0, dd.totalSecs() - rs);
            segs[i][0] = rs;
            segs[i][1] = other;
            sumSecs += dd.totalSecs();
        }
        chartAnalytics.setYAxis(true);
        chartAnalytics.setData(labels, segs, new int[]{getColor(R.color.td_primary), otherColor},
                endAgo == 0 ? n - 1 : -1, false, false);
        chartAnalytics.setMaxTooltip(true);
        chartAnalytics.setAvgLine(true);
        chartAnalytics.setLetterPills(true);
        legendRow.setVisibility(View.VISIBLE);

        double avgSecs = sumSecs / (double) n;
        long prevSum = 0;
        for (StatsDb.Day pd : db.getDaysRange(endAgo + n, n)) prevSum += pd.totalSecs();
        double prevAvg = prevSum / (double) n;

        SimpleDateFormat mf = new SimpleDateFormat("MMM d", Locale.US);
        Calendar c0 = Calendar.getInstance();
        c0.add(Calendar.DAY_OF_YEAR, -(endAgo + n - 1));
        Calendar c1 = Calendar.getInstance();
        c1.add(Calendar.DAY_OF_YEAR, -endAgo);
        tvAnRange.setText("Daily average · " + mf.format(c0.getTime())
                + "–" + mf.format(c1.getTime()));
        tvAnTotal.setText(fmtShortMins((int) (avgSecs / 60)));
        setDeltaPill(avgSecs, prevAvg);

        long diffMins = Math.round((prevAvg - avgSecs) / 60);
        String period = (n == 7) ? "week" : "month";
        String ref = anOffset == 0 ? "last " + period : "previous " + period;
        if (diffMins == 0) {
            tvAnCaption.setText("About the same as " + ref + ".");
        } else if (diffMins > 0) {
            tvAnCaption.setText(diffMins + " minutes less each day than " + ref + ".");
        } else {
            tvAnCaption.setText((-diffMins) + " minutes more each day than " + ref + ".");
        }
    }

    /** Green "↓ 18%" / red "↑ 5%" pill vs a previous value. */
    private void setDeltaPill(double cur, double prev) {
        if (prev < 1) {
            tvAnPill.setText("—");
            tvAnPill.setBackgroundResource(R.drawable.pill_track);
            tvAnPill.setTextColor(getColor(R.color.td_sub));
            return;
        }
        double pct = (cur - prev) / prev * 100.0;
        boolean down = pct <= 0;
        tvAnPill.setText((down ? "↓ " : "↑ ") + Math.round(Math.abs(pct)) + "%");
        tvAnPill.setBackgroundResource(down ? R.drawable.pill_track : R.drawable.pill_danger);
        tvAnPill.setTextColor(getColor(down ? R.color.td_pill_text : R.color.danger));
    }

    /** App breakdown table — follows the selected chart period. */
    private void refreshBreakdown() {
        String startDate, endDate, rangeLbl;
        SimpleDateFormat mf = new SimpleDateFormat("MMM d", Locale.US);
        if (anMode == 0) {
            int ago = anEndAgo();
            startDate = endDate = StatsDb.dateDaysAgo(ago);
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -ago);
            rangeLbl = ago == 0 ? "Today" : ago == 1 ? "Yesterday"
                    : mf.format(c.getTime());
        } else {
            int n = anMode == 1 ? 7 : 30;
            int endAgo = anEndAgo();
            endDate = StatsDb.dateDaysAgo(endAgo);
            startDate = StatsDb.dateDaysAgo(endAgo + n - 1);
            Calendar c0 = Calendar.getInstance();
            c0.add(Calendar.DAY_OF_YEAR, -(endAgo + n - 1));
            Calendar c1 = Calendar.getInstance();
            c1.add(Calendar.DAY_OF_YEAR, -endAgo);
            rangeLbl = anMode == 1 ? "This week" : "This month";
        }
        tvBreakdownRange.setText(rangeLbl);
        StatsDb.Day d = db.getRangeSum(startDate, endDate);
        rowsContainer.removeAllViews();
        int[][] apps = {
                {d.igSecs, d.igReels, d.igRsecs, R.drawable.ic_ig},
                {d.ttSecs, d.ttReels, d.ttRsecs, R.drawable.ic_tt},
                {d.fbSecs, d.fbReels, d.fbRsecs, R.drawable.ic_fb},
        };
        String[] names = {"Instagram", "TikTok", "Facebook"};
        Integer[] order = {0, 1, 2};
        Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                return Integer.compare(apps[b][0], apps[a][0]);
            }
        });
        LayoutInflater inf = LayoutInflater.from(this);
        for (int idx : order) {
            View row = inf.inflate(R.layout.row_app_breakdown, rowsContainer, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(apps[idx][3]);
            ((TextView) row.findViewById(R.id.row_name)).setText(names[idx]);
            ((TextView) row.findViewById(R.id.row_time))
                    .setText(fmtShortMins(apps[idx][0] / 60));
            ((TextView) row.findViewById(R.id.row_reels))
                    .setText(apps[idx][1] + " reels");
            ((TextView) row.findViewById(R.id.row_reels_time))
                    .setText(fmtShortMins(apps[idx][2] / 60) + " reels");
            rowsContainer.addView(row);
            if (idx != order[order.length - 1]) {
                View div = new View(this);
                div.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                div.setBackgroundColor(getColor(R.color.td_track));
                LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) div.getLayoutParams();
                lp.leftMargin = (int) (60 * getResources().getDisplayMetrics().density + 0.5f);
                rowsContainer.addView(div);
            }
        }
        tvTotals.setText(d.totalReels() + " reels · "
                + fmtShortMins(d.reelsSecs() / 60) + " total");
        long otherApps = Math.max(0,
                deviceTotalSecs(dateStartMs(startDate), dateEndMs(endDate)) - d.totalSecs());
        tvOtherApps.setText("Other apps · " + fmtShortMins((int) (otherApps / 60)));
    }

    private long dateStartMs(String date) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .parse(date).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private long dateEndMs(String date) {
        return dateStartMs(date) + 86400000L - 1;
    }

    /** Whole-device foreground time in [startMs, endMs] (UsageStats). */
    private long deviceTotalSecs(long startMs, long endMs) {
        try {
            UsageStatsManager usm =
                    (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;
            Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(
                    startMs, Math.min(endMs, System.currentTimeMillis()));
            if (map == null) return 0;
            long t = 0;
            for (UsageStats s : map.values()) t += s.getTotalTimeInForeground();
            return t / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------- history ----------------

    private void bindHistory() {
        tvHistAvg = findViewById(R.id.tv_hist_avg);
        tvHistDelta = findViewById(R.id.tv_hist_delta);
        chartHistory = findViewById(R.id.chart_history);
        tvHistIg = findViewById(R.id.tv_hist_ig);
        tvHistFb = findViewById(R.id.tv_hist_fb);
        tvHistTt = findViewById(R.id.tv_hist_tt);
    }

    private void refreshHistory() {
        List<StatsDb.Day> week = db.last7();
        String[] labels = new String[7];
        long[][] segs = new long[7][3];
        long sum = 0, ig = 0, fb = 0, tt = 0;
        for (int i = 0; i < 7; i++) {
            StatsDb.Day d = week.get(i);
            labels[i] = d.date;
            segs[i][0] = d.igReels; segs[i][1] = d.fbReels; segs[i][2] = d.ttReels;
            sum += d.totalReels();
            ig += d.igReels; fb += d.fbReels; tt += d.ttReels;
        }
        chartHistory.setData(labels, segs, new int[]{C_IG, C_FB, C_TT}, -1, false, false);

        double avg = sum / 7.0;
        tvHistAvg.setText(trimNum(avg));

        long prevSum = 0;
        for (int k = 7; k < 14; k++) prevSum += db.getDayOffset(k).totalReels();
        double prevAvg = prevSum / 7.0;
        if (prevAvg < 0.5 && avg < 0.5) {
            tvHistDelta.setText("no data yet");
            tvHistDelta.setTextColor(C_SUB);
        } else if (prevAvg < 0.5) {
            tvHistDelta.setText("first week!");
            tvHistDelta.setTextColor(C_OK);
        } else {
            double pct = (avg - prevAvg) / prevAvg * 100.0;
            boolean down = pct <= 0;
            tvHistDelta.setText((down ? "↓ " : "↑ ") + trimNum(Math.abs(pct)) + "%");
            tvHistDelta.setTextColor(down ? C_OK : C_BAD);
        }

        tvHistIg.setText(ig + " reels");
        tvHistFb.setText(fb + " reels");
        tvHistTt.setText(tt + " reels");
    }

    private static String trimNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.05) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.1f", v);
    }

    // ---------------- goals ----------------

    private void bindGoals() {
        tvGoalValue = findViewById(R.id.tv_goal_value);
        appRows = findViewById(R.id.app_rows);
        chip5 = findViewById(R.id.chip_5);
        chip15 = findViewById(R.id.chip_15);
        chip30 = findViewById(R.id.chip_30);
        btnStartBreak = findViewById(R.id.btn_start_break);

        findViewById(R.id.card_goal).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editGlobalGoal(); }
        });
        findViewById(R.id.btn_add_limit).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this, "More apps coming soon",
                        Toast.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener chipClick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int id = v.getId();
                breakMins = (id == R.id.chip_5) ? 5 : (id == R.id.chip_30) ? 30 : 15;
                paintBreakChips();
            }
        };
        chip5.setOnClickListener(chipClick);
        chip15.setOnClickListener(chipClick);
        chip30.setOnClickListener(chipClick);

        btnStartBreak.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, BreakActivity.class);
                i.putExtra("mins", breakMins);
                startActivity(i);
            }
        });
    }

    private void paintBreakChips() {
        styleBreakChip(chip5, breakMins == 5);
        styleBreakChip(chip15, breakMins == 15);
        styleBreakChip(chip30, breakMins == 30);
        btnStartBreak.setText("Start a " + breakMins + "-minute break");
    }

    private void styleBreakChip(TextView tv, boolean active) {
        tv.setBackgroundResource(active ? R.drawable.chip_break_on : R.drawable.chip_break_off);
        tv.setTextColor(active ? 0xFFFFFFFF : getColor(R.color.td_sub));
        tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void refreshGoals() {
        goalMins = prefs.getLimitMinutes();
        tvGoalValue.setText(fmtShortMins(goalMins));
        paintBreakChips();
        buildAppRows();
    }

    private void buildAppRows() {
        appRows.removeAllViews();
        StatsDb.Day d = db.getDay(StatsDb.today());
        final String[] apps = {"ig", "tt", "fb"};
        final String[] names = {"Instagram", "TikTok", "Facebook"};
        int[] icons = {R.drawable.ic_ig, R.drawable.ic_tt, R.drawable.ic_fb};
        int[] bars = {R.drawable.bar_ig, R.drawable.bar_tt, R.drawable.bar_fb};
        int[] secs = {d.igSecs, d.ttSecs, d.fbSecs};
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < 3; i++) {
            final String app = apps[i];
            final String name = names[i];
            View row = inf.inflate(R.layout.row_app_limit, appRows, false);
            ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icons[i]);
            ((TextView) row.findViewById(R.id.row_name)).setText(name);
            int lim = prefs.getAppLimitMins(app);
            boolean on = prefs.isAppLimitOn(app);
            int usedMins = secs[i] / 60;
            TextView tvSub = row.findViewById(R.id.row_sub);
            ((TextView) row.findViewById(R.id.row_limit)).setText(fmtShortMins(lim));
            if (on) {
                int left = lim - usedMins;
                tvSub.setText(usedMins + "m used · "
                        + (left >= 0 ? left + "m left" : "over by " + (-left) + "m"));
            } else {
                tvSub.setText(usedMins + "m used · nudges off");
            }
            ProgressBar bar = row.findViewById(R.id.row_bar);
            bar.setProgressDrawable(getDrawable(bars[i]));
            bar.setMax(100);
            bar.setProgress(lim > 0 ? Math.min(100, usedMins * 100 / lim) : 0);
            Switch sw = row.findViewById(R.id.row_toggle);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(on);
            sw.setTrackTintList(getColorStateList(R.color.toggle_track));
            sw.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    prefs.setAppLimitOn(app, checked);
                    refreshGoals();
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { editAppLimit(app, name); }
            });
            appRows.addView(row);
            if (i < 2) {
                View div = new View(MainActivity.this);
                div.setBackgroundColor(getColor(R.color.td_nav_border));
                appRows.addView(div, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }
    }

    private void editGlobalGoal() {
        final String[] labels = {"30 min", "1 hour", "1.5 hours", "2 hours", "3 hours"};
        final int[] mins = {30, 60, 90, 120, 180};
        int cur = prefs.getLimitMinutes();
        int sel = 0;
        for (int i = 0; i < mins.length; i++) if (mins[i] == cur) sel = i;
        new AlertDialog.Builder(this)
                .setTitle("Daily screen time goal")
                .setSingleChoiceItems(labels, sel, null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int s = ((AlertDialog) d).getListView().getCheckedItemPosition();
                        prefs.setLimits(prefs.getLimitReels(), mins[Math.max(0, s)]);
                        refreshGoals();
                        refreshHome();
                        Toast.makeText(MainActivity.this, "Goal saved",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editAppLimit(final String app, String name) {
        final String[] labels = {"15 min", "30 min", "45 min", "1 hour", "1.5 hours", "2 hours"};
        final int[] mins = {15, 30, 45, 60, 90, 120};
        int cur = prefs.getAppLimitMins(app);
        int sel = 1;
        for (int i = 0; i < mins.length; i++) if (mins[i] == cur) sel = i;
        new AlertDialog.Builder(this)
                .setTitle(name + " daily limit")
                .setSingleChoiceItems(labels, sel, null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int s = ((AlertDialog) d).getListView().getCheckedItemPosition();
                        prefs.setAppLimitMins(app, mins[Math.max(0, s)]);
                        refreshGoals();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------- shared ----------------

    private void refreshAll() {
        refreshHome();
        refreshAnalytics();
        refreshHistory();
        refreshGoals();
        refreshYou();
    }

    private boolean isAccessibilityEnabled() {
        String expected = getPackageName() + "/" + ReelAccessibilityService.class.getName();
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private static String fmtMins(int secs) {
        int m = secs / 60;
        if (m < 60) return m + "m";
        return (m / 60) + "h " + (m % 60) + "m";
    }
}
