package com.uzukip.gokapi.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.uzukip.gokapi.data.settings.Settings
import com.uzukip.gokapi.data.settings.VibrationStrength

/** Settings-driven haptics, copied from spendr-kt. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun vibrate(durationMs: Int) {
        val v = vibrator ?: return
        if (durationMs <= 0 || !v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

val Settings.vibrationMs: Int
    get() = when (vibrationStrength) {
        VibrationStrength.OFF -> 0
        VibrationStrength.LIGHT -> 12
        VibrationStrength.DEFAULT -> 25
        VibrationStrength.STRONG -> 50
        VibrationStrength.CUSTOM -> customVibrationMs
    }
