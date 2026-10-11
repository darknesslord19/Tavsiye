package com.darkneslord.iptv

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import kotlin.math.min

/** Darknes Lord renk paleti. */
object Theme {
    val BG = Color.parseColor("#0A0A0F")
    val CARD = Color.parseColor("#14141C")
    val CARD_ALT = Color.parseColor("#1B1B26")
    val STROKE = Color.parseColor("#2A2A3A")
    val TEXT = Color.parseColor("#F4F4F8")
    val MUTED = Color.parseColor("#8E8EA3")
    val ACCENT = Color.parseColor("#E11D48")
    val ACCENT_2 = Color.parseColor("#6D28D9")
    val GREEN = Color.parseColor("#22C55E")
    val RED = Color.parseColor("#F43F5E")
    val AMBER = Color.parseColor("#F59E0B")
    val OFF = Color.parseColor("#3A3A4C")
    val VIOLET = Color.parseColor("#A78BFA")
    val BLUE = Color.parseColor("#60A5FA")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

fun roundBg(ctx: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = ctx.dpf(radiusDp.toFloat())
        if (strokeDp > 0) setStroke(ctx.dp(strokeDp), strokeColor)
    }

/** Dokunma dalgası (ripple) efektli yuvarlak arka plan. */
fun rippleOval(ctx: Context): RippleDrawable {
    val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
    return RippleDrawable(ColorStateList.valueOf(withAlpha(Color.WHITE, 40)), null, mask)
}

enum class IconKind { EYE, EYE_OFF, EDIT, TRASH, REFRESH, CHEVRON, PLUS, CLOSE, CHECK, USER, SHIELD }

/** 24x24 ızgarada çizilen çizgi-ikonlar (kaynak dosyası gerekmez). */
class IconView(ctx: Context, var kind: IconKind, var tint: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.9f
    }

    init {
        isClickable = true
        background = rippleOval(ctx)
    }

    fun set(kind: IconKind, tint: Int) {
        this.kind = kind
        this.tint = tint
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height) / 24f * 0.58f // ikon, dokunma alanının %58'i
        c.save()
        c.translate(width / 2f, height / 2f)
        c.scale(s, s)
        c.translate(-12f, -12f)
        p.color = tint
        p.style = Paint.Style.STROKE
        when (kind) {
            IconKind.EYE, IconKind.EYE_OFF -> {
                val eye = Path().apply {
                    moveTo(2f, 12f)
                    cubicTo(5f, 6.5f, 8.5f, 5f, 12f, 5f)
                    cubicTo(15.5f, 5f, 19f, 6.5f, 22f, 12f)
                    cubicTo(19f, 17.5f, 15.5f, 19f, 12f, 19f)
                    cubicTo(8.5f, 19f, 5f, 17.5f, 2f, 12f)
                    close()
                }
                c.drawPath(eye, p)
                c.drawCircle(12f, 12f, 3f, p)
                if (kind == IconKind.EYE_OFF) c.drawLine(3.5f, 3.5f, 20.5f, 20.5f, p)
            }
            IconKind.EDIT -> {
                val pen = Path().apply {
                    moveTo(4f, 20f); lineTo(8.2f, 19.2f); lineTo(19.5f, 7.9f)
                    lineTo(16.1f, 4.5f); lineTo(4.8f, 15.8f); close()
                }
                c.drawPath(pen, p)
                c.drawLine(14f, 6.6f, 17.4f, 10f, p)
            }
            IconKind.TRASH -> {
                c.drawLine(4f, 7f, 20f, 7f, p)
                c.drawPath(Path().apply { moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f) }, p)
                c.drawPath(Path().apply { moveTo(6f, 7f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 7f) }, p)
                c.drawLine(10f, 11f, 10f, 16f, p)
                c.drawLine(14f, 11f, 14f, 16f, p)
            }
            IconKind.REFRESH -> {
                c.drawArc(RectF(5f, 5f, 19f, 19f), 20f, 300f, false, p)
                c.drawPath(Path().apply { moveTo(20.5f, 4f); lineTo(20.5f, 8.5f); lineTo(16f, 8.5f) }, p)
            }
            IconKind.CHEVRON -> c.drawPath(Path().apply { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }, p)
            IconKind.PLUS -> {
                c.drawLine(12f, 5f, 12f, 19f, p)
                c.drawLine(5f, 12f, 19f, 12f, p)
            }
            IconKind.CLOSE -> {
                c.drawLine(6f, 6f, 18f, 18f, p)
                c.drawLine(18f, 6f, 6f, 18f, p)
            }
            IconKind.USER -> {
                c.drawCircle(12f, 8f, 4f, p)
                c.drawPath(Path().apply {
                    moveTo(4f, 20.5f); cubicTo(4f, 16f, 8f, 14f, 12f, 14f); cubicTo(16f, 14f, 20f, 16f, 20f, 20.5f)
                }, p)
            }
            IconKind.SHIELD -> {
                c.drawPath(Path().apply {
                    moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f)
                    cubicTo(20f, 16.5f, 16.5f, 19.5f, 12f, 21f)
                    cubicTo(7.5f, 19.5f, 4f, 16.5f, 4f, 12f); lineTo(4f, 6f); close()
                }, p)
                c.drawPath(Path().apply { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) }, p)
            }
            IconKind.CHECK -> c.drawPath(Path().apply { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }, p)
        }
        c.restore()
    }
}

/** Aktif / pasif anahtarı (animasyonlu). */
class ToggleView(ctx: Context) : View(ctx) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private var pos = 0f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    init {
        isClickable = true
        setOnClickListener {
            setChecked(!checked, true)
            onChange?.invoke(checked)
        }
    }

    fun setChecked(value: Boolean, animate: Boolean = false) {
        checked = value
        val target = if (value) 1f else 0f
        if (animate) {
            ValueAnimator.ofFloat(pos, target).apply {
                duration = 160
                addUpdateListener { pos = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            pos = target
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(46), context.dp(26))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = blend(Theme.OFF, Theme.GREEN, pos)
        c.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, track)
        val r = h / 2 - context.dpf(3f)
        val cx = h / 2 + pos * (w - h)
        c.drawCircle(cx, h / 2, r, thumb)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.argb(
        255,
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}

/** Başlıktaki vektör IPTV / medya logosu: gradyan zemin, anten, TV gövdesi ve oynat simgesi. */
class LogoView(ctx: Context) : View(ctx) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 1.7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bg.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Theme.ACCENT, Theme.ACCENT_2, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        c.drawRoundRect(0f, 0f, w, height.toFloat(), w * 0.28f, w * 0.28f, bg)
        c.save()
        c.scale(w / 24f, w / 24f)
        c.drawRoundRect(RectF(4f, 8f, 20f, 18.5f), 2.6f, 2.6f, line)                       // TV gövdesi
        c.drawPath(Path().apply { moveTo(8.5f, 3.8f); lineTo(12f, 8f); lineTo(15.5f, 3.8f) }, line) // anten
        c.drawPath(Path().apply { moveTo(10.6f, 10.7f); lineTo(10.6f, 16f); lineTo(15f, 13.35f); close() }, fill) // oynat
        c.drawLine(8f, 21f, 16f, 21f, line)                                                 // ayak
        c.restore()
    }
}
