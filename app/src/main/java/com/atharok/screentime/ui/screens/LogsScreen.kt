package com.atharok.screentime.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.atharok.screentime.R
import com.atharok.screentime.common.utils.AppLog
import com.atharok.screentime.ui.components.AppScaffold
import com.atharok.screentime.ui.components.NavigateUpAction
import com.atharok.screentime.ui.components.RefreshAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun LogsScreen(
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf(AppLog.LogStats(0, 0L, 0, "-", "-")) }
    var lines by remember { mutableStateOf(emptyList<String>()) }
    var onlyErrors by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    val reload: () -> Unit = {
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                AppLog.stats() to AppLog.readTail(500)
            }
            stats = result.first
            lines = if (result.second.isEmpty()) emptyList() else result.second.lines()
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        AppLog.i("LogsScreen", "Runtime log screen opened; ${AppLog.logFiles().size} log files on disk")
        reload()
    }

    AppScaffold(
        title = stringResource(id = R.string.runtime_logs),
        modifier = modifier,
        navigateUp = {
            NavigateUpAction(navigateUp)
        },
        topBarActions = {
            IconButton(onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { AppLog.clipboardText() }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MyScreenTime logs", text))
                    Toast.makeText(
                        context,
                        context.getString(R.string.logs_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(id = R.string.copy_logs)
                )
            }

            IconButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { AppLog.export() } }
            }) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(id = R.string.export_logs)
                )
            }

            RefreshAction(refresh = reload)
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                LogsInfoCard(
                    stats = stats,
                    onlyErrors = onlyErrors,
                    onOnlyErrorsChange = { onlyErrors = it },
                    onExport = {
                        scope.launch { withContext(Dispatchers.IO) { AppLog.export() } }
                    }
                )
            }

            val shown = if (onlyErrors) {
                lines.filter { it.contains("| ERROR |") || it.contains("| FATAL |") || it.contains("| WARN |") }
            } else {
                lines
            }

            if (shown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            id = if (loading) R.string.logs_loading else R.string.logs_empty
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(id = R.dimen.padding_large))
                    )
                }
            } else {
                items(shown) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = when {
                            line.contains("| FATAL |") || line.contains("| ERROR |") ->
                                MaterialTheme.colorScheme.error
                            line.contains("| WARN |") ->
                                MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = dimensionResource(id = R.dimen.padding_medium),
                                vertical = dimensionResource(id = R.dimen.padding_small)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsInfoCard(
    stats: AppLog.LogStats,
    onlyErrors: Boolean,
    onOnlyErrorsChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val padding = dimensionResource(id = R.dimen.padding_large)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            Text(
                text = stringResource(id = R.string.logs_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_normal)))

            Text(
                text = stringResource(
                    id = R.string.logs_stats,
                    stats.fileCount,
                    stats.firstDay,
                    stats.lastDay,
                    stats.lineCount,
                    String.format(Locale.US, "%.1f", stats.totalBytes / 1024.0)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_normal)))

            Text(
                text = stringResource(id = R.string.logs_retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))

            Text(
                text = stringResource(id = R.string.logs_ai_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_normal)))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.only_errors),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = onlyErrors, onCheckedChange = onOnlyErrorsChange)
            }

            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_normal)))
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.padding_normal)))
                Text(text = stringResource(id = R.string.export_logs))
            }
        }
    }
}
