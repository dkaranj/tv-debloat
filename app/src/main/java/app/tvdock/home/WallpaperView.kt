package app.tvdock.home

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/** Cross-fading wallpaper rotator. Holds at most two bitmaps. */
class WallpaperView(ctx: Context) : View(ctx) {
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val photoDir = File(ctx.getExternalFilesDir(null), "wallpapers")
    private var cur: Bitmap? = null
    private var next: Bitmap? = null
    private var fade = 1f
    private var seq = 0
    private var running = false
    private var order = emptyList<Any>() // Int = built-in scene, File = photo
    var intervalMs = 45_000L

    private val tick = Runnable { advance() }

    fun start() {
        if (running) return
        running = true
        if (cur == null) advance(first = true) else ui.postDelayed(tick, intervalMs)
    }

    fun stop() { running = false; ui.removeCallbacks(tick) }

    private fun buildOrder(): List<Any> {
        val items = ArrayList<Any>()
        for (i in 0 until Scenes.COUNT) items.add(i)
        photoDir.listFiles()?.filter { it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }?.forEach { items.add(it) }
        items.shuffle()
        return items
    }

    private fun advance(first: Boolean = false) {
        if (!running) return
        if (order.isEmpty() || seq >= order.size) { order = buildOrder(); seq = 0 }
        val item = order[seq++]
        val w = max(width, 960); val h = max(height, 540)
        worker.execute {
            val bmp = try {
                when (item) {
                    is Int -> Scenes.render(item, 960, 540)
                    is File -> decode(item)
                    else -> null
                }
            } catch (t: Throwable) { null }
            ui.post {
                if (bmp != null && running) {
                    next = bmp
                    if (first) { cur = bmp; next = null; fade = 1f; invalidate() } else crossfade()
                }
                if (running) { ui.removeCallbacks(tick); ui.postDelayed(tick, intervalMs) }
            }
        }
    }

    private fun decode(f: File): Bitmap? {
        val o = BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.path, o)
        var s = 1; while (o.outWidth / (s * 2) >= 1280) s *= 2
        val d = BitmapFactory.Options(); d.inSampleSize = s; d.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(f.path, d)
    }

    private fun crossfade() {
        fade = 0f
        val a = android.animation.ValueAnimator.ofFloat(0f, 1f); a.duration = 1500
        a.addUpdateListener { fade = it.animatedValue as Float; invalidate() }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: android.animation.Animator) { cur?.takeIf { it !== next }?.recycle(); cur = next; next = null; fade = 1f; invalidate() }
        })
        a.start()
    }

    private fun draw(c: Canvas, b: Bitmap, alpha: Int) {
        paint.alpha = alpha
        val vw = width.toFloat(); val vh = height.toFloat()
        val scale = max(vw / b.width, vh / b.height)
        val sw = vw / scale; val sh = vh / scale
        val l = (b.width - sw) / 2; val t = (b.height - sh) / 2
        c.drawBitmap(b, Rect(l.toInt(), t.toInt(), (l + sw).toInt(), (t + sh).toInt()), RectF(0f, 0f, vw, vh), paint)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val a = cur; val b = next
        if (a != null) draw(c, a, 255)
        if (b != null) draw(c, b, (fade * 255).toInt())
        // soft scrim at the bottom so the dock stays readable
        val g = Paint(); g.shader = LinearGradient(0f, height * 0.6f, 0f, height.toFloat(), 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, height * 0.6f, width.toFloat(), height.toFloat(), g)
    }

    override fun onDetachedFromWindow() { stop(); worker.shutdown(); super.onDetachedFromWindow() }
}
