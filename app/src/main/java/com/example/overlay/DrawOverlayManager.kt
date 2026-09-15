package com.example.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.data.AppSettings
import com.example.data.ClassScheduleManager
import com.example.data.DrawResult
import com.example.data.StudentEntity
import com.example.data.StudentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class DrawOverlayManager(
    private val context: Context,
    private val repository: StudentRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var floatingContainerView: LinearLayout? = null
    private var floatingBallView: FrameLayout? = null
    private var floatingResetBtn: View? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null

    private var resultOverlayView: View? = null
    private var resultOverlayParams: WindowManager.LayoutParams? = null

    private var breakLockOverlayView: View? = null
    private var breakLockOverlayParams: WindowManager.LayoutParams? = null

    private val breakBouncingOverlay = BreakBouncingOverlay(context)

    private var currentSettings = AppSettings()
    private var currentDrawnCount: Int = 0
    private var autoDismissJob: Job? = null
    private var animationJob: Job? = null

    private var screenWidth = 1080
    private var screenHeight = 1920

    init {
        updateScreenDimensions()
        observeSettings()
        observeDrawnCount()
        startScheduleAutoResetTicker()
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

    private fun observeSettings() {
        scope.launch {
            repository.settingsFlow.collect { settings ->
                val oldSettings = currentSettings
                currentSettings = settings
                if (floatingContainerView != null) {
                    if (oldSettings.buttonSizeDp != settings.buttonSizeDp ||
                        oldSettings.buttonOpacity != settings.buttonOpacity
                    ) {
                        updateFloatingButtonAppearance()
                    }
                }
                if (oldSettings.breakDvdEnabled != settings.breakDvdEnabled) {
                    withContext(Dispatchers.Main) {
                        if (!settings.breakDvdEnabled) {
                            breakBouncingOverlay.hide()
                        } else {
                            val status = ClassScheduleManager.getCurrentStatus()
                            breakBouncingOverlay.updateScheduleStatus(status)
                        }
                    }
                }
            }
        }
    }

    private fun observeDrawnCount() {
        scope.launch {
            repository.drawnCount.collect { count ->
                currentDrawnCount = count
                updateResetButtonVisibility()
            }
        }
    }

    private fun startScheduleAutoResetTicker() {
        scope.launch {
            while (isActive) {
                val didReset = ClassScheduleManager.checkAndAutoReset(context, repository)
                if (didReset) {
                    withContext(Dispatchers.Main) {
                        showToast("上課時間到，已自動重設抽籤名單")
                    }
                }
                withContext(Dispatchers.Main) {
                    if (currentSettings.breakDvdEnabled) {
                        val status = ClassScheduleManager.getCurrentStatus()
                        breakBouncingOverlay.updateScheduleStatus(status)
                    } else {
                        breakBouncingOverlay.hide()
                    }
                }
                delay(3000)
            }
        }
    }

    fun showFloatingButton() {
        if (!Settings.canDrawOverlays(context)) return
        if (floatingContainerView != null) return // Already shown

        updateScreenDimensions()
        val sizePx = dpToPx(currentSettings.buttonSizeDp)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            val initialX = if (currentSettings.rememberPosition) {
                (screenWidth * currentSettings.lastPositionXPercent - sizePx / 2).toInt()
            } else {
                screenWidth - sizePx - dpToPx(16)
            }

            val initialY = if (currentSettings.rememberPosition) {
                (screenHeight * currentSettings.lastPositionYPercent - sizePx / 2).toInt()
            } else {
                screenHeight / 2 - sizePx / 2
            }

            x = initialX.coerceIn(0, (screenWidth - dpToPx(160)).coerceAtLeast(0))
            y = initialY.coerceIn(dpToPx(40), (screenHeight - sizePx - dpToPx(40)).coerceAtLeast(0))
        }

        floatingButtonParams = params
        val container = createFloatingContainerView(sizePx)
        floatingContainerView = container

        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
            floatingContainerView = null
        }
    }

    fun hideFloatingButton() {
        floatingContainerView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingContainerView = null
            floatingBallView = null
            floatingResetBtn = null
        }
    }

    fun hideResultOverlay() {
        autoDismissJob?.cancel()
        animationJob?.cancel()
        resultOverlayView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            resultOverlayView = null
        }
    }

    fun hideBreakLockOverlay() {
        breakLockOverlayView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            breakLockOverlayView = null
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        updateScreenDimensions()
        breakBouncingOverlay.onConfigurationChanged()
        floatingContainerView?.let { view ->
            floatingButtonParams?.let { params ->
                val sizePx = dpToPx(currentSettings.buttonSizeDp)
                params.x = params.x.coerceIn(0, (screenWidth - dpToPx(160)).coerceAtLeast(0))
                params.y = params.y.coerceIn(dpToPx(40), (screenHeight - sizePx - dpToPx(40)).coerceAtLeast(0))
                try {
                    if (view.isAttachedToWindow) {
                        windowManager.updateViewLayout(view, params)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        resultOverlayView?.let { view ->
            resultOverlayParams?.let { params ->
                params.width = (screenWidth * 0.72f).roundToInt()
                try {
                    if (view.isAttachedToWindow) {
                        windowManager.updateViewLayout(view, params)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun destroy() {
        breakBouncingOverlay.destroy()
        hideBreakLockOverlay()
        hideResultOverlay()
        hideFloatingButton()
        scope.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingContainerView(sizePx: Int): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Create Main Floating Ball (Dice)
        val ball = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
        val ballBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb((currentSettings.buttonOpacity * 240).toInt(), 30, 27, 75)) // Deep indigo
            setStroke(dpToPx(2), Color.argb(180, 129, 140, 248)) // Glowing violet border
        }
        ball.background = ballBg
        ball.elevation = dpToPx(10).toFloat()

        val iconText = TextView(context).apply {
            text = "🎲"
            textSize = (currentSettings.buttonSizeDp * 0.44f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        ball.addView(iconText)
        floatingBallView = ball

        // 2. Create Reset Button Pill ("🔄 重置")
        val resetBtn = createResetButtonView()
        floatingResetBtn = resetBtn
        resetBtn.visibility = if (currentDrawnCount > 0) View.VISIBLE else View.GONE

        // 3. Arrange Views initially based on screen position
        val params = floatingButtonParams
        val isRightSide = (params?.x ?: 0) > screenWidth / 2
        if (isRightSide) {
            container.addView(resetBtn)
            container.addView(ball)
        } else {
            container.addView(ball)
            container.addView(resetBtn)
        }

        // 4. Setup Touch Listener on the Ball for Drag, Tap (<800ms), Long-press (>800ms)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var downTime = 0L
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            longPressTriggered = true
            ball.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            openMainActivity()
        }

        ball.setOnTouchListener { _, event ->
            val p = floatingButtonParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressTriggered = false
                    mainHandler.postDelayed(longPressRunnable, 850)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }

                    if (isDragging && !longPressTriggered) {
                        val currentSize = dpToPx(currentSettings.buttonSizeDp)
                        p.x = (initialX + dx).coerceIn(0, (screenWidth - currentSize).coerceAtLeast(0))
                        p.y = (initialY + dy).coerceIn(dpToPx(20), (screenHeight - currentSize - dpToPx(20)).coerceAtLeast(0))
                        try {
                            if (container.isAttachedToWindow) {
                                windowManager.updateViewLayout(container, p)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val pressDuration = System.currentTimeMillis() - downTime

                    if (!isDragging && !longPressTriggered && pressDuration < 850) {
                        // Short Click Trigger
                        ball.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        handleBallClick()
                    } else if (isDragging) {
                        // Adjust reset button position relative to the ball (left vs right)
                        updateResetButtonPlacement()

                        if (currentSettings.rememberPosition) {
                            val currentSize = dpToPx(currentSettings.buttonSizeDp)
                            val xPercent = (p.x + currentSize / 2f) / screenWidth.toFloat()
                            val yPercent = (p.y + currentSize / 2f) / screenHeight.toFloat()
                            scope.launch(Dispatchers.IO) {
                                repository.settingsManager.savePosition(xPercent, yPercent)
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }

        return container
    }

    private fun createResetButtonView(): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dpToPx(10)
            val padV = dpToPx(6)
            setPadding(padH, padV, padH, padV)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18).toFloat()
                setColor(Color.argb((currentSettings.buttonOpacity * 240).toInt(), 30, 27, 75))
                setStroke(dpToPx(1.5f), Color.argb(200, 239, 68, 68)) // Distinct Rose/Coral border
            }
            background = bg
            elevation = dpToPx(10).toFloat()

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(6)
                rightMargin = dpToPx(6)
            }
        }

        val text = TextView(context).apply {
            this.text = "🔄 重置"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FECDD3")) // Soft coral red
        }
        pill.addView(text)

        pill.setOnClickListener {
            pill.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            scope.launch {
                withContext(Dispatchers.IO) {
                    repository.resetCurrentRound()
                }
                withContext(Dispatchers.Main) {
                    showToast("已重設本輪抽籤名單")
                }
            }
        }

        return pill
    }

    private fun updateResetButtonVisibility() {
        floatingResetBtn?.let { btn ->
            btn.visibility = if (currentDrawnCount > 0) View.VISIBLE else View.GONE
        }
    }

    private fun updateResetButtonPlacement() {
        val container = floatingContainerView ?: return
        val ball = floatingBallView ?: return
        val resetBtn = floatingResetBtn ?: return
        val params = floatingButtonParams ?: return

        val isRightSide = params.x > screenWidth / 2
        val firstChild = if (container.childCount > 0) container.getChildAt(0) else null

        val needsReorder = if (isRightSide) {
            firstChild != resetBtn
        } else {
            firstChild != ball
        }

        if (needsReorder) {
            container.removeAllViews()
            if (isRightSide) {
                container.addView(resetBtn)
                container.addView(ball)
            } else {
                container.addView(ball)
                container.addView(resetBtn)
            }
            try {
                if (container.isAttachedToWindow) {
                    windowManager.updateViewLayout(container, params)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateFloatingButtonAppearance() {
        val ball = floatingBallView ?: return
        val container = floatingContainerView ?: return
        val params = floatingButtonParams ?: return
        val sizePx = dpToPx(currentSettings.buttonSizeDp)

        ball.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb((currentSettings.buttonOpacity * 240).toInt(), 30, 27, 75))
            setStroke(dpToPx(2), Color.argb(180, 129, 140, 248))
        }
        ball.background = bg

        val textView = ball.getChildAt(0) as? TextView
        textView?.textSize = (currentSettings.buttonSizeDp * 0.44f)

        try {
            if (container.isAttachedToWindow) {
                windowManager.updateViewLayout(container, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleBallClick() {
        // Check if currently in break time (locked)
        if (ClassScheduleManager.isBreakTime()) {
            showBreakLockOverlay()
        } else {
            triggerDraw()
        }
    }

    fun triggerDraw() {
        scope.launch {
            // Check auto-reset before draw
            ClassScheduleManager.checkAndAutoReset(context, repository)
            val drawResult = withContext(Dispatchers.IO) {
                repository.executeDraw()
            }
            displayDrawResult(drawResult)
        }
    }

    private fun showBreakLockOverlay() {
        hideBreakLockOverlay()
        hideResultOverlay()
        updateScreenDimensions()

        val cardWidth = (screenWidth * 0.82f).roundToInt().coerceIn(dpToPx(280), dpToPx(340))

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.45f
            windowAnimations = android.R.style.Animation_Dialog
        }

        breakLockOverlayParams = params
        val view = createBreakLockCardView(cardWidth)
        breakLockOverlayView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            breakLockOverlayView = null
        }
    }

    private fun createBreakLockCardView(cardWidth: Int): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.argb(245, 15, 23, 42)) // Slate-900 96% opacity
                setStroke(dpToPx(1.5f), Color.argb(160, 99, 102, 241))
            }
            background = bg
            elevation = dpToPx(16).toFloat()
        }

        // Header Row with Title and Close (✕)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(context).apply {
            text = "🔒 下課時間鎖定"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F8FAFC"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                hideBreakLockOverlay()
            }
        }
        header.addView(closeBtn)
        root.addView(header)

        // Subtitle
        val subtitle = TextView(context).apply {
            text = "目前為下課休息時間\n請輸入密碼進行抽籤"
            textSize = 13f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8), 0, dpToPx(12))
        }
        root.addView(subtitle)

        // PIN Dots Container (6 dots)
        val dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(4), 0, dpToPx(10))
        }

        val dotViews = mutableListOf<View>()
        for (i in 0 until 6) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(14), dpToPx(14)).apply {
                    leftMargin = dpToPx(6)
                    rightMargin = dpToPx(6)
                }
                background = createDotDrawable(filled = false)
            }
            dotViews.add(dot)
            dotsContainer.addView(dot)
        }
        root.addView(dotsContainer)

        // Error message text
        val errorText = TextView(context).apply {
            text = "密碼錯誤，請重新輸入"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#EF4444")) // Red
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            setPadding(0, 0, 0, dpToPx(8))
        }
        root.addView(errorText)

        // Entered PIN state
        var enteredPin = ""

        fun updateDots() {
            for (i in dotViews.indices) {
                dotViews[i].background = createDotDrawable(filled = i < enteredPin.length)
            }
        }

        // On-screen Numeric Keypad (1-9, 取消, 0, ⌫)
        val keypadLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("取消", "0", "⌫")
        )

        for (row in keys) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(6)
                }
            }

            for (key in row) {
                val keyBtn = TextView(context).apply {
                    text = key
                    textSize = if (key.length > 1) 14f else 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(
                        when (key) {
                            "取消" -> Color.parseColor("#94A3B8")
                            "⌫" -> Color.parseColor("#FCA5A5")
                            else -> Color.parseColor("#F8FAFC")
                        }
                    )
                    gravity = Gravity.CENTER

                    val btnBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(Color.argb(160, 30, 41, 59)) // Slate-800
                        setStroke(dpToPx(1), Color.argb(100, 71, 85, 105))
                    }
                    background = btnBg

                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        dpToPx(44),
                        1f
                    ).apply {
                        leftMargin = dpToPx(4)
                        rightMargin = dpToPx(4)
                    }

                    setOnClickListener {
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        when (key) {
                            "取消" -> {
                                hideBreakLockOverlay()
                            }
                            "⌫" -> {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                    updateDots()
                                    errorText.visibility = View.INVISIBLE
                                }
                            }
                            else -> {
                                if (enteredPin.length < 6) {
                                    enteredPin += key
                                    updateDots()
                                    errorText.visibility = View.INVISIBLE

                                    if (enteredPin.length == 6) {
                                        if (ClassScheduleManager.verifyPassword(enteredPin)) {
                                            hideBreakLockOverlay()
                                            triggerDraw()
                                        } else {
                                            errorText.visibility = View.VISIBLE
                                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                            mainHandler.postDelayed({
                                                enteredPin = ""
                                                updateDots()
                                                errorText.visibility = View.INVISIBLE
                                            }, 700)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                rowLayout.addView(keyBtn)
            }
            keypadLayout.addView(rowLayout)
        }
        root.addView(keypadLayout)

        return root
    }

    private fun createDotDrawable(filled: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (filled) {
                setColor(Color.parseColor("#818CF8")) // Indigo
                setStroke(dpToPx(1.5f), Color.parseColor("#C7D2FE"))
            } else {
                setColor(Color.argb(80, 51, 65, 85)) // Slate
                setStroke(dpToPx(1.5f), Color.parseColor("#64748B"))
            }
        }
    }

    private fun displayDrawResult(result: DrawResult) {
        hideResultOverlay()
        updateScreenDimensions()

        val cardWidth = (screenWidth * 0.72f).roundToInt().coerceIn(dpToPx(260), dpToPx(380))

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.25f
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            windowAnimations = android.R.style.Animation_Dialog
        }

        resultOverlayParams = params
        val view = createResultCardView(result, cardWidth)
        resultOverlayView = view

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            resultOverlayView = null
            return
        }

        if (result is DrawResult.Success && currentSettings.animationEnabled && result.candidates.isNotEmpty()) {
            runDrawAnimation(view, result)
        } else {
            scheduleAutoDismiss()
        }
    }

    private fun runDrawAnimation(cardView: View, success: DrawResult.Success) {
        val numberText = cardView.findViewById<TextView>(VIEW_ID_NUMBER)
        val nameText = cardView.findViewById<TextView>(VIEW_ID_NAME)
        val engText = cardView.findViewById<TextView>(VIEW_ID_ENG)

        animationJob = scope.launch {
            val totalSteps = 12
            val pool = success.candidates
            for (step in 0 until totalSteps) {
                val candidate = pool.random()
                numberText?.text = candidate.number.toString()
                nameText?.text = candidate.name
                if (currentSettings.showEnglishName && candidate.englishName.isNotBlank()) {
                    engText?.text = candidate.englishName
                    engText?.visibility = View.VISIBLE
                } else {
                    engText?.visibility = View.GONE
                }

                val stepDelay = 60L + (step * step * 1.5f).toLong()
                delay(stepDelay)
            }

            numberText?.text = success.student.number.toString()
            nameText?.text = success.student.name
            if (currentSettings.showEnglishName && success.student.englishName.isNotBlank()) {
                engText?.text = success.student.englishName
                engText?.visibility = View.VISIBLE
            } else {
                engText?.visibility = View.GONE
            }

            numberText?.let {
                it.scaleX = 1.3f
                it.scaleY = 1.3f
                it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start()
            }

            scheduleAutoDismiss()
        }
    }

    private fun scheduleAutoDismiss() {
        autoDismissJob?.cancel()
        val durationMs = (currentSettings.resultDurationSeconds * 1000L).coerceIn(1500L, 10000L)
        autoDismissJob = scope.launch {
            delay(durationMs)
            hideResultOverlay()
        }
    }

    private fun createResultCardView(result: DrawResult, cardWidth: Int): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))

            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                setColor(Color.argb(235, 15, 23, 42))
                setStroke(dpToPx(1.5f), Color.argb(160, 99, 102, 241))
            }
            background = cardBg
            elevation = dpToPx(16).toFloat()

            setOnClickListener {
                hideResultOverlay()
            }
        }

        when (result) {
            is DrawResult.Success -> {
                val headerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(16)
                    }
                }

                val iconText = TextView(context).apply {
                    text = "🎲"
                    textSize = 20f
                    setPadding(0, 0, dpToPx(6), 0)
                }
                headerLayout.addView(iconText)

                val titleText = TextView(context).apply {
                    text = "抽籤結果"
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#E2E8F0"))
                }
                headerLayout.addView(titleText)

                if (currentSettings.nonRepeating) {
                    val badge = TextView(context).apply {
                        text = "本輪 ${result.drawnCount}/${result.totalEnabledCount}"
                        textSize = 12f
                        setTextColor(Color.parseColor("#A5B4FC"))
                        setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
                        val badgeBg = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dpToPx(12).toFloat()
                            setColor(Color.argb(120, 49, 46, 129))
                            setStroke(dpToPx(1), Color.argb(100, 129, 140, 248))
                        }
                        background = badgeBg
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            leftMargin = dpToPx(10)
                        }
                    }
                    headerLayout.addView(badge)
                }

                root.addView(headerLayout)

                val numberContainer = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(84),
                        dpToPx(84)
                    ).apply {
                        bottomMargin = dpToPx(14)
                    }
                    val numBg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.argb(140, 30, 27, 75))
                        setStroke(dpToPx(2), Color.parseColor("#818CF8"))
                    }
                    background = numBg
                }

                val numberView = TextView(context).apply {
                    id = VIEW_ID_NUMBER
                    text = result.student.number.toString()
                    textSize = 34f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FFFFFF"))
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                numberContainer.addView(numberView)
                root.addView(numberContainer)

                val nameView = TextView(context).apply {
                    id = VIEW_ID_NAME
                    text = result.student.name
                    textSize = 24f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F8FAFC"))
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                root.addView(nameView)

                val engView = TextView(context).apply {
                    id = VIEW_ID_ENG
                    text = result.student.englishName
                    textSize = 14f
                    setTextColor(Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    visibility = if (currentSettings.showEnglishName && result.student.englishName.isNotBlank()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(4)
                    }
                }
                root.addView(engView)
            }

            is DrawResult.RoundCompleted -> {
                val icon = TextView(context).apply {
                    text = "🎉"
                    textSize = 36f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(10))
                }
                root.addView(icon)

                val title = TextView(context).apply {
                    text = "本輪已全部抽完"
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F8FAFC"))
                    gravity = Gravity.CENTER
                }
                root.addView(title)

                val subtitle = TextView(context).apply {
                    text = "${result.totalEnabledCount} / ${result.totalEnabledCount}"
                    textSize = 15f
                    setTextColor(Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(4), 0, dpToPx(18))
                }
                root.addView(subtitle)

                val resetBtn = Button(context).apply {
                    text = "開始下一輪"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    val btnBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(Color.parseColor("#4F46E5"))
                    }
                    background = btnBg
                    setOnClickListener {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.resetCurrentRound()
                            }
                            triggerDraw()
                        }
                    }
                }
                root.addView(resetBtn)
            }

            DrawResult.NoStudents -> {
                val icon = TextView(context).apply {
                    text = "👥"
                    textSize = 36f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(10))
                }
                root.addView(icon)

                val title = TextView(context).apply {
                    text = "目前沒有可抽籤的學生"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F8FAFC"))
                    gravity = Gravity.CENTER
                }
                root.addView(title)

                val desc = TextView(context).apply {
                    text = "請先新增學生或匯入 CSV"
                    textSize = 14f
                    setTextColor(Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(4), 0, dpToPx(18))
                }
                root.addView(desc)

                val settingsBtn = Button(context).apply {
                    text = "前往設定"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    val btnBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(Color.parseColor("#4F46E5"))
                    }
                    background = btnBg
                    setOnClickListener {
                        hideResultOverlay()
                        openMainActivity()
                    }
                }
                root.addView(settingsBtn)
            }

            DrawResult.NoEnabledStudents -> {
                val icon = TextView(context).apply {
                    text = "⚠️"
                    textSize = 36f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dpToPx(10))
                }
                root.addView(icon)

                val title = TextView(context).apply {
                    text = "目前沒有啟用中的學生"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F8FAFC"))
                    gravity = Gravity.CENTER
                }
                root.addView(title)

                val desc = TextView(context).apply {
                    text = "請前往設定啟用學生名單"
                    textSize = 14f
                    setTextColor(Color.parseColor("#94A3B8"))
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(4), 0, dpToPx(18))
                }
                root.addView(desc)

                val settingsBtn = Button(context).apply {
                    text = "前往設定"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    val btnBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(14).toFloat()
                        setColor(Color.parseColor("#4F46E5"))
                    }
                    background = btnBg
                    setOnClickListener {
                        hideResultOverlay()
                        openMainActivity()
                    }
                }
                root.addView(settingsBtn)
            }
        }

        return root
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun openMainActivity() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun dpToPx(dp: Number): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).roundToInt()
    }

    companion object {
        private const val VIEW_ID_NUMBER = 1001
        private const val VIEW_ID_NAME = 1002
        private const val VIEW_ID_ENG = 1003
    }
}
