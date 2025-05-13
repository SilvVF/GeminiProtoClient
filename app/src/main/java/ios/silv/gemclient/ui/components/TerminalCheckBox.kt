package ios.silv.gemclient.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp


private val CheckboxSize = 20.dp
private val CheckboxDefaultPadding = 2.dp

@Composable
private fun CheckBoxCanvas(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    role: Role,
) {
    val checkColor = LocalContentColor.current
    Canvas(
        modifier
            .minimumInteractiveComponentSize()
            .padding(CheckboxDefaultPadding)
            .wrapContentSize(Alignment.Center)
            .requiredSize(CheckboxSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = role,
            ) {
                onCheckedChange(!checked)
            }
    ) {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = size.height * 0.8f
                typeface = android.graphics.Typeface.MONOSPACE
                color = checkColor.toArgb()
            }

            val bracketLeft = "["
            val bracketRight = "]"
            val xChar = "x"
            val placeholder = "x"

            val leftWidth = paint.measureText(bracketLeft)
            val contentWidth = paint.measureText(placeholder)
            val rightWidth = paint.measureText(bracketRight)
            val totalWidth = leftWidth + contentWidth + rightWidth


            val metrics = paint.fontMetrics
            val baseY = (size.height - metrics.top - metrics.bottom) / 2f

            val startX = (size.width - totalWidth) / 2f

            // Draw left bracket
            canvas.nativeCanvas.drawText(bracketLeft, startX, baseY, paint)
            // Draw x if checked
            val contentX = startX + leftWidth
            if (checked) {
                canvas.nativeCanvas.drawText(xChar, contentX, baseY, paint)
            }
            // Draw right bracket
            val rightX = contentX + contentWidth
            canvas.nativeCanvas.drawText(bracketRight, rightX, baseY, paint)
        }
    }
}

@Composable
fun TerminalRadioButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CheckBoxCanvas(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        role = Role.RadioButton
    )
}

@Composable
fun TerminalCheckBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CheckBoxCanvas(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        role = Role.Checkbox
    )
}