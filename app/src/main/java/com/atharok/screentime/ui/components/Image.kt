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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.core.graphics.drawable.toBitmap
import com.atharok.screentime.R

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