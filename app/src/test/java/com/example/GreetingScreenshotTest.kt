package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.AppSettings
import com.example.data.ClassScheduleManager
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboard_screenshot() {
        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    hasOverlayPermission = true,
                    settings = AppSettings(floatingEnabled = true),
                    scheduleStatus = ClassScheduleManager.PERIODS[0].let { period ->
                        com.example.data.ScheduleStatus.InClass(
                            period = period,
                            elapsedSeconds = 900,
                            totalSeconds = 2700
                        )
                    },
                    totalCount = 30,
                    enabledCount = 30,
                    drawnCount = 12,
                    onToggleFloatingService = {},
                    onTestDraw = {},
                    onResetRound = {},
                    onNavigateToStudents = {},
                    onNavigateToHistory = {},
                    onNavigateToSettings = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard.png")
    }
}
