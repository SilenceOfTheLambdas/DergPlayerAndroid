package com.silenceofthelambda.dergplayer.ui.tui

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

object TuiUtils {
    private const val ASCII_CHARS = " .':-^=+*!&%$@#"

    fun bitmapToAscii(bitmap: Bitmap, width: Int, height: Int): String {
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val sb = StringBuilder()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = resized.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Using relative luminance
                var brightness = (0.2126 * r + 0.7152 * g + 0.0722 * b)
                
                // Contrast enhancement for TUI
                val contrast = 1.3
                brightness = (brightness - 128) * contrast + 128
                brightness = brightness.coerceIn(0.0, 255.0)

                val charIndex = ((brightness / 255.0) * (ASCII_CHARS.length - 1)).roundToInt()
                sb.append(ASCII_CHARS[charIndex])
            }
            if (y < height - 1) {
                sb.append("\n")
            }
        }
        return sb.toString()
    }
}
