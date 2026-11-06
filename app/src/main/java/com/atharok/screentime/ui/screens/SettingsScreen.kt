package com.atharok.screentime.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.screentime.R
import com.atharok.screentime.common.extensions.getAppVersion
import com.atharok.screentime.common.utils.SOURCE_CODE_LINK
import com.atharok.screentime.common.utils.WEB_SITE_LINK
import com.atharok.screentime.common.utils.isDynamicColorsAvailable
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.domain.entities.ThemeEntity
import com.atharok.screentime.presentation.viewmodel.SettingsViewModel
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.ListDialog
import com.atharok.screentime.ui.components.NavigateUpAction
import com.atharok.screentime.ui.components.ScreenTimeCalculationMethodModalBottomSheet
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextNormalSecondary
import com.atharok.screentime.ui.views.PeriodSelector

@Composable
fun SettingsScreen(
    navigateUp: () -> Unit,
    openIgnoredAppsScreen: () -> Unit,
    openThirdLibrariesScreen: () -> Unit,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = stringResource(id = R.string.settings),
        modifier = modifier,
        navigateUp = {
            NavigateUpAction(navigateUp)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {

            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val horizontalPadding = dimensionResource(id = R.dimen.padding_large)
            val verticalPadding = dimensionResource(id = R.dimen.padding_medium)

            // Appearance
            val theme: ThemeEntity by settingsViewModel.theme.collectAsStateWithLifecycle(initialValue = ThemeEntity.SYSTEM)
            val useBlackColorForDarkTheme: Boolean by settingsViewModel.useBlackColorForDarkTheme.collectAsStateWithLifecycle(initialValue = false)
            // Display
            val defaultPeriod: Period by settingsViewModel.defaultPeriod.collectAsStateWithLifecycle(initialValue = Period.DAY)
            val ignoreSystemApps: Boolean by settingsViewModel.ignoreSystemApps.collectAsStateWithLifecycle(initialValue = false)

            // ---- Appearance ----

            SettingsTitle(
                text = stringResource(id = R.string.appearance),
                icon = Icons.Rounded.Palette,
                iconDescription = stringResource(id = R.string.appearance),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsListDialog(
                title = R.string.theme,
                dialogMessage = null,
                value = theme,
                onValueChange = { settingsViewModel.changeTheme(it) },
                items = ThemeEntity.entries,
                convertValueToString = { context.getString(it.stringRes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsSwitch(
                primaryText = stringResource(id = R.string.theme_black),
                secondaryText = stringResource(id = R.string.theme_black_oled_info),
                checked = useBlackColorForDarkTheme,
                onCheckedChange = { settingsViewModel.setUseBlackColorForDarkTheme(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            if(isDynamicColorsAvailable()) {
                val useDynamicColors: Boolean by settingsViewModel.useDynamicColors.collectAsStateWithLifecycle(initialValue = true)
                SettingsSwitch(
                    primaryText = stringResource(id = R.string.dynamic_colors),
                    secondaryText = null,
                    checked = useDynamicColors,
                    onCheckedChange = { settingsViewModel.setUseDynamicColors(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = horizontalPadding,
                            vertical = verticalPadding
                        )
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = verticalPadding)
            )

            // ---- Display ----

            SettingsTitle(
                text = stringResource(id = R.string.display),
                icon = Icons.Rounded.DisplaySettings,
                iconDescription = stringResource(id = R.string.display),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsDefaultPeriodSelector(
                defaultPeriod = defaultPeriod,
                onDefaultPeriodChange = { settingsViewModel.saveDefaultPeriod(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsSwitch(
                primaryText = stringResource(id = R.string.ignore_system_apps),
                secondaryText = null,
                checked = ignoreSystemApps,
                onCheckedChange = { settingsViewModel.saveIgnoreSystemApps(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsText(
                text = stringResource(id = R.string.manage_apps_to_ignore),
                modifier = Modifier
                    .clickable {
                        openIgnoredAppsScreen()
                    }
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = verticalPadding)
            )

            // ---- About ----

            SettingsTitle(
                text = stringResource(id = R.string.about),
                icon = Icons.Outlined.Info,
                iconDescription = stringResource(id = R.string.about),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsScreenTimeCalculationMethodModalBottomSheet(
                title = R.string.screen_time_calculation_method,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val activity = LocalActivity.current
                SettingsText(
                    text = stringResource(id = R.string.language),
                    modifier = Modifier
                        .clickable {
                            activity?.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    Uri.fromParts("package", activity.packageName, null)
                                )
                            )
                        }
                        .fillMaxWidth()
                        .padding(
                            horizontal = horizontalPadding,
                            vertical = verticalPadding
                        )
                )
            }

            SettingsText(
                text = stringResource(id = R.string.third_party_library),
                modifier = Modifier
                    .clickable {
                        openThirdLibrariesScreen()
                    }
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsText(
                text = stringResource(id = R.string.website),
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri(WEB_SITE_LINK)
                    }
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            Column(
                modifier = Modifier
                    .clickable {
                        uriHandler.openUri(SOURCE_CODE_LINK)
                    }
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            ) {
                TextNormal(text = stringResource(id = R.string.source_code))
                TextNormalSecondary(text = stringResource(id = R.string.code_version, context.getAppVersion()))
            }
        }
    }
}

@Composable
private fun SettingsDefaultPeriodSelector(
    defaultPeriod: Period,
    onDefaultPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        TextNormal(
            text = stringResource(id = R.string.default_period),
            modifier = Modifier.fillMaxSize()
        )

        TextNormalSecondary(
            text = stringResource(id = defaultPeriod.stringRes),
            modifier = Modifier.fillMaxSize().padding(bottom = dimensionResource(R.dimen.padding_small))
        )

        PeriodSelector(
            period = defaultPeriod,
            onPeriodChange = onDefaultPeriodChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// --- Reusable components ----

@Composable
private fun <T> SettingsListDialog(
    @StringRes title: Int,
    dialogMessage: String?,
    value: T,
    onValueChange: (T) -> Unit,
    items: List<T>,
    convertValueToString: (T) -> String,
    modifier: Modifier = Modifier
) {
    var isShowingDialog by remember { mutableStateOf(false) }

    StatelessSettingsListDialog(
        value = value,
        onValueChange = onValueChange,
        items = items,
        convertValueToString = convertValueToString,
        showDialog = isShowingDialog,
        onShowDialogChange = { isShowingDialog = it },
        title = title,
        dialogMessage = dialogMessage,
        modifier = modifier
    )
}

@Composable
private fun <T> StatelessSettingsListDialog(
    value: T,
    onValueChange: (T) -> Unit,
    items: List<T>,
    convertValueToString: (T) -> String,
    showDialog: Boolean,
    onShowDialogChange: (Boolean) -> Unit,
    @StringRes title: Int,
    dialogMessage: String?,
    modifier: Modifier = Modifier
) {
    if(showDialog) {
        ListDialog(
            confirmButtonText = stringResource(android.R.string.ok),
            dismissButtonText = stringResource(android.R.string.cancel),
            onConfirmation = { index ->
                onValueChange(items[index])
                onShowDialogChange(false)
            },
            onDismissRequest = { onShowDialogChange(false) },
            dialogTitle = stringResource(title),
            dialogMessage = dialogMessage,
            items = items.map { convertValueToString(it) },
            defaultItemIndex = items.indexOf(value)
        )
    }

    Column(
        modifier = Modifier
            .clickable { onShowDialogChange(true) }
            .then(modifier)
    ) {
        TextNormal(text = stringResource(id = title))
        TextNormalSecondary(text = convertValueToString(value))
    }
}

@Composable
private fun SettingsScreenTimeCalculationMethodModalBottomSheet(
    @StringRes title: Int,
    modifier: Modifier = Modifier
) {
    var isShowingModalBottomSheet by remember { mutableStateOf(false) }

    if(isShowingModalBottomSheet) {
        ScreenTimeCalculationMethodModalBottomSheet(
            title = stringResource(id = title),
            onDismissRequest = { isShowingModalBottomSheet = false }
        )
    }

    SettingsText(
        text = stringResource(id = title),
        modifier = Modifier
            .clickable { isShowingModalBottomSheet = true }
            .then(modifier)
    )
}

@Composable
private fun SettingsSwitch(
    primaryText: String,
    secondaryText: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .then(modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            TextNormal(text = primaryText)
            secondaryText?.let {
                TextNormalSecondary(text = it)
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = null//onCheckedChange
        )
    }
}

@Composable
private fun SettingsTitle(
    text: String,
    icon: ImageVector,
    iconDescription: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = icon,
            contentDescription = iconDescription,
            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_normal_size)),
            colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.secondary)
        )
        TextNormal(
            text = text,
            modifier = Modifier.padding(start = dimensionResource(id = R.dimen.padding_medium)),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun SettingsText(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        TextNormal(
            text = text,
            modifier = Modifier.fillMaxSize()
        )
    }
}