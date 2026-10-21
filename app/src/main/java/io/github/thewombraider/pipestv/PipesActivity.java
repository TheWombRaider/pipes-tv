package io.github.thewombraider.pipestv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** The same scene in an ordinary full-screen window: a preview, and a way to run it without waiting for idle. */
public class PipesActivity extends Activity {
    private PipesGLView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new PipesGLView(this, getIntent().getFloatExtra("teapot", -1f), getIntent().getFloatExtra("timescale", 1f), getIntent().getExtras());
        view.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.onResume();
    }

    @Override
    protected void onPause() {
        view.onPause();
        super.onPause();
    }
}
