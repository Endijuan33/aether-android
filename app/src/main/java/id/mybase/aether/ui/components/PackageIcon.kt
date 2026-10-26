package id.mybase.aether.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.mybase.aether.di.AppContainer

/**
 * Fixed-size decorative identity next to the accessible app name. Never queries PM in composition.
 */
@Composable
fun PackageIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val provider = remember(context.applicationContext) { AppContainer.get(context).icons }
    val generation by provider.generation.collectAsStateWithLifecycle()
    val drawable by
        produceState<Drawable?>(null, provider, packageName, generation) {
            value = provider.load(packageName)
        }
    val painter = remember(drawable) { drawable?.let(::NativeIconPainter) }
    if (painter == null) Icon(Icons.Default.Android, null, modifier.size(40.dp))
    else Image(painter, null, modifier.size(40.dp))
}

private class NativeIconPainter(private val drawable: Drawable) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}
