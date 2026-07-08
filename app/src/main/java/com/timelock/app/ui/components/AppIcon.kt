package com.timelock.app.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用图标组件。
 *
 * 从 [PackageManager] 安全获取图标，失败时回退到默认图标。
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }

    val bitmap = remember(packageName) {
        try {
            val drawable = pm.getApplicationIcon(packageName)
            drawable.toBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            pm.defaultActivityIcon.toBitmap()
        }
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "应用图标",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.2f).dp)),
        contentScale = ContentScale.Fit
    )
}

/** 安全地将 [Drawable] 转换为 [Bitmap] */
private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = if (intrinsicWidth > 0) intrinsicWidth else 128
    val height = if (intrinsicHeight > 0) intrinsicHeight else 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
