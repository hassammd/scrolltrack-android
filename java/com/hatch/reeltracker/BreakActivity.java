package com.hatch.reeltracker;

import android.app.Activity;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

/** Minimal break timer (temporary native screen until Figma designs it). */
public class BreakActivity extends Activity {

    private CountDownTimer timer;
    private TextView tvTimer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_break);
        tvTimer = findViewById(R.id.tv_break_timer);

        int mins = getIntent().getIntExtra("mins", 10);
        long total = mins * 60L * 1000L;

        timer = new CountDownTimer(total, 1000) {
            @Override
            public void onTick(long left) {
                long s = left / 1000;
                tvTimer.setText(String.format("%02d:%02d", s / 60, s % 60));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("Done 🌱");
            }
        };
        timer.start();

        findViewById(R.id.btn_end_break).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }
}
