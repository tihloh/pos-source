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
import java.net.URL

@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
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
