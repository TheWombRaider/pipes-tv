package io.github.thewombraider.pipestv;

import android.service.dreams.DreamService;

/** The screensaver entry point: a full-screen GL surface that any key press or touch dismisses. */
public class PipesDream extends DreamService {
    private PipesGLView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new PipesGLView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.onResume();
    }

    @Override
    public void onDreamingStopped() {
        if (view != null) view.onPause();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        view = null;
        super.onDetachedFromWindow();
    }
}
