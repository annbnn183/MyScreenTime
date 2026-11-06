package com.atharok.screentime.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.atharok.screentime.R

// ---- Actions ----

@Composable
private fun TopAppBarAction(
    onClick: () -> Unit,
    image: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = image,
            contentDescription = contentDescription
        )
    }
}

@Composable
fun NavigateUpAction(
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBarAction(
        onClick = navigateUp,
        image = Icons.AutoMirrored.Rounded.ArrowBack,
        contentDescription = stringResource(id = R.string.back),
        modifier = modifier
    )
}

@Composable
fun SettingsAction(
    openSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBarAction(
        onClick = openSettingsScreen,
        image = Icons.Rounded.Settings,
        contentDescription = stringResource(id = R.string.settings),
        modifier = modifier
    )
}

@Composable
fun RefreshAction(
    refresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBarAction(
        onClick = refresh,
        image = Icons.Rounded.Refresh,
        contentDescription = stringResource(id = R.string.refresh),
        modifier = modifier
    )
}