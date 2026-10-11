package com.hatch.reeltracker;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;

/** First-run onboarding (Figma 01). Skipped on later launches. */
public class OnboardingActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs prefs = new Prefs(this);
        if (prefs.isOnboardingDone()) {
            goMain();
            return;
        }
        setContentView(R.layout.activity_onboarding);

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = getColor(R.color.ob_bg);
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        getWindow().setStatusBarColor(bg);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (night) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);

        View.OnClickListener done = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setOnboardingDone();
                goMain();
            }
        };
        findViewById(R.id.ob_skip).setOnClickListener(done);
        findViewById(R.id.ob_start).setOnClickListener(done);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
