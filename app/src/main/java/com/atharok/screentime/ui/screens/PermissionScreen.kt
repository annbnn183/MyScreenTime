package com.atharok.screentime.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import com.atharok.screentime.R
import com.atharok.screentime.common.extensions.getActivity
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.MaterialButton
import com.atharok.screentime.ui.components.OnLifecycleEvent
import com.atharok.screentime.ui.components.SettingsAction
import com.atharok.screentime.ui.components.TextMedium
import com.atharok.screentime.ui.components.TextNormalSecondary

@Composable
fun PermissionScreen(
    isAppUsageAccessAllowed: () -> Boolean,
    openDeviceUsageScreen: () -> Unit,
    openSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    StatefulPermissionScreen(
        isAppUsageAccessAllowed = isAppUsageAccessAllowed,
        openDeviceUsageScreen = openDeviceUsageScreen,
    ) { context ->
        StatelessPermissionScreen(
            context = context,
            openSettingsScreen = openSettingsScreen,
            modifier = modifier
        )
    }
}

@Composable
private fun StatefulPermissionScreen(
    isAppUsageAccessAllowed: () -> Boolean,
    openDeviceUsageScreen: () -> Unit,
    content: @Composable (Context) -> Unit
) {
    val context = LocalContext.current

    OnLifecycleEvent { _, event ->
        if(event == Lifecycle.Event.ON_RESUME) {
            if(isAppUsageAccessAllowed()) {
                openDeviceUsageScreen()
            }
        }
    }

    content(context)
}

@Composable
private fun StatelessPermissionScreen(
    context: Context,
    openSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    ActivationScreen(
        topBarTitle = stringResource(id = R.string.permission),
        image = Icons.Rounded.Lock,
        title = stringResource(id = R.string.access_to_usage_data),
        message = stringResource(id = R.string.access_to_usage_data_message),
        buttonIcon = Icons.Rounded.Key,
        buttonText = stringResource(id = R.string.authorize),
        buttonOnClick = {
            openUsageAccessSettings(context)
        },
        openSettingsScreen = openSettingsScreen,
        modifier = modifier
    )
}

@Composable
private fun ActivationScreen(
    topBarTitle: String,
    image: ImageVector,
    title: String,
    message: String,
    buttonIcon: ImageVector,
    buttonText: String,
    buttonOnClick: () -> Unit,
    openSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = topBarTitle,
        modifier = modifier,
        topBarActions = {
            SettingsAction(openSettingsScreen)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = dimensionResource(id = R.dimen.padding_large),
                        vertical = dimensionResource(id = R.dimen.padding_small)
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding( // Permet de centrer verticalement, indépendamment du inner Padding.
                            top = innerPadding.calculateBottomPadding() / 2f,
                            bottom = innerPadding.calculateTopPadding() / 2f
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        imageVector = image,
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .padding(vertical = dimensionResource(id = R.dimen.padding_normal))
                            .aspectRatio(1f),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )

                    TextMedium(
                        text = title,
                        Modifier.padding(vertical = dimensionResource(id = R.dimen.padding_normal)),
                        textAlign = TextAlign.Center
                    )

                    TextNormalSecondary(
                        text = message,
                        textAlign = TextAlign.Center
                    )
                }

                MaterialButton(
                    onClick = buttonOnClick,
                    modifier = Modifier.fillMaxWidth(),
                    text = buttonText,
                    icon = buttonIcon,
                )
            }
        }
    }
}

private fun openUsageAccessSettings(context: Context) {
    try {
        val intent = Intent(ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
        context.getActivity()?.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(ACTION_USAGE_ACCESS_SETTINGS)
        context.getActivity()?.startActivity(intent)
    }
}