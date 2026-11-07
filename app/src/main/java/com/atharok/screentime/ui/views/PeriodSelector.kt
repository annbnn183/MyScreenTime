package com.atharok.screentime.ui.views

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.MultiChoiceSegmentedButtonRowScope
import androidx.compose.material3.SegmentedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.ui.components.TextNormal
import kotlin.math.abs

@Composable
fun PeriodSelector(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = Period.entries
    val swipeThreshold = with(LocalDensity.current) { 40.dp.toPx() }

    MultiChoiceSegmentedButtonRow(
        modifier = modifier.pointerInput(period, swipeThreshold) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    dragDistance += dragAmount
                    change.consume()
                },
                onDragEnd = {
                    if (abs(dragDistance) >= swipeThreshold) {
                        val direction = if (dragDistance < 0f) 1 else -1
                        val nextIndex = (period.ordinal + direction)
                        if (nextIndex in entries.indices) {
                            onPeriodChange(entries[nextIndex])
                        }
                    }
                    dragDistance = 0f
                },
                onDragCancel = { dragDistance = 0f }
            )
        }
    ) {
        entries.forEachIndexed { index, p ->
            val shape = when (index) {
                0 -> RoundedCornerShape(
                    topStartPercent = 50, topEndPercent = 0,
                    bottomEndPercent = 0, bottomStartPercent = 50
                )
                entries.lastIndex -> RoundedCornerShape(
                    topStartPercent = 0, topEndPercent = 50,
                    bottomEndPercent = 50, bottomStartPercent = 0
                )
                else -> RoundedCornerShape(0)
            }
            PeriodSegmentedButton(
                period = p,
                isSelected = period == p,
                onPeriodChange = onPeriodChange,
                shape = shape
            )
        }
    }
}

@Composable
private fun MultiChoiceSegmentedButtonRowScope.PeriodSegmentedButton(
    period: Period,
    isSelected: Boolean,
    onPeriodChange: (Period) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    SegmentedButton(
        checked = isSelected,
        onCheckedChange = { onPeriodChange(period) },
        shape = shape,
        modifier = modifier
    ) {
        TextNormal(text = stringResource(id = period.stringRes))
    }
}
