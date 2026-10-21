package io.github.thewombraider.pipestv;

import android.content.Context;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

final class PipesGLView extends GLSurfaceView {
    PipesGLView(Context context) {
        this(context, -1f, 1f, null);
    }

    /** {@code teapotChance} below zero keeps the default; a test run can raise it to see the teapot. */
    PipesGLView(Context context, float teapotChance, float timeScale, android.os.Bundle tuning) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new MultisampleChooser());
        setRenderer(new PipesRenderer(context, teapotChance, timeScale, tuning));
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    /** Asks for 4x multisampling so pipe edges are smooth, and falls back to a plain config without it. */
    private static final class MultisampleChooser implements EGLConfigChooser {
        private static final int EGL_RENDERABLE_TYPE = 0x3040;
        private static final int EGL_OPENGL_ES2_BIT = 4;
        private static final int EGL_SAMPLE_BUFFERS = 0x3032;
        private static final int EGL_SAMPLES = 0x3031;

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            EGLConfig config = choose(egl, display, true);
            return config != null ? config : choose(egl, display, false);
        }

        private EGLConfig choose(EGL10 egl, EGLDisplay display, boolean multisample) {
            int[] attribs = multisample
                    ? new int[] {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_DEPTH_SIZE, 16, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                            EGL_SAMPLE_BUFFERS, 1, EGL_SAMPLES, 4, EGL10.EGL_NONE}
                    : new int[] {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                            EGL10.EGL_DEPTH_SIZE, 16, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE};
            int[] count = new int[1];
            if (!egl.eglChooseConfig(display, attribs, null, 0, count) || count[0] <= 0) return null;
            EGLConfig[] configs = new EGLConfig[count[0]];
            if (!egl.eglChooseConfig(display, attribs, configs, count[0], count)) return null;
            return configs[0];
        }
    }
}
