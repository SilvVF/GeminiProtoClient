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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

object TerminalSectionDefaults {
    val horizontalPadding = 4.dp

    @Composable
    fun Label(
        text: String,
        style: TextStyle = MaterialTheme.typography.labelLarge,
        color: Color = MaterialTheme.colorScheme.background
    ) {
        Surface(
            color = color,
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 2.dp),
                style = style
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
        require(measurables.size == 3) {
            "Expected exactly 3 measurables: content, label, wrapper"
        }

        val contentMeasurable = measurables[0]
        val labelMeasurable = measurables[1]
        val wrapperMeasurable = measurables[2]

        val labelPlaceable = labelMeasurable.measure(
            constraints.copy(
                minWidth = labelMeasurable.minIntrinsicWidth(constraints.maxHeight)
            )
        )
        val availableContentHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - (labelPlaceable.height + labelPlaceable.height / 2)).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }

        val availableContentWidth = if (constraints.hasBoundedWidth) {
            // -1 dp for border
            (constraints.maxWidth).coerceAtLeast(0) - 1.dp.roundToPx()
        } else {
            Constraints.Infinity
        }


        val contentConstraints = constraints.copy(
            minWidth = 0,
            maxWidth = availableContentWidth,
            minHeight = 0,
            maxHeight = availableContentHeight
        )

        val contentPlaceable = contentMeasurable.measure(contentConstraints)
        val layoutWidth = maxOf(
            (labelOffset.x.roundToPx() * 2) + labelPlaceable.width,
            contentPlaceable.width,
            constraints.minWidth
        )

        val layoutHeight = maxOf(
            labelOffset.y.roundToPx() +
                labelPlaceable.height + (labelPlaceable.height / 2) +
                contentPlaceable.height,
            constraints.minHeight
        )

        val wrapperMaxHeight = (layoutHeight - labelPlaceable.height / 2).coerceAtLeast(0)
        val wrapperConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = layoutWidth,
            maxHeight = wrapperMaxHeight
        )
        val wrapperPlaceable = wrapperMeasurable.measure(wrapperConstraints)

        return layout(layoutWidth, layoutHeight) {

            wrapperPlaceable.place(
                0,
                layoutHeight - wrapperMaxHeight
            )

            labelPlaceable.place(
                labelOffset.x.roundToPx(),
                labelOffset.y.roundToPx()
            )

            contentPlaceable.place(
                (layoutWidth  - contentPlaceable.width) / 2,
                (layoutHeight + labelPlaceable.height / 2 - contentPlaceable.height) / 2
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
        modifier = modifier.minimumInteractiveComponentSize(),
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
