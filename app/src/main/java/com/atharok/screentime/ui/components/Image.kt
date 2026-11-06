package com.atharok.screentime.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.core.graphics.drawable.toBitmap
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun Image(
    drawable: Drawable,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality
) {
    Image(
        bitmap = drawable.toBitmap().asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality
    )
}

@Composable
fun ApplicationIcon(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    drawable?.let {
        Image(
            drawable = it,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } ?: run {
        Image(
            imageVector = Icons.Rounded.QuestionMark,
            contentDescription = contentDescription,
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(dimensionResource(id = R.dimen.padding_normal)),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
        )
    }
}

/**
 * Icon lookup by package name. The Drawable is fetched once and downscaled to the size actually
 * shown on screen, then reused from [AppIconCache]; no full-size bitmap is created per cell or
 * per recomposition. Prefer this over the Drawable overload in long lists.
 */
@Composable
fun ApplicationIcon(
    packageName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizeDp = dimensionResource(id = R.dimen.icon_large_size)
    val sizePx = remember(density, sizeDp) {
        with(density) { sizeDp.toPx() }.roundToInt()
    }
    // The icon state MUST be keyed by packageName. produceState keeps its result State in a plain
    // remember {} (no keys), so re-running the producer for a different app still holds the OLD
    // app's bitmap; combined with an "if (value == null)" guard it never reloaded, which is why
    // sorting the ranking left the previous app's icon in that cell. Re-creating the state for
    // every packageName makes a cache hit show the correct icon immediately and a miss fall back
    // to the placeholder until the IO load finishes.
    val iconState = remember(packageName, sizePx) {
        mutableStateOf<ImageBitmap?>(AppIconCache.peek(packageName, sizePx))
    }
    LaunchedEffect(packageName, sizePx) {
        if (iconState.value == null) {
            val loaded = withContext(Dispatchers.IO) {
                AppIconCache.get(context, packageName, sizePx)
            }
            iconState.value = loaded
        }
    }
    val resolved = iconState.value
    if (resolved != null) {
        androidx.compose.foundation.Image(
            bitmap = resolved,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        Image(
            imageVector = Icons.Rounded.QuestionMark,
            contentDescription = contentDescription,
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(dimensionResource(id = R.dimen.padding_normal)),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
        )
    }
}