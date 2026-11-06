package com.atharok.screentime.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.atharok.screentime.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateModalBottomSheet(
    title: String,
    icon: ImageVector,
    iconDescription: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        contentWindowInsets = {
            WindowInsets(0, 0, 0, 0)
        }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    end = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                )
        ) {
            Row(
                modifier = Modifier.padding(bottom = dimensionResource(id = R.dimen.padding_large)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    imageVector = icon,
                    contentDescription = iconDescription,
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_medium_size)),
                    colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.onSurface)
                )
                TextLarge(
                    text = title,
                    modifier = Modifier.padding(start = dimensionResource(id = R.dimen.padding_medium))
                )
            }
            content()
        }
    }
}

@Composable
fun ScreenTimeCalculationMethodModalBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TemplateModalBottomSheet(
        title = title,
        icon = Icons.Outlined.Info,
        iconDescription = null,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Section(
            title = stringResource(id = R.string.screen_time_calculation_method_part_1_title),
            message = stringResource(id = R.string.screen_time_calculation_method_part_1_details),
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )

        Section(
            title = stringResource(id = R.string.screen_time_calculation_method_part_2_title),
            message = stringResource(id = R.string.screen_time_calculation_method_part_2_details),
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )

        Section(
            title = stringResource(id = R.string.screen_time_calculation_method_part_3_title),
            message = stringResource(id = R.string.screen_time_calculation_method_part_3_details),
            modifier = Modifier.padding(top = dimensionResource(id = R.dimen.padding_medium))
        )
    }
}

@Composable
private fun Section(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TextMedium(
            text = title
        )
        TextNormalSecondary(
            text = message,
            modifier = Modifier.padding(vertical = dimensionResource(id = R.dimen.padding_large))
        )
    }
}