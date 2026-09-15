package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.data.StudentRepository
import com.example.service.FloatingDrawService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val repository = StudentRepository.getInstance(context)
                val settings = repository.settingsManager.getSettings()

                // Check if user enabled boot auto-start and floating was active
                if ((settings.bootAutoStart || settings.floatingEnabled) &&
                    Settings.canDrawOverlays(context)
                ) {
                    FloatingDrawService.start(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
