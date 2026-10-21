package io.github.thewombraider.pipestv;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.Choreographer;

/**
 * A GL surface that draws only when the picture changes. The original drew whole pipe segments 30 times a second
 * and nothing in between, so on a 60 Hz screen every other refresh would repeat the last frame; skipping those
 * halves the work, and between scenes the GPU does nothing at all.
 */
final class PipesGLView extends GLSurfaceView implements Choreographer.FrameCallback {
    private final PipesRenderer renderer;
    private boolean running;

    PipesGLView(Context context) {
        this(context, -1f, 1f, null);
    }

    /** {@code teapotChance} below zero keeps the default; a test run can raise it to see the teapot. */
    PipesGLView(Context context, float teapotChance, float timeScale, android.os.Bundle tuning) {
        super(context);
        setEGLContextClientVersion(2);
        // 8-bit color and a depth buffer, no multisampling: the original drew with plain OpenGL 1.1 and no smoothing.
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        renderer = new PipesRenderer(context, teapotChance, timeScale, tuning);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void onResume() {
        super.onResume();
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void onPause() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onPause();
    }

    /** Called on every refresh; asks for a frame when the renderer has something new to show. */
    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (System.nanoTime() >= renderer.nextFrameNanos()) requestRender();
        Choreographer.getInstance().postFrameCallback(this);
    }
}
