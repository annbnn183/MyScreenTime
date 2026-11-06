package com.atharok.screentime.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.atharok.screentime.R
import com.atharok.screentime.domain.entities.AppInfo
import com.atharok.screentime.domain.resources.Resource
import com.atharok.screentime.presentation.viewmodel.InstalledPackagesViewModel
import com.atharok.screentime.presentation.viewmodel.SettingsViewModel
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.ApplicationIcon
import com.atharok.screentime.ui.components.NavigateUpAction
import com.atharok.screentime.ui.components.TextNormal
import com.atharok.screentime.ui.components.TextNormalSecondary
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel

@Composable
fun IgnoredAppsScreen(
    navigateUp: () -> Unit,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    installedPackagesViewModel: InstalledPackagesViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val installedAppsResource: Resource<List<AppInfo>> by installedPackagesViewModel.installedApps.collectAsState()
    val ignoredPackages: List<String> by settingsViewModel.ignoredPackages.collectAsState(listOf())

    LaunchedEffect(Unit) {
        installedPackagesViewModel.loadInstalledPackages(context, settingsViewModel.ignoreSystemApps.first())
    }

    StatelessIgnoredAppsScreen(
        navigateUp = navigateUp,
        installedAppsResource = installedAppsResource,
        ignoredPackages = ignoredPackages,
        saveIgnoredPackages = {
            settingsViewModel.saveIgnoredPackages(it)
        },
        modifier = modifier
    )
}

@Composable
fun StatelessIgnoredAppsScreen(
    navigateUp: () -> Unit,
    installedAppsResource: Resource<List<AppInfo>>,
    ignoredPackages: List<String>,
    saveIgnoredPackages: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = stringResource(id = R.string.apps_to_ignore),
        modifier = modifier,
        navigateUp = {
            NavigateUpAction(navigateUp)
        },
    ) { innerPadding ->

        when(installedAppsResource) {
            is Resource.Failure -> {
                TextNormal(installedAppsResource.throwable.toString())
            }

            is Resource.Progress -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is Resource.Success -> {

                val installedApps = installedAppsResource.data

                LazyColumn(
                    modifier = Modifier,
                    contentPadding = innerPadding
                ) {
                    items(installedApps) { installedApp ->
                        InstalledAppsItem(
                            appName = installedApp.name,
                            packageName = installedApp.packageName,
                            appIcon = installedApp.icon,
                            ignored = ignoredPackages.contains(installedApp.packageName),
                            onIgnoredChange = { ignored: Boolean ->
                                val updatedList = ignoredPackages.toMutableSet().apply {
                                    val packageName = installedApp.packageName
                                    if (ignored) add(packageName) else remove(packageName)
                                }

                                saveIgnoredPackages(updatedList.toList())
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(dimensionResource(id = R.dimen.padding_large))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppsItem(
    appName: String,
    packageName: String,
    appIcon: Drawable?,
    ignored: Boolean,
    onIgnoredChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .clickable { onIgnoredChange(!ignored) }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_medium))
    ) {

        ApplicationIcon(
            drawable = appIcon,
            contentDescription = appName,
            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_large_size))
        )

        Column(
            modifier = Modifier.fillMaxSize().weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TextNormal(
                text = appName,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            TextNormalSecondary(text = packageName)
        }

        Switch(
            checked = ignored,
            onCheckedChange = null//onCheckedChange
        )
    }
}