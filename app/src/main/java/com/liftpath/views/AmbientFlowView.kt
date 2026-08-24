package com.liftpath.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.core.graphics.ColorUtils
import com.liftpath.R
import com.liftpath.helpers.lpColor

/**
 * The app's ambient background: a slowly drifting flow field, drawn on the GPU.
 *
 * Replaces `lp_bg_ambient_flow.xml` — three sine waves sliding sideways on a linear loop at
 * 2.5–4% alpha. Two problems with that, both fixed here. A linear translate reads as static
 * because nothing about it accelerates, and an `<animated-vector>` re-tessellates its paths
 * on the CPU every frame for a picture that never actually changes shape. This draws one
 * full-screen quad and lets a fragment shader do the work, so the field genuinely evolves —
 * it never repeats — for less CPU than the thing it replaces.
 *
 * `minSdk` is 35, so [RuntimeShader] (API 33) needs no fallback path. That is the whole
 * reason this is now possible; when the waves were written it would have needed one.
 *
 * **Colour comes from the token layer.** The accent is read through [lpColor] at construction
 * exactly like every other themed surface, so all eight palettes work and a palette switch is
 * picked up by the activity recreate. There is no `@color/` anywhere near this file.
 *
 * Self-starting: it animates whenever it is attached and its window is visible, and stops
 * otherwise. That is deliberate rather than convenient — the previous drawable needed a
 * `setupBackgroundAnimation()` call in each hosting activity, and of the 23 layouts that
 * carried it, two (`activity_health_connect`, `activity_training_detail`) had no such call and
 * so were quietly showing a frozen background. A screen cannot forget to start this one.
 */
class AmbientFlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Null if the AGSL failed to compile.
     *
     * [RuntimeShader]'s constructor throws on a shader error, and this view is inflated by 23
     * layouts — so an uncaught throw here is not a loud failure, it is every screen in the app
     * dead on arrival. Degrading to [fallbackPaint] keeps the same spirit as the magenta
     * unbound-token colour: the mistake is impossible to miss (the field is visibly flat, and
     * the compile log is in logcat) without being impossible to recover from.
     */
    private val shader: RuntimeShader? = try {
        RuntimeShader(SHADER_SOURCE)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Ambient flow shader failed to compile — falling back to a flat wash", e)
        null
    }

    private val paint = Paint()

    /** Used when there is no shader, or the canvas is not hardware accelerated — see [onDraw]. */
    private val fallbackPaint = Paint()

    /**
     * False when the system animator scale is 0 ("Remove animations" / developer options).
     * The field then draws one static frame rather than nothing, so the composition is
     * unchanged and only the motion goes away.
     */
    private val animated: Boolean

    private var running = false
    private var lastFrameNanos = 0L

    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }

    init {
        val accent = context.lpColor(R.attr.lpAccent)
        // Day/night, resolved the same way the palettes resolve theirs — through a
        // values-night override rather than a branch in here. See lp_floats.xml.
        val intensity = resources.getFloat(R.dimen.lp_ambient_intensity)

        shader?.setColorUniform(UNIFORM_ACCENT, accent)
        shader?.setFloatUniform(UNIFORM_INTENSITY, intensity)

        // Roughly the field's mean alpha, so a software render lands in the same key as a
        // hardware one rather than reading as a different design.
        fallbackPaint.color = ColorUtils.setAlphaComponent(accent, (255f * intensity * 0.45f).toInt())

        animated = shader != null && animatorScale(context) > 0f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader?.setFloatUniform(UNIFORM_RESOLUTION, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        // RuntimeShader requires the GPU pipeline; on a software canvas it draws nothing at
        // all. That path is real — screenshot and thumbnail capture use one — so fill with
        // the field's average tone instead of leaving a hole.
        if (shader == null || !canvas.isHardwareAccelerated) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fallbackPaint)
            return
        }

        shader.setFloatUniform(UNIFORM_TIME, if (animated) elapsedSeconds() else 0f)
        // Re-assigning after mutating a uniform is what invalidates the cached native
        // shader. Skipping it draws the previous frame's time value forever.
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    /**
     * The one hook that accounts for window visibility as well as view visibility, so a
     * backgrounded activity stops shading without every host having to forward `onPause`.
     */
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) start() else stop()
    }

    private fun start() {
        if (running || !animated || !isAttachedToWindow) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /**
     * Redraws at [TARGET_FPS] rather than on every vsync. The field drifts at ~0.03 units a
     * second, so a 120Hz display would spend eight frames out of nine re-shading a picture
     * that has not visibly moved — and a full-screen fragment shader is the one thing on
     * this screen worth pacing.
     */
    private fun onFrame(frameTimeNanos: Long) {
        if (!running) return
        if (frameTimeNanos - lastFrameNanos >= FRAME_INTERVAL_NANOS) {
            lastFrameNanos = frameTimeNanos
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private companion object {

        private const val TAG = "AmbientFlowView"

        /**
         * Process-wide clock origin. Every screen shares it, so the field is one continuous
         * thing the user moves *over* rather than something that restarts behind each
         * activity. Wrapped at [LOOP_SECONDS] to keep the shader's float coordinates in a
         * precise range; the noise is not periodic, so that wrap is a discontinuity — an
         * hour of uninterrupted foreground time buys one barely-perceptible shift at 7%
         * alpha, which is the right side of that trade.
         */
        private val ORIGIN_NANOS = SystemClock.elapsedRealtimeNanos()
        private const val LOOP_SECONDS = 3600L

        private const val TARGET_FPS = 30L
        private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / TARGET_FPS

        private const val UNIFORM_RESOLUTION = "uResolution"
        private const val UNIFORM_TIME = "uTime"
        private const val UNIFORM_ACCENT = "uAccent"
        private const val UNIFORM_INTENSITY = "uIntensity"

        private fun elapsedSeconds(): Float {
            val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - ORIGIN_NANOS) / 1_000_000L
            return (elapsedMillis % (LOOP_SECONDS * 1000L)) / 1000f
        }

        private fun animatorScale(context: Context): Float =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )

        /**
         * AGSL. `layout(color)` is what makes `setColorUniform` legal and gets the accent
         * converted into the shader's working colour space instead of being pushed through
         * as raw sRGB components.
         *
         * The output is PREMULTIPLIED, which is the convention Skia runtime effects return.
         * It also means this view composites over whatever surface hosts it rather than
         * owning the canvas fill — the same relationship the tinted `ImageView` had, so the
         * 26 layouts that host it need no rethinking.
         */
        private val SHADER_SOURCE = """
            uniform float2 uResolution;
            uniform float  uTime;
            uniform float  uIntensity;
            layout(color) uniform half4 uAccent;

            // Cheap 2D value hash. Not a good hash in any general sense — it only has to be
            // decorrelated enough that the interpolated lattice below stops looking like a
            // lattice, and at this alpha that bar is low.
            float hash(float2 p) {
                p = fract(p * float2(127.31, 311.7));
                p += dot(p, p + 34.53);
                return fract(p.x * p.y);
            }

            float valueNoise(float2 p) {
                float2 cell = floor(p);
                float2 f = fract(p);
                float2 w = f * f * (3.0 - 2.0 * f);   // smoothstep, inlined
                float a = hash(cell);
                float b = hash(cell + float2(1.0, 0.0));
                float c = hash(cell + float2(0.0, 1.0));
                float d = hash(cell + float2(1.0, 1.0));
                return mix(mix(a, b, w.x), mix(c, d, w.x), w.y);
            }

            // Three octaves, not the usual five or six. Octaves four and up contribute
            // detail below one alpha step of 7%, so they cost fragments and change nothing.
            float fbm(float2 p) {
                float sum = 0.0;
                float amp = 0.5;
                for (int i = 0; i < 3; i++) {
                    sum += amp * valueNoise(p);
                    p = p * 2.07 + float2(19.0, 7.0);   // offset breaks octave alignment
                    amp *= 0.5;
                }
                return sum / 0.875;                     // 0.5+0.25+0.125 back to 0..1
            }

            half4 main(float2 fragCoord) {
                // Normalised by HEIGHT on both axes, so feature size is a fraction of the
                // screen's short-to-long dimension and the field does not stretch on a
                // tall phone the way the centerCrop'd 400x800dp vector did.
                float2 uv = fragCoord / uResolution.y;

                // ANISOTROPIC. Compressing x and stretching y means a feature is wide and
                // shallow rather than round, so the field is built from long drifting veils
                // instead of blobs. This is the single biggest reason it reads as flow: a
                // round soft-edged patch of colour looks like a stain no matter how well it
                // moves, because nothing about its shape has a direction.
                float2 p = float2(uv.x * 0.85, uv.y * 2.6);

                float t = uTime * 0.05;

                // Domain warp, drifting consistently rather than wobbling in place. Two
                // lookups displace the ribbon phase below; this is what bends the veils into
                // something organic instead of a stripe pattern.
                float2 w = float2(
                    fbm(p + float2(0.0, t)),
                    fbm(p + float2(3.7, 1.9) - float2(t * 0.6, 0.0))
                );

                // Ribbons from a WARPED SINE, not from thresholded noise.
                //
                // The previous version windowed fbm with smoothstep(0.34, 0.68), and that is
                // what made the field look unfinished: a hard clip leaves dead flat regions
                // joined by soft round edges, which is the visual signature of a stain. sin()
                // has no flat zone and no edge — it is continuously varying everywhere, so
                // every part of the field is always in motion and always has gradient. The
                // warp is what stops it reading as stripes.
                float phase = (p.y + w.y * 2.4 + w.x * 0.8) * 5.5 - t * 2.2;
                float ribbon = 0.5 + 0.5 * sin(phase);

                // Bias the ribbons dark-to-light rather than symmetric: the bright half of a
                // sine is as wide as the dark half, which covers too much. Raising it to a
                // power narrows the veils and widens the clean canvas between them without
                // reintroducing a hard edge.
                ribbon = pow(ribbon, 1.7);

                // Broad envelope, so the veils gather and disperse across the screen instead
                // of running edge to edge at constant strength. Smoothstep is safe here —
                // this modulates an already-smooth field rather than creating the shape.
                float envelope = smoothstep(0.30, 0.80, fbm(p * 0.45 + float2(t * 0.5, t * 0.2)));

                float n = ribbon * mix(0.35, 1.0, envelope);

                // Bottom-weighted: the top of every screen carries a title and the field must
                // not compete. A floor, not a fade to nothing.
                n *= mix(0.55, 1.0, uv.y);

                half alpha = half(n * uIntensity);
                return half4(uAccent.rgb * alpha, alpha);
            }
        """
    }
}
