package com.example.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.ClassScheduleManager
import com.example.data.ScheduleStatus
import kotlin.math.roundToInt

/**
 * Bouncing DVD Screensaver style overlay shown during class break time.
 * Displays:
 * "下課時間"
 * "還剩 x 分鐘"
 *
 * Requirements:
 * 1. Bounces around like the classic DVD screensaver (https://bouncingdvdlogo.com/)
 *    changing gradient colors on every edge bounce.
 * 2. Click / Touch penetrates (click-through) to underlying apps/system by default.
 * 3. Double click on the bouncing logo / widget hides the overlay.
 * 4. Automatically hides when class time begins.
 */
class BreakBouncingOverlay(
    private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootContainer: FrameLayout? = null
    private var bouncingBadgeView: LinearLayout? = null
    private var titleTextView: TextView? = null
    private var timerTextView: TextView? = null

    private var screenWidth = 1080
    private var screenHeight = 1920

    // Coordinates and velocities for the bouncing animation (in px)
    private var posX = 100f
    private var posY = 200f
    private var velocityX = 3.5f
    private var velocityY = 3.5f

    // Size of the bouncing badge
    private var badgeWidth = 0
    private var badgeHeight = 0

    // Palette of vibrant colors like classic DVD logo
    private val vibrantColors = listOf(
        Pair(Color.parseColor("#FF1744"), Color.parseColor("#D50000")), // Vivid Red
        Pair(Color.parseColor("#2979FF"), Color.parseColor("#2962FF")), // Vivid Blue
        Pair(Color.parseColor("#00E676"), Color.parseColor("#00C853")), // Vivid Green
        Pair(Color.parseColor("#FF9100"), Color.parseColor("#FF6D00")), // Vivid Orange
        Pair(Color.parseColor("#D500F9"), Color.parseColor("#AA00FF")), // Vivid Purple
        Pair(Color.parseColor("#00E5FF"), Color.parseColor("#00B8D4")), // Vivid Cyan
        Pair(Color.parseColor("#FFD600"), Color.parseColor("#FFAB00")), // Vivid Amber
        Pair(Color.parseColor("#F50057"), Color.parseColor("#C51162"))  // Vivid Pink
    )
    private var currentColorIndex = 0

    private var isRunning = false
    private var isDismissedByUser = false
    private var tickerRunnable: Runnable? = null
    private var animRunnable: Runnable? = null

    init {
        updateScreenDimensions()
    }

    private fun updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(dm)
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
        }
    }

    fun onConfigurationChanged() {
        updateScreenDimensions()
    }

    /**
     * Updates or triggers break status display.
     * When it is break time, shows and bounces if not manually hidden.
     * When it is class time, hides and resets user dismissal state.
     */
    fun updateScheduleStatus(status: ScheduleStatus) {
        if (status.isClass) {
            // It's class time: hide bouncing overlay and reset dismissal state
            isDismissedByUser = false
            hide()
        } else {
            // It's break time
            if (isDismissedByUser) {
                return // User double-clicked to hide it for this break
            }
            val remainingMins = calculateRemainingMinutes(status)
            if (rootContainer == null) {
                show(remainingMins)
            } else {
                updateText(remainingMins)
            }
        }
    }

    private fun calculateRemainingMinutes(status: ScheduleStatus): Int {
        if (status is ScheduleStatus.InBreak && status.nextPeriod != null) {
            val now = java.util.Calendar.getInstance()
            val currentSec = now.get(java.util.Calendar.HOUR_OF_DAY) * 3600 +
                    now.get(java.util.Calendar.MINUTE) * 60 +
                    now.get(java.util.Calendar.SECOND)
            val diffSec = status.nextPeriod.startSecondsOfDay - currentSec
            return (diffSec / 60).coerceAtLeast(1)
        }
        return 10
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(remainingMinutes: Int) {
        if (!Settings.canDrawOverlays(context)) return
        if (rootContainer != null) return // Already visible

        updateScreenDimensions()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Entire screen overlay with NOT_TOUCH_MODAL and FLAG_LAYOUT_IN_SCREEN
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Gesture detector to detect double-tap to hide
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                hide()
                isDismissedByUser = true
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Single tap does nothing (penetrates through via dispatchTouchEvent)
                return false
            }
        })

        // Root container: intercepts double-taps on the badge, but lets touches penetrate elsewhere!
        val root = object : FrameLayout(context) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val badge = bouncingBadgeView
                if (badge != null && badge.visibility == View.VISIBLE) {
                    val location = IntArray(2)
                    badge.getLocationOnScreen(location)
                    val bx = location[0]
                    val by = location[1]
                    val inBadge = ev.rawX >= bx && ev.rawX <= (bx + badge.width) &&
                            ev.rawY >= by && ev.rawY <= (by + badge.height)

                    if (inBadge) {
                        // Deliver to gesture detector for double tap detection
                        gestureDetector.onTouchEvent(ev)
                        // Allow penetration even when touching badge if not consumed by double tap
                        return false
                    }
                }
                // Outside badge: completely penetrate!
                return false
            }
        }

        // Create the bouncing badge
        val badge = createBadgeView(remainingMinutes)
        bouncingBadgeView = badge
        root.addView(badge)

        try {
            windowManager.addView(root, params)
            rootContainer = root
            isRunning = true

            // Set initial speed based on density
            val density = context.resources.displayMetrics.density
            val speed = 2.8f * density
            velocityX = if (Math.random() > 0.5) speed else -speed
            velocityY = if (Math.random() > 0.5) speed else -speed

            // Initial random position
            posX = (100f * density).coerceAtMost((screenWidth - dpToPx(180)).toFloat())
            posY = (200f * density).coerceAtMost((screenHeight - dpToPx(100)).toFloat())

            badge.translationX = posX
            badge.translationY = posY

            startAnimationLoop()
            startStatusTicker()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createBadgeView(remainingMinutes: Int): LinearLayout {
        val badge = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(18), dpToPx(12), dpToPx(18), dpToPx(12))

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            elevation = dpToPx(12).toFloat()
        }

        updateBadgeBackground(badge, currentColorIndex)

        // Top title: "下課時間"
        val title = TextView(context).apply {
            text = "下課時間"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 3f, Color.parseColor("#80000000"))
        }
        titleTextView = title
        badge.addView(title)

        // Bottom timer: "還剩 x 分鐘"
        val timer = TextView(context).apply {
            text = "還剩 ${remainingMinutes} 分鐘"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFFDE7"))
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"))
        }
        timerTextView = timer
        badge.addView(timer)

        val hint = TextView(context).apply {
            text = "雙擊隱藏"
            textSize = 9f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2), 0, 0)
        }
        badge.addView(hint)

        return badge
    }

    private fun updateBadgeBackground(view: View, colorIndex: Int) {
        val (c1, c2) = vibrantColors[colorIndex % vibrantColors.size]
        val shape = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(c1, c2)
        ).apply {
            cornerRadius = dpToPx(18).toFloat()
            setStroke(dpToPx(2.5f), Color.WHITE)
        }
        view.background = shape
    }

    private fun updateText(remainingMinutes: Int) {
        timerTextView?.text = "還剩 ${remainingMinutes} 分鐘"
    }

    private fun startAnimationLoop() {
        animRunnable = object : Runnable {
            override fun run() {
                if (!isRunning || rootContainer == null || bouncingBadgeView == null) return

                val badge = bouncingBadgeView ?: return
                if (badgeWidth == 0 || badgeHeight == 0) {
                    badgeWidth = badge.width
                    badgeHeight = badge.height
                    if (badgeWidth == 0) {
                        badge.measure(
                            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST)
                        )
                        badgeWidth = badge.measuredWidth
                        badgeHeight = badge.measuredHeight
                    }
                }

                val effectiveWidth = if (badgeWidth > 0) badgeWidth else dpToPx(160)
                val effectiveHeight = if (badgeHeight > 0) badgeHeight else dpToPx(80)

                posX += velocityX
                posY += velocityY

                var bounced = false

                // Left bounce
                if (posX <= 0f) {
                    posX = 0f
                    velocityX = -velocityX
                    bounced = true
                }
                // Right bounce
                val maxX = (screenWidth - effectiveWidth).coerceAtLeast(0).toFloat()
                if (posX >= maxX) {
                    posX = maxX
                    velocityX = -velocityX
                    bounced = true
                }

                // Top bounce (consider status bar offset approx 24dp)
                val topBoundary = dpToPx(24).toFloat()
                if (posY <= topBoundary) {
                    posY = topBoundary
                    velocityY = -velocityY
                    bounced = true
                }
                // Bottom bounce (consider navigation bar offset approx 48dp)
                val maxY = (screenHeight - effectiveHeight - dpToPx(48)).coerceAtLeast(0).toFloat()
                if (posY >= maxY) {
                    posY = maxY
                    velocityY = -velocityY
                    bounced = true
                }

                if (bounced) {
                    // Change color on every bounce, classic DVD style!
                    currentColorIndex = (currentColorIndex + 1) % vibrantColors.size
                    updateBadgeBackground(badge, currentColorIndex)
                }

                badge.translationX = posX
                badge.translationY = posY

                // 60 FPS approx: ~16ms
                mainHandler.postDelayed(this, 16)
            }
        }
        mainHandler.post(animRunnable!!)
    }

    private fun startStatusTicker() {
        tickerRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                val status = ClassScheduleManager.getCurrentStatus()
                if (status.isClass) {
                    hide()
                } else {
                    val mins = calculateRemainingMinutes(status)
                    updateText(mins)
                    mainHandler.postDelayed(this, 5000) // Update minute every 5s
                }
            }
        }
        mainHandler.postDelayed(tickerRunnable!!, 5000)
    }

    fun hide() {
        isRunning = false
        animRunnable?.let { mainHandler.removeCallbacks(it) }
        tickerRunnable?.let { mainHandler.removeCallbacks(it) }
        animRunnable = null
        tickerRunnable = null

        rootContainer?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            rootContainer = null
            bouncingBadgeView = null
            titleTextView = null
            timerTextView = null
        }
    }

    fun destroy() {
        hide()
    }

    private fun dpToPx(dp: Number): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
    }
}
