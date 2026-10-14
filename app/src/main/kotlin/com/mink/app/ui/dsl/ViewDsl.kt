package com.mink.app.ui.dsl

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import com.mink.app.ui.theme.AppTheme
import com.mink.app.ui.theme.Icon
import com.mink.app.ui.theme.IconView
import com.mink.app.ui.theme.Theme

// ── Dimension helpers ──────────────────────────────────────────

fun Context.dp(n: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, n.toFloat(), resources.displayMetrics).toInt()

fun View.dp(n: Int): Int = context.dp(n)

fun View.padding(h: Int, v: Int) { val hp = dp(h); val vp = dp(v); setPadding(hp, vp, hp, vp) }
fun View.padding(all: Int) { val p = dp(all); setPadding(p, p, p, p) }

// ── Tap helpers ────────────────────────────────────────────────

fun View.onTap(action: () -> Unit) { setOnClickListener { action() } }

/** Tap with press-scale feedback (0.96 down, 1.0 up). */
fun View.onTapFeedback(action: () -> Unit) {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(OvershootInterpolator(2f)).start()
        }
        false
    }
    setOnClickListener { action() }
}

// ── Drawable helpers ───────────────────────────────────────────

fun Context.roundedDrawable(
    color: Int, radiusDp: Int = 12,
    strokeColor: Int = Color.TRANSPARENT, strokeWidthDp: Int = 0
): GradientDrawable = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radiusDp).toFloat()
    if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), strokeColor)
}

fun Context.gradientDrawable(
    colors: IntArray,
    orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TOP_BOTTOM,
    radiusDp: Int = 0
): GradientDrawable = GradientDrawable(orientation, colors).apply {
    if (radiusDp > 0) cornerRadius = dp(radiusDp).toFloat()
}

fun Context.rippleDrawable(
    contentColor: Int, radiusDp: Int = 12,
    rippleColor: Int = Theme.c.ripple,
    strokeColor: Int = Color.TRANSPARENT, strokeWidthDp: Int = 0
): RippleDrawable {
    val content = roundedDrawable(contentColor, radiusDp, strokeColor, strokeWidthDp)
    val mask = roundedDrawable(Color.WHITE, radiusDp)
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
}

// ── Layout builders ────────────────────────────────────────────

fun Context.vstack(block: LinearLayout.() -> Unit): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; block() }

fun Context.hstack(block: LinearLayout.() -> Unit): LinearLayout =
    LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; block() }

fun Context.frame(block: FrameLayout.() -> Unit): FrameLayout =
    FrameLayout(this).apply { block() }

fun Context.card(
    bgColor: Int = Theme.c.surface, radiusDp: Int = 16,
    strokeColor: Int = Theme.c.outline, strokeWidthDp: Int = 1,
    block: FrameLayout.() -> Unit
): FrameLayout = FrameLayout(this).apply {
    background = roundedDrawable(bgColor, radiusDp, strokeColor, strokeWidthDp)
    block()
}

fun Context.scrollColumn(block: LinearLayout.() -> Unit): ScrollView {
    val sv = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        )
    }
    val inner = vstack(block)
    sv.addView(inner)
    return sv
}

fun spacer(): View.() -> Unit = {
    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
}

// ── Type scale (2.3) ───────────────────────────────────────────
// Display  28sp semibold
// Title    20sp semibold
// Headline 17sp semibold
// Body     15sp normal
// Label    13sp medium
// Caption  12sp normal
// Overline 11sp medium uppercase

private val mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val boldTf   = Typeface.create("sans-serif-medium", Typeface.BOLD)
private val normalTf = Typeface.create("sans-serif", Typeface.NORMAL)

fun Context.displayText(value: String): TextView = TextView(this).apply {
    text = value; setTextColor(Theme.c.text); textSize = 28f; typeface = boldTf
}

fun Context.titleText(value: String): TextView = TextView(this).apply {
    text = value; setTextColor(Theme.c.text); textSize = 20f; typeface = boldTf
}

fun Context.headlineText(value: String): TextView = TextView(this).apply {
    text = value; setTextColor(Theme.c.text); textSize = 17f; typeface = boldTf
}

fun Context.bodyText(value: String): TextView = TextView(this).apply {
    text = value; setTextColor(Theme.c.textSecondary); textSize = 15f; typeface = normalTf
    setLineSpacing(dp(3).toFloat(), 1f)
}

fun Context.labelText(value: String): TextView = TextView(this).apply {
    text = value; setTextColor(Theme.c.text); textSize = 13f; typeface = mediumTf
}

fun Context.captionText(value: String): TextView = TextView(this).apply {
    text = value; setTextColor(Theme.c.textTertiary); textSize = 12f; typeface = normalTf
}

fun Context.overlineText(value: String): TextView = TextView(this).apply {
    text = value.uppercase(); setTextColor(Theme.c.textTertiary); textSize = 11f; typeface = mediumTf
    letterSpacing = 0.06f
}

fun Context.sectionTitle(value: String): TextView = headlineText(value)

fun Context.badgeText(
    value: String,
    bgColor: Int = Theme.c.accentSoft,
    textColor: Int = Theme.c.accent
): TextView = TextView(this).apply {
    text = value; textSize = 11f; typeface = mediumTf; setTextColor(textColor)
    background = roundedDrawable(bgColor, radiusDp = 8); padding(h = 8, v = 4)
}

// ── Buttons (4.2) ──────────────────────────────────────────────

fun Context.primaryButton(label: String, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label; setTextColor(Theme.c.onAccent); textSize = 14f; typeface = mediumTf
        isAllCaps = false; gravity = Gravity.CENTER
        background = rippleDrawable(Theme.c.accent, radiusDp = 12)
        minHeight = dp(44); minimumHeight = dp(44)
        padding(h = 20, v = 10)
        onTapFeedback(onClick)
    }

fun Context.secondaryButton(label: String, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label; setTextColor(Theme.c.text); textSize = 13f; typeface = mediumTf
        isAllCaps = false; gravity = Gravity.CENTER
        background = rippleDrawable(Theme.c.surfaceHigh, radiusDp = 12, strokeColor = Theme.c.outline, strokeWidthDp = 1)
        minHeight = dp(40); minimumHeight = dp(40)
        padding(h = 16, v = 8)
        onTapFeedback(onClick)
    }

fun Context.textButton(label: String, color: Int = Theme.c.accent, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label; setTextColor(color); textSize = 13f; typeface = mediumTf
        isAllCaps = false; gravity = Gravity.CENTER
        background = rippleDrawable(Color.TRANSPARENT, radiusDp = 8)
        padding(h = 10, v = 6)
        onTap(onClick)
    }

fun Context.iconButton(
    icon: Icon, sizeDp: Int = 24, tint: Int = Theme.c.text,
    bgColor: Int = Color.TRANSPARENT, radiusDp: Int = 22,
    onClick: () -> Unit
): FrameLayout {
    val wrap = FrameLayout(this)
    val total = dp(sizeDp + 20)
    wrap.minimumWidth = total; wrap.minimumHeight = total
    wrap.background = rippleDrawable(bgColor, radiusDp)
    val iv = IconView(this, icon, sizeDp, tint)
    val lp = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER)
    wrap.addView(iv, lp)
    wrap.onTapFeedback(onClick)
    return wrap
}

fun Context.chipButton(label: String, isSelected: Boolean, onClick: () -> Unit): TextView =
    TextView(this).apply {
        text = label; textSize = 12f; typeface = mediumTf; gravity = Gravity.CENTER
        padding(h = 16, v = 8)
        if (isSelected) {
            setTextColor(Theme.c.onAccent)
            background = rippleDrawable(Theme.c.accent, radiusDp = 20)
        } else {
            setTextColor(Theme.c.textSecondary)
            background = rippleDrawable(Theme.c.surfaceHigh, radiusDp = 20, strokeColor = Theme.c.outline, strokeWidthDp = 1)
        }
        onTapFeedback(onClick)
    }

// ── ScreenHeader (4.1) ─────────────────────────────────────────

fun Context.screenHeader(
    title: String,
    leadingIcon: Icon? = null,
    onLeading: (() -> Unit)? = null,
    trailingContent: (LinearLayout.() -> Unit)? = null
): LinearLayout {
    val header = hstack {
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Theme.c.surface)
        padding(h = 20, v = 14)
    }

    if (leadingIcon != null && onLeading != null) {
        val btn = iconButton(leadingIcon, sizeDp = 22, tint = Theme.c.text) { onLeading() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = dp(12)
        btn.layoutParams = lp
        header.addView(btn)
    }

    val titleTv = titleText(title)
    titleTv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    header.addView(titleTv)

    trailingContent?.let { trailingLambda ->
        val trailing = hstack {
            gravity = Gravity.CENTER_VERTICAL
        }
        trailingLambda.invoke(trailing)
        header.addView(trailing)
    }
    return header
}

// ── SectionHeader (4.6) ────────────────────────────────────────

fun Context.sectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null): LinearLayout =
    hstack {
        gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(24); lp.bottomMargin = dp(12)
        lp.marginStart = dp(20); lp.marginEnd = dp(20)
        layoutParams = lp

        addView(headlineText(title).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (actionLabel != null && onAction != null) {
            addView(textButton(actionLabel, Theme.c.accent) { onAction() })
        }
    }

// ── SearchField (4.7) ──────────────────────────────────────────

fun Context.searchField(hint: String = "Search...", onSearch: (String) -> Unit): EditText =
    EditText(this).apply {
        setHint(hint)
        setTextColor(Theme.c.text); setHintTextColor(Theme.c.textTertiary)
        textSize = 15f; typeface = normalTf
        background = roundedDrawable(Theme.c.surfaceHigh, radiusDp = 24)
        minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        setSingleLine()
        setOnEditorActionListener { _, _, _ ->
            val q = text.toString().trim()
            if (q.isNotEmpty()) onSearch(q)
            true
        }
    }

// ── SegmentedControl ───────────────────────────────────────────

fun Context.segmentedControl(
    items: List<String>, selectedIndex: Int = 0,
    onChange: (Int) -> Unit
): LinearLayout {
    val container = hstack {
        background = roundedDrawable(Theme.c.surfaceHigh, radiusDp = 12, strokeColor = Theme.c.outline, strokeWidthDp = 1)
        padding(3)
    }
    val tabViews = mutableListOf<TextView>()

    fun applySelection(idx: Int) {
        tabViews.forEachIndexed { i, tv ->
            val sel = i == idx
            tv.background = if (sel) roundedDrawable(Theme.c.accent, radiusDp = 10) else null
            tv.setTextColor(if (sel) Theme.c.onAccent else Theme.c.textSecondary)
        }
    }

    items.forEachIndexed { i, label ->
        val tv = TextView(this).apply {
            text = label; textSize = 12f; typeface = mediumTf; gravity = Gravity.CENTER
            padding(h = 14, v = 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onTap {
                applySelection(i)
                onChange(i)
            }
        }
        tabViews.add(tv)
        container.addView(tv)
    }
    applySelection(selectedIndex)
    return container
}

// ── ListRow (generic tappable row) ─────────────────────────────

fun Context.listRow(
    height: Int = 64,
    block: LinearLayout.() -> Unit
): LinearLayout = hstack {
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = dp(height)
    background = rippleDrawable(Color.TRANSPARENT, radiusDp = 12)
    setPadding(dp(20), dp(8), dp(20), dp(8))
    block()
}

// ── ArtworkView (image with placeholder, rounded clip, fade-in) ──

fun Context.artworkView(sizeDp: Int = 56, radiusDp: Int = 12): ImageView =
    ImageView(this).apply {
        val s = dp(sizeDp)
        layoutParams = LinearLayout.LayoutParams(s, s)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = roundedDrawable(Theme.c.surfaceHigh, radiusDp)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.context.dp(radiusDp).toFloat())
            }
        }
    }

// ── Skeleton / shimmer block ───────────────────────────────────

class SkeletonView(context: Context, private val heightDp: Int = 14, private val radiusDp: Int = 8) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.c.surfaceHigh }
    private val anim = ValueAnimator.ofFloat(0.45f, 1f).apply {
        duration = 900; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { alpha = it.animatedValue as Float }
        start()
    }
    init { minimumHeight = dp(heightDp) }
    override fun onDraw(canvas: Canvas) {
        val r = dp(radiusDp).toFloat()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
    }
}

fun Context.skeleton(widthFraction: Float = 1f, heightDp: Int = 14): SkeletonView {
    return SkeletonView(this, heightDp).apply {
        val lp = LinearLayout.LayoutParams(
            if (widthFraction >= 1f) LinearLayout.LayoutParams.MATCH_PARENT
            else (resources.displayMetrics.widthPixels * widthFraction).toInt(),
            dp(heightDp)
        )
        lp.bottomMargin = dp(8)
        layoutParams = lp
    }
}

fun Context.skeletonRow(): LinearLayout = hstack {
    padding(h = 20, v = 10)
    val art = SkeletonView(this@skeletonRow, 56, 12).apply {
        val lp = LinearLayout.LayoutParams(dp(56), dp(56))
        lp.marginEnd = dp(12)
        layoutParams = lp
    }
    addView(art)
    val texts = vstack {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(skeleton(0.7f, 14))
        addView(skeleton(0.5f, 10))
    }
    addView(texts)
}

fun Context.skeletonRows(count: Int = 6): LinearLayout = vstack {
    for (i in 0 until count) addView(skeletonRow())
}

// ── EmptyState (4.9) ───────────────────────────────────────────

fun Context.emptyState(
    icon: Icon = Icon.EMPTY_INBOX,
    title: String = "Nothing here yet",
    message: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
): LinearLayout = vstack {
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(40), dp(60), dp(40), dp(60))

    // Badge circle
    val badge = frame {
        val size = dp(72)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = roundedDrawable(Theme.c.accentSoft, radiusDp = 36)
        val iv = IconView(this@emptyState, icon, 32, Theme.c.accent)
        addView(iv, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
    }
    addView(badge)

    val t = titleText(title).apply {
        gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(20); lp.bottomMargin = dp(8)
        layoutParams = lp
    }
    addView(t)

    if (message.isNotEmpty()) {
        val m = bodyText(message).apply {
            gravity = Gravity.CENTER; maxWidth = dp(280)
        }
        addView(m)
    }

    if (actionLabel != null && onAction != null) {
        val btn = primaryButton(actionLabel, onAction).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(20)
            layoutParams = lp
        }
        addView(btn)
    }
}

// ── ErrorState (4.9) ───────────────────────────────────────────

fun Context.errorState(
    message: String = "Something went wrong",
    onRetry: (() -> Unit)? = null
): LinearLayout = vstack {
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(40), dp(60), dp(40), dp(60))

    val badge = frame {
        val size = dp(72)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = roundedDrawable(Color.argb(40, Color.red(Theme.c.error), Color.green(Theme.c.error), Color.blue(Theme.c.error)), radiusDp = 36)
        val iv = IconView(this@errorState, Icon.ERROR, 32, Theme.c.error)
        addView(iv, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
    }
    addView(badge)

    val t = titleText("Error").apply {
        gravity = Gravity.CENTER; setTextColor(Theme.c.error)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(20); lp.bottomMargin = dp(8)
        layoutParams = lp
    }
    addView(t)
    addView(bodyText(message).apply { gravity = Gravity.CENTER; maxWidth = dp(280) })

    if (onRetry != null) {
        addView(secondaryButton("Try again") { onRetry() }.apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(20); layoutParams = lp
        })
    }
}

// ── ToastHost (replaces Toast.makeText) ────────────────────────

object ToastHost {
    private var currentToast: View? = null

    fun show(parent: FrameLayout, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        currentToast?.let { parent.removeView(it) }
        val ctx = parent.context
        val pill = ctx.hstack {
            gravity = Gravity.CENTER_VERTICAL
            background = ctx.roundedDrawable(Theme.c.surfaceHigh, radiusDp = 14, strokeColor = Theme.c.outline, strokeWidthDp = 1)
            elevation = ctx.dp(6).toFloat()
            setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))

            addView(ctx.labelText(message).apply {
                setTextColor(Theme.c.text)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (actionLabel != null && onAction != null) {
                addView(ctx.textButton(actionLabel, Theme.c.accent) { onAction() }.apply {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.marginStart = ctx.dp(8); layoutParams = lp
                })
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            setMargins(ctx.dp(16), 0, ctx.dp(16), ctx.dp(140))
        }
        pill.translationY = ctx.dp(60).toFloat()
        pill.alpha = 0f
        parent.addView(pill, lp)
        pill.animate().translationY(0f).alpha(1f).setDuration(250).start()
        currentToast = pill
        pill.postDelayed({
            pill.animate().translationY(ctx.dp(40).toFloat()).alpha(0f).setDuration(200).withEndAction {
                try { parent.removeView(pill) } catch (_: Exception) {}
            }.start()
        }, 3500)
    }
}

// ── BottomSheet (4.8) ──────────────────────────────────────────

fun Context.showBottomSheet(
    parent: FrameLayout,
    block: LinearLayout.() -> Unit
) {
    val ctx = this
    val scrim = View(ctx).apply {
        setBackgroundColor(Theme.c.scrim)
        alpha = 0f
    }
    val sheet = vstack {
        background = roundedDrawable(Theme.c.surface, radiusDp = 28).apply {
            cornerRadii = floatArrayOf(
                dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(), dp(28).toFloat(),
                0f, 0f, 0f, 0f
            )
        }
        // Drag handle
        addView(frame {
            val handle = View(ctx).apply {
                background = roundedDrawable(Theme.c.outline, radiusDp = 2)
            }
            val hlp = FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER)
            hlp.topMargin = dp(12); hlp.bottomMargin = dp(12)
            addView(handle, hlp)
        })
        block()
        setPadding(0, 0, 0, dp(20))
    }

    val sheetLp = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT,
        Gravity.BOTTOM
    )

    fun dismiss() {
        scrim.animate().alpha(0f).setDuration(200).start()
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(200).withEndAction {
            try { parent.removeView(scrim); parent.removeView(sheet) } catch (_: Exception) {}
        }.start()
    }

    scrim.setOnClickListener { dismiss() }
    parent.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    parent.addView(sheet, sheetLp)

    sheet.post {
        sheet.translationY = sheet.height.toFloat()
        scrim.animate().alpha(1f).setDuration(200).start()
        sheet.animate().translationY(0f).setDuration(300).setInterpolator(AccelerateDecelerateInterpolator()).start()
    }
}

fun Context.sheetRow(icon: Icon, label: String, tint: Int = Theme.c.text, onClick: () -> Unit): LinearLayout =
    hstack {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        background = rippleDrawable(Color.TRANSPARENT, radiusDp = 0)
        setPadding(dp(24), dp(12), dp(24), dp(12))

        addView(IconView(this@sheetRow, icon, 22, tint).apply {
            val lp = LinearLayout.LayoutParams(dp(22), dp(22))
            lp.marginEnd = dp(16)
            layoutParams = lp
        })
        addView(labelText(label).apply {
            setTextColor(tint)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        onTap(onClick)
    }

// ── Divider ────────────────────────────────────────────────────

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(Theme.c.outline)
    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
        marginStart = dp(20); marginEnd = dp(20)
    }
}
