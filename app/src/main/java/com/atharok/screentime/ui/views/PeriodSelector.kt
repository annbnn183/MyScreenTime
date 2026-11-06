package com.atharok.screentime.ui.views

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.MultiChoiceSegmentedButtonRowScope
import androidx.compose.material3.SegmentedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import com.atharok.screentime.domain.entities.Period
import com.atharok.screentime.ui.components.TextNormal

@Composable
fun PeriodSelector(
    period: Period,
    onPeriodChange: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    MultiChoiceSegmentedButtonRow(modifier = modifier) {
        PeriodSegmentedButton(
            period = Period.WEEK,
            isSelected = period == Period.WEEK,
            onPeriodChange = onPeriodChange,
            shape = RoundedCornerShape(
                topStartPercent = 50,
                topEndPercent = 0,
                bottomEndPercent = 0,
                bottomStartPercent = 50,
            )
        )

        PeriodSegmentedButton(
            period = Period.DAY,
            isSelected = period == Period.DAY,
            onPeriodChange = onPeriodChange,
            shape = RoundedCornerShape(
                topStartPercent = 0,
                topEndPercent = 50,
                bottomEndPercent = 50,
                bottomStartPercent = 0,
            )
        )
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