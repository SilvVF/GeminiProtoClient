package ios.silv.gemclient.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Composable
fun TerminalSection(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    content: @Composable () -> Unit,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    labelOffset: DpOffset = DpOffset(12.dp, 0.dp)
) {
    Layout(
        modifier = modifier,
        content = {
            content()
            label()
            // Using Box directly here for wrapper; it will just take up the layout size.
            Box(Modifier.fillMaxSize().border(1.dp, borderColor))
        }
    ) { measurables, constraints ->

        val contentMeasurable = measurables[0]
        val labelMeasurable = measurables[1]
        val wrapperMeasurable = measurables[2]

        val labelPlaceable = labelMeasurable.measure(
            constraints.copy(minHeight = 0, maxHeight = constraints.maxHeight)
        )

        val availableHeight = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            // fallback for unbounded height: size to content
            val contentPlaceable = contentMeasurable.measure(constraints.copy(minHeight = 0))
            contentPlaceable.height + labelPlaceable.height + labelPlaceable.height / 2
        }

        val bottomBarPlaceable = contentMeasurable.measure(
            constraints.copy(minHeight = 0, maxHeight = availableHeight)
        )

        val layoutHeight = labelPlaceable.height + bottomBarPlaceable.height + labelPlaceable.height / 2

        val wrapperPlaceable = wrapperMeasurable.measure(
            constraints.copy(minHeight = 0, maxHeight = layoutHeight - labelPlaceable.height / 2)
        )

        layout(constraints.maxWidth, layoutHeight) {
            bottomBarPlaceable.place(
                (constraints.maxWidth - bottomBarPlaceable.width) / 2,
                labelPlaceable.height
            )
            wrapperPlaceable.place(0, labelPlaceable.height / 2)
            labelPlaceable.place(labelOffset.x.roundToPx(), labelOffset.y.roundToPx())
        }
    }
}
