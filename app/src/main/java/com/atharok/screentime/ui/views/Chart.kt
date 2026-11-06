package com.atharok.screentime.ui.views

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atharok.screentime.common.utils.DateTimeUtils
import com.atharok.screentime.domain.entities.Period
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.vicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlin.math.floor

@Composable
fun Chart(
    period: Period,
    data: Map<String, Float>,
    average: Double,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {

    val bottomAxisLabelListKey = remember { ExtraStore.Key<List<String>>() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnModel { series(data.values) }
            extras {
                it[bottomAxisLabelListKey] = data.keys.toList()
            }
        }
    }

    StatelessChart(
        data = data,
        average = average,
        bottomAxis = when(period) {
            Period.WEEK -> {
                rememberBottomAxisDefault(
                    valueFormatter = remember {{ cartesianMeasuringContext, x, _ ->
                        val xInt: Int = x.toInt()
                        cartesianMeasuringContext.model.extraStore.getOrNull(bottomAxisLabelListKey)?.getOrNull(xInt) ?: "$xInt"
                    }},
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(addExtremeLabelPadding = true)
                )
            }
            Period.DAY -> {
                rememberBottomAxisDefault(
                    valueFormatter = remember{{ cartesianMeasuringContext, x, _  ->
                        val xInt: Int = x.toInt()
                        if(xInt % 4 == 0) cartesianMeasuringContext.model.extraStore.getOrNull(bottomAxisLabelListKey)?.getOrNull(xInt) ?: "$xInt" else "$xInt"
                    }},
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { 4 }, addExtremeLabelPadding = true)
                )
            }
        },
        modifier = modifier,
        modelProducer = modelProducer
    )
}

@Composable
private fun StatelessChart(
    data: Map<String, Float>,
    average: Double,
    bottomAxis: Axis<Axis.Position.Horizontal.Bottom>,
    modifier: Modifier,
    modelProducer: CartesianChartModelProducer
) {
    ProvideVicoTheme(theme = rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        vicoTheme.columnCartesianLayerColors.map { color ->
                            rememberLineComponent(
                                fill = Fill(color),
                                thickness = 4.dp,
                                shape = CircleShape
                            )
                        }
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    line = rememberAxisLineComponentDefault(),
                    label = rememberAxisLabelComponentDefault(),
                    tick = rememberAxisTickGuidelineComponentDefault(),
                    guideline = rememberAxisGuidelineComponentDefault(),
                    valueFormatter = remember {
                        { _, y: Double, _ ->
                            DateTimeUtils.formatToHoursMinutes(y.toFloat())
                        }
                    },
                    itemPlacer = VerticalAxis.ItemPlacer.step(
                        step = {
                            val max = data.values.max()
                            (if(max >= 1f) 0.25f + floor(max) * 0.25f else 1f / 60f).toDouble()
                        }
                    )
                ),
                bottomAxis = bottomAxis,

                // Average
                decorations = listOf(
                    HorizontalLine(
                        y = {
                            //(data.values.sum() / data.values.size).toDouble()
                            average
                        },
                        line = LineComponent(
                            fill = Fill(MaterialTheme.colorScheme.tertiary),
                            shape = CircleShape
                        )
                    )
                )
            ),
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = rememberVicoScrollState(false),
        )
    }
}

// ---- Axis ----

@Composable
private fun rememberBottomAxisDefault(
    valueFormatter: CartesianValueFormatter = remember { CartesianValueFormatter.decimal() },
    itemPlacer: HorizontalAxis.ItemPlacer = remember { HorizontalAxis.ItemPlacer.aligned() },
): HorizontalAxis<Axis.Position.Horizontal.Bottom> = HorizontalAxis.rememberBottom(
    line = rememberAxisLineComponentDefault(),
    label = rememberAxisLabelComponentDefault(),
    tick = rememberAxisTickGuidelineComponentDefault(),
    guideline = rememberAxisGuidelineComponentDefault(),
    valueFormatter = valueFormatter,
    itemPlacer = itemPlacer
)

@Composable
private fun rememberAxisLabelComponentDefault(): TextComponent = rememberAxisLabelComponent(
    style = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
)

@Composable
private fun rememberAxisLineComponentDefault(): LineComponent = rememberAxisLineComponent(
    fill = Fill(MaterialTheme.colorScheme.outline)
)

@Composable
private fun rememberAxisTickGuidelineComponentDefault(): LineComponent = rememberAxisGuidelineComponent(
    fill = Fill(MaterialTheme.colorScheme.outline)
)

@Composable
private fun rememberAxisGuidelineComponentDefault(): LineComponent = rememberAxisGuidelineComponent(
    fill = Fill(MaterialTheme.colorScheme.outlineVariant)
)