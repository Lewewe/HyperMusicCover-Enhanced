package com.os4.musiccover;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * The artwork's colour regions, drawn over the cover backdrop in the wallpaper process.
 *
 * Here and not in SystemUI because the notifications' glass samples this window and not
 * SystemUI's views: drawn there, the regions showed between the notifications and never in them
 * (measured 2026-11-01: a row's pixels did not move by one level as the regions did).
 *
 * The switches and the colours come from CoverGlow; a track's new colours ease in over the old.
 */
final class WallpaperGlow {
    private WallpaperGlow() {}

    /** Floats a region takes in the spec: centre x, y, radius, alpha, red, green, blue. */
    static final int STRIDE = 7;
    static final int REGIONS = 3;
    /** How far a region wanders from the centre, as a share of the screen's width and height. */
    private static final float WANDER = 0.45f;
    /** Time constant of the fade in and out, and of a new track's colours, in seconds. */
    private static final float TAU = 0.2f;
    /** Past this without a drawn frame the GL thread has stopped: the screen is off. */
    private static final long STALL_MS = 2000L;
    /**
     * Shortest gap between drawn frames: 30 a second, every fourth refresh of a 120Hz panel. Every
     * frame also makes the glass over it blur again (SystemUI measured 58% at 60, 31-46% at 30).
     */
    private static final long FRAME_NS = 32_000_000L;
    /** Off every location the OEM's programs use, whose arrays are left as they are. */
    private static final int ATTR = 7;

    private static final String VERTEX =
            "attribute vec2 aPos;\n"
            + "void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    private static final String FRAGMENT =
            "precision highp float;\n"
            + "uniform float uHeight, uLevel, uEased;\n"
            + "uniform vec2 uCentre[3];\n"
            + "uniform vec4 uAxes[3];\n"
            + "uniform float uRadius[3];\n"
            + "uniform vec4 uColour[3];\n"
            + "void main() {\n"
            + "  vec2 p = vec2(gl_FragCoord.x, uHeight - gl_FragCoord.y);\n"
            + "  vec3 c = vec3(0.0);\n"
            + "  float a = 0.0;\n"
            + "  for (int i = 0; i < 3; i++) {\n"
            + "    vec2 d = p - uCentre[i];\n"
            + "    d = vec2(dot(d, uAxes[i].xy), dot(d, uAxes[i].zw));\n"
            + "    float q = 1.0 - clamp(length(d) / uRadius[i], 0.0, 1.0);\n"
            + "    q = mix(q, q * q * (3.0 - 2.0 * q), uEased);\n"
            + "    float s = uColour[i].a * q;\n"
            + "    c = c * (1.0 - s) + uColour[i].rgb * s;\n"
            + "    a = a + s - a * s;\n"
            + "  }\n"
            // Noise of under one level: slow gradients this wide band without it.
            + "  float n = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));\n"
            + "  gl_FragColor = vec4((c + (n - 0.5) * a / 255.0) * uLevel, a * uLevel);\n"
            + "}\n";

    // Main thread.
    private static boolean sOn, sDrift, sLooping;
    private static float[] sTarget;
    private static long sLastTick, sLastRequest;

    // Read by the GL thread.
    private static volatile float sLevel;
    private static volatile boolean sDrawDrift;
    private static volatile float[] sSpec;
    private static volatile long sLastDraw;

    // GL thread.
    private static volatile boolean sBroken;
    private static EGLContext sCtx;
    private static int sProgram, uHeight, uLevel, uEased, uCentre, uAxes, uRadius, uColour;
    private static final float[] sCentres = new float[2 * REGIONS];
    private static final float[] sAxesV = new float[4 * REGIONS];
    private static final float[] sRadii = new float[REGIONS];
    private static final float[] sColours = new float[4 * REGIONS];
    private static final int[] sInt = new int[1];
    private static final int[] sBlend = new int[4];
    private static final int[] sViewport = new int[4];
    private static final FloatBuffer QUAD = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(new float[]{-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f});

    /** From CoverGlow's message. A null or short spec keeps the colours there are. */
    static void set(final boolean on, final boolean drift, final float[] spec) {
        new Handler(Looper.getMainLooper()).post(() -> {
            sOn = on;
            sDrift = drift;
            sDrawDrift = drift;
            if (spec != null && spec.length == REGIONS * STRIDE) {
                sTarget = spec;
                if (sSpec == null || sLevel <= 0f) sSpec = spec.clone();
            }
            startLoop();
        });
    }

    private static void startLoop() {
        if (sLooping || sTarget == null || sBroken) return;
        sLooping = true;
        sLastTick = 0L;
        sLastDraw = SystemClock.uptimeMillis();
        Choreographer.getInstance().postFrameCallback(WallpaperGlow::tick);
    }

    private static void tick(long nowNs) {
        sLooping = false;
        if (sBroken) return;
        float dt = sLastTick == 0L ? 0f : Math.min(0.1f, (nowNs - sLastTick) / 1e9f);
        sLastTick = nowNs;
        float k = dt <= 0f ? 0f : (float) (1.0 - Math.exp(-dt / TAU));

        float want = sOn ? 1f : 0f;
        float level = sLevel + (want - sLevel) * k;
        if (Math.abs(want - level) < 0.004f) level = want;
        sLevel = level;

        boolean moving = false;
        float[] have = sSpec, target = sTarget;
        if (have != null && target != null && have != target) {
            float[] next = new float[have.length];
            for (int i = 0; i < have.length; i++) {
                next[i] = have[i] + (target[i] - have[i]) * k;
                if (Math.abs(target[i] - next[i]) < 0.002f) next[i] = target[i];
                moving |= next[i] != target[i];
            }
            sSpec = moving ? next : target;
        }

        boolean more = level != want || moving || (sDrift && level > 0f);
        // Always on the last step, so that step is what gets drawn.
        if (!more || nowNs - sLastRequest >= FRAME_NS) {
            sLastRequest = nowNs;
            WallpaperProbe.requestFrame(true);
        }
        if (!more) return;
        if (SystemClock.uptimeMillis() - sLastDraw > STALL_MS) {
            // Nothing asked for has been drawn. CoverGlow's own message ends or restarts this.
            if (want <= 0f) sLevel = 0f;
            return;
        }
        sLooping = true;
        Choreographer.getInstance().postFrameCallback(WallpaperGlow::tick);
    }

    /** On the GL thread, after the OEM has drawn the picture. Never fatal to the wallpaper. */
    static void draw() {
        float level = sLevel;
        float[] spec = sSpec;
        if (level <= 0f || spec == null || sBroken) return;
        sLastDraw = SystemClock.uptimeMillis();
        try {
            drawNow(level, spec);
        } catch (Throwable t) {
            sBroken = true;
            Xp.w("MCWallpaperGlow: drawing failed, off from now on: " + t);
        }
    }

    private static void drawNow(float level, float[] spec) {
        EGLContext ctx = EGL14.eglGetCurrentContext();
        if (!ctx.equals(sCtx)) {
            // A program goes with its context, and the engine rebuilds the context at will.
            sProgram = build();
            sCtx = ctx;
        }

        int[] one = sInt;
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, one, 0);
        int oldProgram = one[0];
        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, one, 0);
        int oldBuffer = one[0];
        boolean blend = GLES20.glIsEnabled(GLES20.GL_BLEND);
        GLES20.glGetIntegerv(GLES20.GL_BLEND_SRC_RGB, one, 0);
        sBlend[0] = one[0];
        GLES20.glGetIntegerv(GLES20.GL_BLEND_DST_RGB, one, 0);
        sBlend[1] = one[0];
        GLES20.glGetIntegerv(GLES20.GL_BLEND_SRC_ALPHA, one, 0);
        sBlend[2] = one[0];
        GLES20.glGetIntegerv(GLES20.GL_BLEND_DST_ALPHA, one, 0);
        sBlend[3] = one[0];
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, sViewport, 0);

        pose(spec, sViewport[2], sViewport[3], sDrawDrift);

        GLES20.glUseProgram(sProgram);
        GLES20.glUniform1f(uHeight, sViewport[3]);
        GLES20.glUniform1f(uLevel, level);
        GLES20.glUniform1f(uEased, sDrawDrift ? 1f : 0f);
        GLES20.glUniform2fv(uCentre, REGIONS, sCentres, 0);
        GLES20.glUniform4fv(uAxes, REGIONS, sAxesV, 0);
        GLES20.glUniform1fv(uRadius, REGIONS, sRadii, 0);
        GLES20.glUniform4fv(uColour, REGIONS, sColours, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glEnableVertexAttribArray(ATTR);
        QUAD.position(0);
        GLES20.glVertexAttribPointer(ATTR, 2, GLES20.GL_FLOAT, false, 0, QUAD);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_ZERO, GLES20.GL_ONE);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(ATTR);
        GLES20.glBlendFuncSeparate(sBlend[0], sBlend[1], sBlend[2], sBlend[3]);
        if (!blend) GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, oldBuffer);
        GLES20.glUseProgram(oldProgram);
    }

    /** Where the regions are this frame: the spec's own places, or roaming. */
    private static void pose(float[] spec, int w, int h, boolean drift) {
        float side = Math.max(w, h);
        double t = SystemClock.uptimeMillis() / 1000.0;
        for (int i = 0; i < REGIONS; i++) {
            int s = i * STRIDE;
            float cx = spec[s] * w, cy = spec[s + 1] * h;
            double cos = 1.0, sin = 0.0;
            float sx = 1f, sy = 1f;
            if (drift) {
                // Two out-of-step waves per axis (periods 23-61s) never repeat a path: each region
                // roams the whole screen slowly. The phases hold across a track change.
                double ax = 0.9 + i * 2.1, ay = 2.3 + i * 1.3;
                cx = w * (0.5f + WANDER * (0.6f * wave(t, 41.0 + 11.0 * i, ax) + 0.4f * wave(t, 23.0 + 5.0 * i, ay)));
                cy = h * (0.5f + WANDER * (0.6f * wave(t, 47.0 + 7.0 * i, ay) + 0.4f * wave(t, 29.0 + 9.0 * i, ax)));
                // Stretched and slowly turning, so it reads as a wash of colour, not a circle.
                double angle = 2.0 * Math.PI * ((t / (53.0 + 17.0 * i)) % 1.0) + i * 1.05;
                cos = Math.cos(angle);
                sin = Math.sin(angle);
                sx = 1.5f;
                sy = 0.75f;
            }
            sCentres[2 * i] = cx;
            sCentres[2 * i + 1] = cy;
            sAxesV[4 * i] = (float) (cos / sx);
            sAxesV[4 * i + 1] = (float) (sin / sx);
            sAxesV[4 * i + 2] = (float) (-sin / sy);
            sAxesV[4 * i + 3] = (float) (cos / sy);
            sRadii[i] = Math.max(1f, spec[s + 2] * side);
            sColours[4 * i] = spec[s + 4];
            sColours[4 * i + 1] = spec[s + 5];
            sColours[4 * i + 2] = spec[s + 6];
            sColours[4 * i + 3] = spec[s + 3];
        }
    }

    private static float wave(double t, double period, double phase) {
        return (float) Math.sin(2.0 * Math.PI * ((t / period) % 1.0) + phase);
    }

    private static int build() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glBindAttribLocation(p, ATTR, "aPos");
        GLES20.glLinkProgram(p);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, sInt, 0);
        if (sInt[0] == 0) throw new IllegalStateException("link: " + GLES20.glGetProgramInfoLog(p));
        uHeight = GLES20.glGetUniformLocation(p, "uHeight");
        uLevel = GLES20.glGetUniformLocation(p, "uLevel");
        uEased = GLES20.glGetUniformLocation(p, "uEased");
        uCentre = GLES20.glGetUniformLocation(p, "uCentre");
        uAxes = GLES20.glGetUniformLocation(p, "uAxes");
        uRadius = GLES20.glGetUniformLocation(p, "uRadius");
        uColour = GLES20.glGetUniformLocation(p, "uColour");
        return p;
    }

    private static int compile(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, sInt, 0);
        if (sInt[0] == 0) throw new IllegalStateException("compile: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }
}
