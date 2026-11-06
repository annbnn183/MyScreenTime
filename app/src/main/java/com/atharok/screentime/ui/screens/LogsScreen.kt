package com.atharok.screentime.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
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

    var stats by remember { mutableStateOf(AppLog.LogStats()) }
    var lines by remember { mutableStateOf(emptyList<String>()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var autoRefresh by remember { mutableStateOf(false) }
    var markText by remember { mutableStateOf("") }
    // The newest events matter most when diagnosing a problem; show them right under the info
    // card instead of forcing a long scroll through the whole permanent history.
    var newestFirst by remember { mutableStateOf(true) }

    val reload: (Boolean) -> Unit = { showLoading ->
        scope.launch {
            if (showLoading) loading = true
            val result = withContext(Dispatchers.IO) {
                // Counting every line of the permanent history is only needed on a full reload;
                // the 3s auto-refresh just reads the newest files so it never slows down over time.
                Pair(if (showLoading) AppLog.stats() else null, AppLog.readRecent(500))
            }
            result.first?.let { stats = it }
            lines = if (result.second.isEmpty()) emptyList() else result.second.lines()
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        AppLog.i("LogsScreen", "Runtime log screen opened; ${AppLog.logFiles().size} log files on disk")
        reload(true)
    }

    // While enabled, keep the tail live without blocking the UI.
    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            delay(3000L)
            reload(false)
        }
    }

    val filterLabels = listOf(
        stringResource(id = R.string.logs_filter_all),
        stringResource(id = R.string.logs_filter_errors),
        stringResource(id = R.string.logs_filter_touch),
        stringResource(id = R.string.logs_filter_query),
        stringResource(id = R.string.logs_filter_system)
    )

    val shown = remember(lines, query, filter) {
        lines.filter { line ->
            val matchQuery = query.isBlank() || line.contains(query, ignoreCase = true)
            val matchFilter = when (filter) {
                1 -> line.contains("| ERROR |") || line.contains("| FATAL |") || line.contains("| WARN |")
                2 -> line.contains("[TOUCH]") || line.contains("[CLICK_TO_FRAME]")
                3 -> line.contains("[QUERY") || line.contains("[AGGREGATE_INTERMEDIATE]")
                4 -> line.contains("| System |") || line.contains("| Lifecycle |") ||
                        line.contains("| Memory |") || line.contains("| ExitInfo |") ||
                        line.contains("| Permission |") || line.contains("[SYS_SAMPLE]")
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    val ordered = remember(shown, newestFirst) {
        if (newestFirst) shown.asReversed() else shown
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

            RefreshAction(refresh = { reload(true) })
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
                    query = query,
                    onQueryChange = { query = it },
                    filter = filter,
                    filterLabels = filterLabels,
                    onFilterChange = { filter = it },
                    autoRefresh = autoRefresh,
                    onAutoRefreshChange = { autoRefresh = it },
                    newestFirst = newestFirst,
                    onOrderChange = { newestFirst = !newestFirst },
                    markText = markText,
                    onMarkTextChange = { markText = it },
                    onMark = {
                        AppLog.mark(markText)
                        markText = ""
                        Toast.makeText(
                            context,
                            context.getString(R.string.logs_mark_done),
                            Toast.LENGTH_SHORT
                        ).show()
                        reload(false)
                    },
                    onExport = {
                        scope.launch { withContext(Dispatchers.IO) { AppLog.export() } }
                    }
                )
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
                items(ordered) { line ->
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
    query: String,
    onQueryChange: (String) -> Unit,
    filter: Int,
    filterLabels: List<String>,
    onFilterChange: (Int) -> Unit,
    autoRefresh: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    newestFirst: Boolean,
    onOrderChange: () -> Unit,
    markText: String,
    onMarkTextChange: (String) -> Unit,
    onMark: () -> Unit,
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

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(text = stringResource(id = R.string.logs_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))

            Row(modifier = Modifier.fillMaxWidth()) {
                filterLabels.forEachIndexed { index, label ->
                    TextButton(
                        onClick = { onFilterChange(index) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (filter == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.logs_order),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onOrderChange) {
                    Text(
                        text = stringResource(
                            id = if (newestFirst) R.string.logs_newest_first
                            else R.string.logs_oldest_first
                        ),
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.logs_auto_refresh),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = autoRefresh, onCheckedChange = onAutoRefreshChange)
            }

            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))
            OutlinedTextField(
                value = markText,
                onValueChange = onMarkTextChange,
                label = { Text(text = stringResource(id = R.string.logs_mark_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.padding_small)))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onMark, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.logs_mark))
                }
                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.padding_normal)))
                Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.padding_small)))
                    Text(text = stringResource(id = R.string.export_logs))
                }
            }
        }
    }
}
