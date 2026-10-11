package com.hatch.reeltracker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** Shows what the accessibility service actually sees — for tuning detection. */
public class DiagnosticsActivity extends Activity {

    private TextView tvLog;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_diagnostics);
        tvLog = findViewById(R.id.tv_log);

        Button btnCopy = findViewById(R.id.btn_copy);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("reel-diag", tvLog.getText()));
                Toast.makeText(DiagnosticsActivity.this, "Log copied", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnClear = findViewById(R.id.btn_clear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                synchronized (ReelAccessibilityService.eventLog) {
                    ReelAccessibilityService.eventLog.clear();
                }
                refresh();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("Service started: ");
        if (ReelAccessibilityService.serviceStartedAt == 0) {
            sb.append("NOT RUNNING (enable tracking!)\n");
        } else {
            sb.append(DateFormat.format("HH:mm:ss", ReelAccessibilityService.serviceStartedAt)).append("\n");
        }
        sb.append("Events seen: ").append(ReelAccessibilityService.eventsSeen).append("\n");
        sb.append("Scroll events: ").append(ReelAccessibilityService.scrollEventsSeen).append("\n");
        sb.append("Reels counted: ").append(ReelAccessibilityService.reelsCounted).append("\n");
        sb.append("Last reel: ").append(ReelAccessibilityService.lastReelInfo).append("\n");
        sb.append("Reels mode: IG=")
                .append(ReelAccessibilityService.reelsModeState.get(ReelAccessibilityService.IG))
                .append(" FB=")
                .append(ReelAccessibilityService.reelsModeState.get(ReelAccessibilityService.FB))
                .append(" TT=")
                .append(ReelAccessibilityService.reelsModeState.get(ReelAccessibilityService.TT1))
                .append("\n");
        sb.append("----------------\n");
        synchronized (ReelAccessibilityService.eventLog) {
            for (String s : ReelAccessibilityService.eventLog) sb.append(s).append("\n");
        }
        tvLog.setText(sb.toString());
    }
}
