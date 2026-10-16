package com.dipandhali.hermesnative.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.dipandhali.hermesnative.core.icons.ProviderIconLoader
import com.dipandhali.hermesnative.core.icons.ProviderIcons
import com.dipandhali.hermesnative.ui.theme.HermesMotion
import com.dipandhali.hermesnative.ui.theme.LocalProviderIconVariant
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for the loader. The composable can be created from any
 * surface without threading a loader parameter through it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ProviderIconEntryPoint {
    fun providerIconLoader(): ProviderIconLoader
}

/**
 * A provider logo: the cached LobeHub PNG when it loads, a deterministic
 * monogram otherwise. The two crossfade, and the monogram shows immediately,
 * so a row never flashes an empty gap while artwork is in flight.
 *
 * The monogram colour is stable per provider (three container roles rotated by
 * the icon identity), which gives the list visual rhythm without inventing
 * brand colours.
 */
@Composable
fun ProviderIcon(
    modelId: String?,
    providerId: String?,
    label: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val iconId = ProviderIcons.iconId(modelId, providerId)
    val variant = LocalProviderIconVariant.current
    val loader = rememberProviderIconLoader()

    val image by produceState<ImageBitmap?>(initialValue = null, iconId, variant, loader) {
        value = iconId?.let { loader.load(it, variant) }
    }

    Crossfade(
        targetState = image,
        animationSpec = HermesMotion.spatialFast(),
        label = "provider-icon",
        modifier = modifier,
    ) { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        } else {
            ProviderMonogram(
                text = ProviderIcons.monogramText(modelId, providerId, label),
                seed = iconId ?: modelId ?: providerId ?: label ?: "",
                size = size,
            )
        }
    }
}

@Composable
private fun ProviderMonogram(text: String, seed: String, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when ((seed.hashCode() and Int.MAX_VALUE) % 3) {
        0 -> colors.secondaryContainer to colors.onSecondaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.surfaceVariant to colors.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontWeight = FontWeight.SemiBold,
            fontSize = with(LocalDensity.current) { (size * MONOGRAM_TEXT_RATIO).toSp() },
        )
    }
}

@Composable
private fun rememberProviderIconLoader(): ProviderIconLoader {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProviderIconEntryPoint::class.java,
        ).providerIconLoader()
    }
}

private const val MONOGRAM_TEXT_RATIO = 0.42f
