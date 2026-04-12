package com.silenceofthelambda.dergplayer.ui.tui

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt

object TuiUtils {
    private const val ASCII_CHARS = " .:-=+*#%@"

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
                val brightness = (0.2126 * r + 0.7152 * g + 0.0722 * b)
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
