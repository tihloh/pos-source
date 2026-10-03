package com.tihloh.pos.product

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object ProductImageStore {
    fun save(context: Context, uri: Uri): String {
        val dir = File(context.filesDir, "product-images").apply { mkdirs() }
        val file = File(dir, "product-${System.currentTimeMillis()}.jpg")

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected image.")

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Unsupported image format.")

        val maxSide = max(bitmap.width, bitmap.height)
        val scale = if (maxSide > 1200) 1200f / maxSide else 1f
        val output = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        FileOutputStream(file).use {
            output.compress(Bitmap.CompressFormat.JPEG, 82, it)
        }

        if (output !== bitmap) output.recycle()
        bitmap.recycle()
        return file.toURI().toString()
    }

    fun fileFromUrl(value: String?): File? {
        if (value.isNullOrBlank() || !value.startsWith("file:")) return null
        return runCatching { File(java.net.URI(value)) }.getOrNull()
            ?.takeIf { it.exists() && it.isFile }
    }
}
