package app.tvdock.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class AppInfo(val pkg: String, val label: String, val icon: Drawable)

/** Minimal Apple-TV-style home: wallpaper, clock, dock. OK on the grid button opens all apps; long-press OK adds/removes dock apps. */
class MainActivity : Activity() {
    private lateinit var wall: WallpaperView
    private lateinit var dock: LinearLayout
    private lateinit var label: TextView
    private lateinit var overlay: FrameLayout
    private lateinit var grid: GridView
    private lateinit var dockCol: LinearLayout
    private var longHandled = false
    private var apps: List<AppInfo> = emptyList()
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("dock", MODE_PRIVATE) }
    private val d: Float by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = loadApps()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val root = FrameLayout(this)
        wall = WallpaperView(this)
        root.addView(wall, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val clock = TextClock(this).apply {
            format12Hour = "EEE d MMM   h:mm a"; format24Hour = "EEE d MMM   HH:mm"
            setTextColor(Color.WHITE); textSize = 24f; setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
        }
        root.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(28), dp(48), 0) })

        label = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, 0xCC000000.toInt())
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply { setColor(0x59000000); cornerRadius = dp(30).toFloat() }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        dockCol = col
        col.addView(label, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })
        col.addView(dock, LinearLayout.LayoutParams(-2, -2))
        root.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(40) })

        overlay = FrameLayout(this).apply { setBackgroundColor(0xEE0E1116.toInt()); visibility = View.GONE }
        grid = GridView(this).apply {
            numColumns = 6; verticalSpacing = dp(12); horizontalSpacing = dp(12); stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = ColorDrawable(0x33FFFFFF); setDrawSelectorOnTop(false); clipToPadding = false
            setPadding(dp(56), dp(56), dp(56), dp(56))
            onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, p, _ -> launch(apps[p].pkg) }
            onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, p, _ -> toggleFav(apps[p].pkg); true }
        }
        val hint = TextView(this).apply { text = "OK = open     long-press OK = add / remove from dock     Back = close"; setTextColor(0xAAFFFFFF.toInt()); textSize = 16f }
        overlay.addView(grid, FrameLayout.LayoutParams(-1, -1))
        overlay.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(16) })
        root.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)
        handleIntent(intent)
        loadApps()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        registerReceiver(pkgReceiver, f)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handleIntent(i); if (overlay.visibility == View.VISIBLE) setOverlay(false); buildDock() }

    /** `adb shell am start -n app.tvdock.home/.MainActivity --es favourites "pkg1,pkg2"` sets the dock. */
    private fun handleIntent(i: Intent?) {
        i?.getStringExtra("favourites")?.let { prefs.edit().putString("fav", it).apply() }
    }

    override fun onResume() { super.onResume(); wall.start(); dock.getChildAt(0)?.requestFocus() }
    override fun onPause() { wall.stop(); super.onPause() }
    override fun onDestroy() { try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}; io.shutdown(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (overlay.visibility == View.VISIBLE) setOverlay(false) /* on Home: do nothing */ }

    private fun loadApps() {
        io.execute {
            val pm = packageManager
            val seen = LinkedHashMap<String, AppInfo>()
            for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (r in pm.queryIntentActivities(q, 0)) {
                    val p = r.activityInfo.packageName
                    if (p == packageName || seen.containsKey(p)) continue
                    seen[p] = AppInfo(p, r.loadLabel(pm).toString(), r.loadIcon(pm))
                }
            }
            val list = seen.values.sortedBy { it.label.lowercase() }
            runOnUiThread { apps = list; if (!prefs.contains("fav")) prefs.edit().putString("fav", defaults().joinToString(",")).apply(); grid.adapter = Adapter(); buildDock() }
        }
    }

    private fun defaults(): List<String> {
        val want = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.spotify.tv.android",
            "com.showmax.tv", "com.google.android.youtube.tvmusic", "com.android.vending", "com.android.tv.settings", "com.android.settings")
        return want.filter { w -> apps.any { it.pkg == w } }.take(8)
    }

    private fun favs(): List<String> = (prefs.getString("fav", "") ?: "").split(",").filter { it.isNotBlank() }

    private fun toggleFav(pkg: String) {
        val f = favs().toMutableList()
        if (f.remove(pkg)) toast("Removed from dock") else { f.add(pkg); toast("Added to dock") }
        prefs.edit().putString("fav", f.joinToString(",")).apply(); buildDock(); (grid.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private fun buildDock() {
        dock.removeAllViews()
        val f = favs().mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
        for (a in f) dock.addView(tile(a.icon, a.label, { launch(a.pkg) }, { toggleFav(a.pkg) }))
        dock.addView(tile(GridIcon(), "All apps", { showAll() }, null))
        if (overlay.visibility != View.VISIBLE) dock.getChildAt(0)?.requestFocus()
    }

    private fun showAll() { setOverlay(true) }

    private fun setOverlay(show: Boolean) {
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        dockCol.descendantFocusability = if (show) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (show) grid.requestFocus() else dock.getChildAt(0)?.requestFocus()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (overlay.visibility == View.VISIBLE) {
            val ok = e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER
            if (ok && e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 1) {
                val p = grid.selectedItemPosition
                if (p >= 0 && p < apps.size) toggleFav(apps[p].pkg)
                longHandled = true; return true
            }
            if (ok && e.action == KeyEvent.ACTION_UP && longHandled) { longHandled = false; return true }
            if (e.keyCode == KeyEvent.KEYCODE_MENU && e.action == KeyEvent.ACTION_DOWN) {
                val p = grid.selectedItemPosition
                if (p >= 0 && p < apps.size) toggleFav(apps[p].pkg)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun tile(icon: Drawable, name: String, click: () -> Unit, long: (() -> Unit)?): View {
        val size = dp(84)
        val v = ImageView(this).apply {
            setImageDrawable(icon.constantState?.newDrawable() ?: icon); scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(8), dp(8), dp(8), dp(8))
            isFocusable = true; isClickable = true
            background = null
            setOnClickListener { click() }
            if (long != null) setOnLongClickListener { long(); true }
            setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.3f else 1f).scaleY(if (focused) 1.3f else 1f).setDuration(110).start()
                view.background = if (focused) GradientDrawable().apply { setColor(0x44FFFFFF); cornerRadius = dp(18).toFloat() } else null
                if (focused) label.text = name
            }
        }
        return v.also { it.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(10), 0, dp(10), 0) } }
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { toast("Can't open $pkg"); return }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { startActivity(i) } catch (e: Exception) { toast("Can't open: ${e.message}") }
        setOverlay(false)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
            val a = apps[p]
            val c = (cv as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(12), dp(8), dp(12))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(72), dp(72)))
                addView(TextView(context).apply { setTextColor(Color.WHITE); textSize = 15f; maxLines = 1; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END })
            }
            (c.getChildAt(0) as ImageView).setImageDrawable(a.icon.constantState?.newDrawable() ?: a.icon)
            (c.getChildAt(1) as TextView).text = (if (favs().contains(a.pkg)) "★ " else "") + a.label
            return c
        }
    }

    /** 3x3 rounded-square grid glyph for the "All apps" button. */
    private class GridIcon : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        override fun draw(c: Canvas) {
            val b = bounds; val s = b.width() * 0.22f; val gap = b.width() * 0.09f; val o = (b.width() - 3 * s - 2 * gap) / 2
            for (r in 0..2) for (k in 0..2) { val x = b.left + o + k * (s + gap); val y = b.top + o + r * (s + gap); c.drawRoundRect(RectF(x, y, x + s, y + s), s * 0.25f, s * 0.25f, p) }
        }
        override fun setAlpha(a: Int) { p.alpha = a }
        override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
