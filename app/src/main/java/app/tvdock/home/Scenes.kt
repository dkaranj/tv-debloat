package app.tvdock.home

import android.graphics.*
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Procedurally drawn nature scenes: zero assets, rendered once off-thread. */
object Scenes {
    const val COUNT = 6
    val names = listOf("Alpine sunrise", "Misty pines", "Ocean sunset", "Aurora night", "Desert dunes", "Mountain lake")

    fun render(index: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (index % COUNT) {
            0 -> alpine(c, w, h)
            1 -> pines(c, w, h)
            2 -> ocean(c, w, h)
            3 -> aurora(c, w, h)
            4 -> dunes(c, w, h)
            else -> lake(c, w, h)
        }
        return bmp
    }

    private fun sky(c: Canvas, w: Int, h: Int, vararg colors: Int) {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }

    /** Ridge line: sum of sines, optionally sharpened into peaks. */
    private fun ridge(w: Int, base: Float, amp: Float, seed: Int, sharp: Boolean): Path {
        val r = Random(seed.toLong())
        val f = floatArrayOf(1.5f + r.nextFloat(), 3.5f + r.nextFloat() * 2, 8f + r.nextFloat() * 4)
        val ph = floatArrayOf(r.nextFloat() * 6.28f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f)
        val path = Path()
        path.moveTo(0f, 10000f)
        var x = 0
        while (x <= w) {
            val t = x.toFloat() / w * 2f * PI.toFloat()
            var v = 0.55f * sin(f[0] * t + ph[0]) + 0.3f * sin(f[1] * t + ph[1]) + 0.15f * sin(f[2] * t + ph[2])
            if (sharp) v = 1f - 2f * abs(v) * 0.9f
            path.lineTo(x.toFloat(), base - amp * v)
            x += 4
        }
        path.lineTo(w.toFloat(), 10000f)
        path.close()
        return path
    }

    private fun fill(c: Canvas, path: Path, top: Int, bottom: Int, y0: Float, y1: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, y0, 0f, y1, top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
    }

    private fun pineRow(c: Canvas, w: Int, baseY: Float, size: Float, color: Int, seed: Int) {
        val r = Random(seed.toLong())
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = color
        var x = -size
        while (x < w + size) {
            val s = size * (0.7f + r.nextFloat() * 0.6f)
            val y = baseY + r.nextFloat() * size * 0.3f
            val path = Path()
            for (i in 0..3) { // stacked tiers
                val ty = y - s * (0.28f * i); val tw = s * 0.45f * (1f - i * 0.2f)
                path.moveTo(x, ty - s * 0.5f); path.lineTo(x - tw, ty); path.lineTo(x + tw, ty); path.close()
            }
            c.drawPath(path, p)
            x += size * (0.35f + r.nextFloat() * 0.3f)
        }
    }

    private fun fog(c: Canvas, w: Int, y: Float, hgt: Float, alpha: Int) {
        val p = Paint()
        p.shader = LinearGradient(0f, y - hgt, 0f, y + hgt, intArrayOf(0x00FFFFFF, Color.argb(alpha, 235, 240, 245), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, y - hgt, w.toFloat(), y + hgt, p)
    }

    private fun alpine(c: Canvas, w: Int, h: Int) {
        sky(c, w, h, 0xFF22305A.toInt(), 0xFF8E5A8C.toInt(), 0xFFF4A261.toInt(), 0xFFFFE3A8.toInt())
        glow(c, w * 0.68f, h * 0.55f, h * 0.55f, 0xCCFFE9B0.toInt())
        val cols = listOf(0xFF6D5A8A, 0xFF4F4A78, 0xFF38395F, 0xFF22264A)
        for (i in cols.indices) {
            val base = h * (0.55f + i * 0.1f)
            fill(c, ridge(w, base, h * (0.22f - i * 0.03f), 11 + i * 7, true), cols[i].toInt(), (cols[i] + 0x101010).toInt() or 0xFF000000.toInt(), base - h * 0.2f, h.toFloat())
            fog(c, w, base + h * 0.02f, h * 0.06f, 70)
        }
    }

    private fun pines(c: Canvas, w: Int, h: Int) {
        sky(c, w, h, 0xFF9FB8B0.toInt(), 0xFFD5E2DA.toInt(), 0xFFEFF3EA.toInt())
        glow(c, w * 0.3f, h * 0.3f, h * 0.5f, 0x88FFFFFF.toInt())
        val cols = intArrayOf(0xFF8AA79D.toInt(), 0xFF5F8277.toInt(), 0xFF3E6258.toInt(), 0xFF1F3B35.toInt())
        for (i in cols.indices) {
            val base = h * (0.5f + i * 0.14f)
            fill(c, ridge(w, base, h * 0.08f, 3 + i * 5, false), cols[i], cols[i], base, h.toFloat())
            pineRow(c, w, base, h * (0.07f + i * 0.04f), cols[i], 40 + i)
            fog(c, w, base + h * 0.02f, h * 0.07f, 110 - i * 20)
        }
    }

    private fun ocean(c: Canvas, w: Int, h: Int) {
        val hor = h * 0.58f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, hor, intArrayOf(0xFF2B2D6E.toInt(), 0xFFB5476F.toInt(), 0xFFFF8F4D.toInt(), 0xFFFFD27A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), hor, p)
        glow(c, w * 0.5f, hor, h * 0.5f, 0xAAFFE0A0.toInt())
        val sun = Paint(Paint.ANTI_ALIAS_FLAG); sun.color = 0xFFFFF3D0.toInt()
        c.save(); c.clipRect(0f, 0f, w.toFloat(), hor); c.drawCircle(w * 0.5f, hor - h * 0.02f, h * 0.09f, sun); c.restore()
        val sea = Paint(); sea.shader = LinearGradient(0f, hor, 0f, h.toFloat(), 0xFFE58B5A.toInt(), 0xFF1B2552.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hor, w.toFloat(), h.toFloat(), sea)
        val r = Random(5); val line = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = hor + 4f
        while (y < h) {
            val k = (y - hor) / (h - hor); val len = w * (0.12f + 0.25f * k) * (0.4f + r.nextFloat())
            val x = w * 0.5f + (r.nextFloat() - 0.5f) * w * 0.12f * (1 + 3 * k)
            line.color = Color.argb((200 * (1 - k)).toInt(), 255, 236, 190); line.strokeWidth = 2f + 4f * k
            c.drawLine(x - len / 2, y, x + len / 2, y, line)
            y += 5f + 14f * k
        }
        fill(c, ridge(w, hor, h * 0.03f, 99, false), 0xFF2B2447.toInt(), 0xFF2B2447.toInt(), hor - 20, hor + 5) // far headland
    }

    private fun aurora(c: Canvas, w: Int, h: Int) {
        sky(c, w, h, 0xFF050A1C.toInt(), 0xFF0B1C3A.toInt(), 0xFF14304A.toInt())
        val r = Random(8); val st = Paint(); st.color = Color.WHITE
        for (i in 0 until 260) { st.alpha = 80 + r.nextInt(175); c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.65f, 0.6f + r.nextFloat() * 1.4f, st) }
        val band = Paint(Paint.ANTI_ALIAS_FLAG); band.style = Paint.Style.STROKE; band.maskFilter = BlurMaskFilter(h * 0.04f, BlurMaskFilter.Blur.NORMAL)
        val colors = intArrayOf(0xFF2BFF9A.toInt(), 0xFF1FD6C0.toInt(), 0xFF7A5CFF.toInt())
        for (k in 0..2) {
            band.color = colors[k]; band.alpha = 140; band.strokeWidth = h * (0.10f - k * 0.02f)
            val path = Path(); var x = -20
            while (x <= w + 20) { val y = h * (0.28f + k * 0.07f) + sin(x / w.toFloat() * 7f + k * 1.7f) * h * 0.07f; if (x == -20) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y); x += 12 }
            c.drawPath(path, band)
        }
        fill(c, ridge(w, h * 0.82f, h * 0.18f, 21, true), 0xFF0A1424.toInt(), 0xFF02050C.toInt(), h * 0.6f, h.toFloat())
        pineRow(c, w, h * 0.95f, h * 0.12f, 0xFF02050C.toInt(), 77)
    }

    private fun dunes(c: Canvas, w: Int, h: Int) {
        sky(c, w, h, 0xFF4B7FB5.toInt(), 0xFFF2C79B.toInt(), 0xFFFBE7C2.toInt())
        glow(c, w * 0.8f, h * 0.25f, h * 0.35f, 0xCCFFF5D8.toInt())
        val cols = intArrayOf(0xFFE2A56B.toInt(), 0xFFC98550.toInt(), 0xFFA9653D.toInt(), 0xFF7E4630.toInt())
        for (i in cols.indices) {
            val base = h * (0.55f + i * 0.13f)
            fill(c, ridge(w, base, h * 0.1f, 60 + i * 9, false), cols[i], cols[i], base, h.toFloat())
        }
    }

    private fun lake(c: Canvas, w: Int, h: Int) {
        val hor = h * 0.56f
        val skyColors = intArrayOf(0xFF3E7CC4.toInt(), 0xFF9CC9EE.toInt(), 0xFFEAF4FA.toInt())
        val p = Paint(); p.shader = LinearGradient(0f, 0f, 0f, hor, skyColors, null, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w.toFloat(), hor, p)
        val mt = ridge(w, hor, h * 0.3f, 31, true)
        fill(c, mt, 0xFF8EA6BE.toInt(), 0xFF3D5873.toInt(), hor - h * 0.3f, hor)
        val near = ridge(w, hor, h * 0.14f, 44, false); fill(c, near, 0xFF2F5547.toInt(), 0xFF1B3A30.toInt(), hor - h * 0.14f, hor)
        val water = Paint(); water.shader = LinearGradient(0f, hor, 0f, h.toFloat(), 0xFF7FB2D8.toInt(), 0xFF17374F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hor, w.toFloat(), h.toFloat(), water)
        c.save(); c.scale(1f, -0.8f, 0f, hor); val m = Paint(); m.alpha = 90
        val layer = Paint(Paint.ANTI_ALIAS_FLAG); layer.color = 0xFF1B3A30.toInt(); layer.alpha = 110; c.drawPath(near, layer)
        layer.color = 0xFF3D5873.toInt(); layer.alpha = 70; c.drawPath(mt, layer); c.restore()
        val r = Random(2); val rp = Paint(); rp.strokeWidth = 2f
        var y = hor + 6f
        while (y < h) { val k = (y - hor) / (h - hor); rp.color = Color.argb((60 * (1 - k)).toInt(), 255, 255, 255); val x = r.nextFloat() * w; c.drawLine(x, y, x + w * (0.05f + 0.1f * k), y, rp); y += 6f + 10f * k }
        c.drawRect(0f, hor - 1, w.toFloat(), hor + 1, Paint().apply { color = 0x33FFFFFF })
    }
}
