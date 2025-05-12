package ios.silv.gemclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

object TerminalSectionDefaults {
    val horizontalPadding = 4.dp

    @Composable
    fun Label(
        text: String,
        color: Color = MaterialTheme.colorScheme.surface
    ) {
        Surface(
            color = color,
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

private class TerminalLayoutMeasurePolicy(
    val labelOffset: DpOffset,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {

        assert(measurables.size == 3) { "to many measurables passed to terminal layout" }

        val contentMeasurable = measurables[0]
        val labelMeasurable = measurables[1]
        val wrapperMeasurable = measurables[2]

        val labelPlaceable = labelMeasurable.measure(
            constraints.copy(
                minHeight = 0,
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                maxHeight = labelMeasurable.minIntrinsicHeight(constraints.maxWidth)
            )
        )

        val availableHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            // fallback for unbounded height: size to content
            val contentPlaceable = contentMeasurable.measure(
                constraints.copy(minHeight = 0)
            )
            contentPlaceable.height
        }


        val availableWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            maxOf(
                contentMeasurable.minIntrinsicWidth(
                    availableHeight - labelPlaceable.height - (labelPlaceable.height / 2)
                ),
                labelPlaceable.width + (labelOffset.x.roundToPx() * 2)
            )
        }

        val contentPlaceable = contentMeasurable.measure(
            constraints.copy(
                minHeight = 0,
                maxHeight = minOf(
                    availableHeight - labelPlaceable.height - (labelPlaceable.height / 2),
                    constraints.maxHeight
                ).coerceAtLeast(0),
                minWidth = 0,
                maxWidth = availableWidth.coerceAtLeast(0)
            )
        )

        val layoutHeight =
            minOf(
                labelPlaceable.height + contentPlaceable.height + labelPlaceable.height / 2,
                constraints.maxHeight
            )

        val wrapperPlaceable = wrapperMeasurable.measure(
            constraints.copy(
                minHeight = 0,
                maxHeight = (layoutHeight - labelPlaceable.height / 2).coerceAtLeast(0),
                minWidth = 0,
                maxWidth = availableWidth.coerceAtLeast(0)
            )
        )

        return layout(availableWidth, layoutHeight) {
            wrapperPlaceable.place(0, labelPlaceable.height / 2)
            labelPlaceable.place(
                labelOffset.x.roundToPx(), labelOffset.y.roundToPx()
            )
            contentPlaceable.place(
                ((availableWidth - contentPlaceable.width) / 2),
                labelPlaceable.height
            )
        }
    }
}

@Composable
fun TerminalSectionButton(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.background,
    labelOffset: DpOffset = DpOffset(12.dp, 0.dp),
    content: @Composable () -> Unit
) {
    val cc = MaterialTheme.colorScheme.contentColorFor(containerColor)
    Layout(
        modifier = modifier,
        content = {
            CompositionLocalProvider(
                LocalContentColor provides cc
            ) {
                content()
            }
            label()
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, borderColor)
                    .background(containerColor)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button
                    ) {
                        onClick()
                    }
            )
        },
        measurePolicy = remember(labelOffset) {
            TerminalLayoutMeasurePolicy(labelOffset)
        }
    )
}

@Composable
fun TerminalSection(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    labelOffset: DpOffset = DpOffset(12.dp, 0.dp),
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            content()
            label()
            Box(Modifier.fillMaxSize().border(1.dp, borderColor))
        },
        measurePolicy = remember(labelOffset) {
            TerminalLayoutMeasurePolicy(labelOffset)
        }
    )
}
