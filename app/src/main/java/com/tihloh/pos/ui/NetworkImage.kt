package com.tihloh.pos.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL

@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val decoded = when {
                    url.startsWith("file:") -> {
                        val file = File(URI(url))
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    else -> URL(url).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                decoded?.asImageBitmap()
            }.getOrNull()
        }
    }

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Product image",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}
