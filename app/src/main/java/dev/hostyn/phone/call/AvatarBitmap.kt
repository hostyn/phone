package dev.hostyn.phone.call

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import dev.hostyn.phone.R
import dev.hostyn.phone.ui.components.avatarColors
import dev.hostyn.phone.ui.components.initialOf
import dev.hostyn.phone.ui.theme.appColorScheme

/** Avatar with the initial and app colors, for the call notification. */
fun avatarBitmap(context: Context, call: CallUi, size: Int = 192): Bitmap {
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val (bg, fg) = avatarColors(appColorScheme(context, dark), call.contactId ?: call.number.hashCode().toLong())
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.toArgb() })

    val initial = if (call.contactId != null || call.name.any { it.isLetter() }) initialOf(call.name) else "#"
    if (initial.first().isLetter()) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg.toArgb()
            textSize = size * 0.42f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initial, size / 2f, y, paint)
    } else {
        ContextCompat.getDrawable(context, R.drawable.ic_person)?.apply {
            setTint(fg.toArgb())
            val inset = (size * 0.22f).toInt()
            setBounds(inset, inset, size - inset, size - inset)
            draw(canvas)
        }
    }
    return bitmap
}
