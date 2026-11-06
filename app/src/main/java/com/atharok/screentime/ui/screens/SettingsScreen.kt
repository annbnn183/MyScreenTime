package com.atharok.screentime.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Timer
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
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.common.utils.SOURCE_CODE_LINK
import com.atharok.screentime.common.utils.WEB_SITE_LINK
import com.atharok.screentime.common.utils.isDynamicColorsAvailable
import com.atharok.screentime.domain.entities.DailyGoalSettings
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

import android.widget.Toast
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.atharok.screentime.common.utils.DataBackupManager
import com.atharok.screentime.ui.components.TextSmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    navigateUp: () -> Unit,
    openIgnoredAppsScreen: () -> Unit,
    openThirdLibrariesScreen: () -> Unit,
    openLogsScreen: () -> Unit,
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

            // ---- Daily screen time goal ----

            val dailyGoalSettings: DailyGoalSettings by settingsViewModel.dailyGoalSettings
                .collectAsStateWithLifecycle(initialValue = DailyGoalSettings())

            // Android 13+ requires the notification permission at runtime. The reminder is still
            // saved as enabled when the user denies it; the worker simply skips posting.
            val requestNotificationPermission = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            SettingsTitle(
                text = stringResource(id = R.string.daily_goal),
                icon = Icons.Rounded.Timer,
                iconDescription = stringResource(id = R.string.daily_goal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsSwitch(
                primaryText = stringResource(id = R.string.daily_goal_enable),
                secondaryText = stringResource(id = R.string.daily_goal_enable_info),
                checked = dailyGoalSettings.enabled,
                onCheckedChange = { enabled ->
                    settingsViewModel.saveDailyGoalEnabled(enabled)
                    if(enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsListDialog(
                title = R.string.daily_goal_target,
                dialogMessage = stringResource(id = R.string.daily_goal_target_info),
                value = dailyGoalSettings.goalMinutes,
                onValueChange = { settingsViewModel.saveDailyGoalMinutes(it) },
                items = DailyGoalSettings.GOAL_MINUTES_OPTIONS,
                convertValueToString = { minutes ->
                    DateTimeUtils.formatToDuration(minutes * 60_000L)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsListDialog(
                title = R.string.daily_goal_warning_threshold,
                dialogMessage = stringResource(id = R.string.daily_goal_warning_threshold_info),
                value = dailyGoalSettings.warningPercent,
                onValueChange = { settingsViewModel.saveDailyGoalWarningPercent(it) },
                items = DailyGoalSettings.WARNING_PERCENT_OPTIONS,
                convertValueToString = { percent -> "$percent%" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            TextNormalSecondary(
                text = stringResource(id = R.string.daily_goal_notification_permission_info),
                modifier = Modifier
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

            // ---- Data management (P2) ----

            val autoBackupEnabled: Boolean by settingsViewModel.autoBackupEnabled
                .collectAsStateWithLifecycle(initialValue = false)

            val scope = rememberCoroutineScope()
            val localActivity = LocalActivity.current

            var showDeleteDialog by remember { mutableStateOf(false) }

            // 加密备份口令
            var showBackupPasswordDialog by remember { mutableStateOf(false) }
            var backupPassword by remember { mutableStateOf("") }
            var backupPasswordConfirm by remember { mutableStateOf("") }
            var backupPasswordError by remember { mutableStateOf(false) }

            // 恢复流程
            var pendingRestore by remember { mutableStateOf<String?>(null) }
            var restorePassword by remember { mutableStateOf("") }
            var showRestorePasswordDialog by remember { mutableStateOf(false) }
            var showRestoreModeDialog by remember { mutableStateOf(false) }
            var restoreRunning by remember { mutableStateOf(false) }
            var showPermissionDialog by remember { mutableStateOf(false) }

            // 明文备份：写入后回读比对，确认完整才提示成功
            val backupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val json = DataBackupManager.exportToString(context, null)
                            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                                ?: throw IllegalStateException("openOutputStream null")
                            val readBack = context.contentResolver.openInputStream(uri)
                                ?.use { it.bufferedReader().readText() }
                            readBack == json
                        }.getOrDefault(false)
                    }
                    Toast.makeText(
                        context,
                        if (ok) R.string.backup_saved else R.string.backup_verify_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // 加密备份：口令确认后，再用 SAF 选择保存位置
            val encryptedBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val password = backupPassword
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val json = DataBackupManager.exportToString(context, password)
                            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                                ?: throw IllegalStateException("openOutputStream null")
                            val readBack = context.contentResolver.openInputStream(uri)
                                ?.use { it.bufferedReader().readText() }
                            readBack == json
                        }.getOrDefault(false)
                    }
                    Toast.makeText(
                        context,
                        if (ok) R.string.backup_saved else R.string.backup_verify_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // 恢复：读取文件 -> 若加密先问口令 -> 再选择合并/覆盖
            val restoreLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val raw = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        }.getOrNull()
                    }
                    if (raw.isNullOrBlank()) {
                        Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (DataBackupManager.isTooLarge(raw)) {
                        Toast.makeText(context, R.string.backup_too_large, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    pendingRestore = raw
                    restorePassword = ""
                    if (DataBackupManager.isEncrypted(raw)) {
                        showRestorePasswordDialog = true
                    } else {
                        showRestoreModeDialog = true
                    }
                }
            }

            val performRestore: (DataBackupManager.RestoreMode) -> Unit = { mode ->
                val raw = pendingRestore
                showRestoreModeDialog = false
                if (raw != null) {
                    scope.launch {
                        restoreRunning = true
                        val result = withContext(Dispatchers.IO) {
                            DataBackupManager.restore(
                                context = context,
                                raw = raw,
                                password = restorePassword.ifBlank { null },
                                mode = mode
                            )
                        }
                        restoreRunning = false
                        if (result.success) {
                            Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
                            if (!DataBackupManager.hasUsageAccessPermission(context)) {
                                showPermissionDialog = true
                            }
                        } else {
                            val base = context.getString(
                                R.string.restore_failed_reason,
                                result.reason ?: "error"
                            )
                            val message = if (result.rolledBack) {
                                "$base · ${context.getString(R.string.restore_rolled_back)}"
                            } else {
                                base
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            SettingsTitle(
                text = stringResource(id = R.string.data_management),
                icon = Icons.Rounded.Lock,
                iconDescription = stringResource(id = R.string.data_management),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsAction(
                primaryText = stringResource(id = R.string.backup_data),
                secondaryText = stringResource(id = R.string.backup_data_desc),
                onClick = { backupLauncher.launch("MyScreenTime-backup-${System.currentTimeMillis()}.json") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsAction(
                primaryText = stringResource(id = R.string.backup_data_encrypted),
                secondaryText = stringResource(id = R.string.backup_data_encrypted_desc),
                onClick = {
                    backupPassword = ""
                    backupPasswordConfirm = ""
                    backupPasswordError = false
                    showBackupPasswordDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsAction(
                primaryText = stringResource(id = R.string.restore_data),
                secondaryText = stringResource(id = R.string.restore_data_desc),
                onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsSwitch(
                primaryText = stringResource(id = R.string.auto_backup),
                secondaryText = stringResource(id = R.string.auto_backup_desc),
                checked = autoBackupEnabled,
                onCheckedChange = { settingsViewModel.setAutoBackupEnabled(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            SettingsAction(
                primaryText = stringResource(id = R.string.delete_all_data),
                secondaryText = stringResource(id = R.string.delete_all_data_desc),
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            )

            // 删除确认（删除前会自动留一份安全快照）
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { TextNormal(text = stringResource(id = R.string.delete_confirm_title)) },
                    text = { TextNormal(text = stringResource(id = R.string.delete_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        DataBackupManager.clearAllData(context)
                                    }
                                    Toast.makeText(context, R.string.data_deleted, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            TextNormal(text = stringResource(id = R.string.delete_confirm_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            TextNormal(text = stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            // 加密备份：设置口令
            if (showBackupPasswordDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupPasswordDialog = false },
                    title = { TextNormal(text = stringResource(id = R.string.backup_password_title)) },
                    text = {
                        Column {
                            TextNormalSecondary(text = stringResource(id = R.string.backup_password_message))
                            OutlinedTextField(
                                value = backupPassword,
                                onValueChange = { backupPassword = it; backupPasswordError = false },
                                singleLine = true,
                                label = { TextNormal(text = stringResource(id = R.string.password_hint)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = backupPasswordConfirm,
                                onValueChange = { backupPasswordConfirm = it; backupPasswordError = false },
                                singleLine = true,
                                label = { TextNormal(text = stringResource(id = R.string.password_confirm_hint)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (backupPasswordError) {
                                TextSmall(text = stringResource(id = R.string.password_mismatch))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (backupPassword.length >= 4 && backupPassword == backupPasswordConfirm) {
                                    showBackupPasswordDialog = false
                                    encryptedBackupLauncher.launch("MyScreenTime-backup-${System.currentTimeMillis()}.json")
                                } else {
                                    backupPasswordError = true
                                }
                            }
                        ) {
                            TextNormal(text = stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackupPasswordDialog = false }) {
                            TextNormal(text = stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            // 恢复：输入备份口令
            if (showRestorePasswordDialog) {
                AlertDialog(
                    onDismissRequest = { showRestorePasswordDialog = false },
                    title = { TextNormal(text = stringResource(id = R.string.restore_password_title)) },
                    text = {
                        Column {
                            TextNormalSecondary(text = stringResource(id = R.string.restore_password_message))
                            OutlinedTextField(
                                value = restorePassword,
                                onValueChange = { restorePassword = it },
                                singleLine = true,
                                label = { TextNormal(text = stringResource(id = R.string.password_hint)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRestorePasswordDialog = false
                                showRestoreModeDialog = true
                            }
                        ) {
                            TextNormal(text = stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestorePasswordDialog = false }) {
                            TextNormal(text = stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            // 恢复：选择合并 / 覆盖
            if (showRestoreModeDialog) {
                AlertDialog(
                    onDismissRequest = { showRestoreModeDialog = false },
                    title = { TextNormal(text = stringResource(id = R.string.restore_options_title)) },
                    text = {
                        Column {
                            TextNormalSecondary(text = stringResource(id = R.string.restore_replace_warning))
                            if (restoreRunning) {
                                TextSmall(text = stringResource(id = R.string.restore_running))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { performRestore(DataBackupManager.RestoreMode.MERGE) }) {
                            TextNormal(text = stringResource(id = R.string.restore_merge))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { performRestore(DataBackupManager.RestoreMode.OVERWRITE) }) {
                            TextNormal(text = stringResource(id = R.string.restore_replace))
                        }
                    }
                )
            }

            // 恢复后：使用情况访问权限需要重新授予
            if (showPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionDialog = false },
                    title = { TextNormal(text = stringResource(id = R.string.permission_needed_title)) },
                    text = { TextNormal(text = stringResource(id = R.string.permission_needed_after_restore)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showPermissionDialog = false
                                localActivity?.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        ) {
                            TextNormal(text = stringResource(id = R.string.grant))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionDialog = false }) {
                            TextNormal(text = stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding
                    )
            ) {
                TextNormal(text = stringResource(id = R.string.maintainer_notice_title))
                TextNormalSecondary(text = stringResource(id = R.string.maintainer_notice_message))
                TextNormalSecondary(text = stringResource(id = R.string.maintainer_contact))
            }

            SettingsText(
                text = stringResource(id = R.string.runtime_logs),
                modifier = Modifier
                    .clickable { openLogsScreen() }
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

@Composable
private fun SettingsAction(
    primaryText: String,
    secondaryText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .then(modifier)
    ) {
        TextNormal(text = primaryText)
        secondaryText?.let {
            TextNormalSecondary(text = it)
        }
    }
}