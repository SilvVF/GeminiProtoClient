package ios.silv.gemclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import ios.silv.gemclient.ui.theme.MutedAlpha


@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun PreviewScrollToTopIndicator() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AutoScrollIndicator(
            modifier = Modifier,
            onClick = {}
        )
    }
}

@Composable
fun TerminalScrollToTop(
    onClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        ElevatedButton(
            shape = RectangleShape,
            onClick = onClick
        ) {
            Text("jump to top")
        }
    }
}

@Composable
fun AutoScrollIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    reversed: Boolean = false,
    chevronColor: Color = MaterialTheme.colorScheme.onSurface,
    lineColor: Color = MaterialTheme.colorScheme.onSurface.copy(
        alpha = MutedAlpha
    )
) {
    val interactionSource = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible,
        modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .semantics { role = Role.Button }
                .defaultMinSize(40.dp, 40.dp)
                .graphicsLayer {
                    this.rotationX = if (reversed) 180f else 0f
                }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()

                        val chevronStroke = size.width * 0.04f
                        val lineStroke = chevronStroke / 2f

                        val linePadding = chevronStroke * 1.5f

                        val chevHeight = size.height / 5.5f

                        val chevEndPoint =
                            (size.height / 2) - (chevHeight / 2) + lineStroke + linePadding
                        val chevWidth = size.width / 3.2f

                        val startX = size.width / 2 - chevWidth / 2
                        val startY = chevEndPoint + chevHeight

                        val midX = size.width / 2
                        val midY = chevEndPoint

                        val endY = startY
                        val endX = size.width / 2 + chevWidth / 2

                        val chevronPath = Path().apply {
                            moveTo(startX, startY)
                            lineTo(midX, midY)
                            lineTo(endX, endY)
                        }

                        drawPath(
                            path = chevronPath,
                            color = chevronColor,
                            style = Stroke(
                                width = chevronStroke,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        val lineY = midY - linePadding
                        drawLine(
                            color = lineColor,
                            start = Offset(startX - chevronStroke / 2, lineY),
                            end = Offset(endX + chevronStroke / 2, lineY),
                            strokeWidth = lineStroke,
                            cap = StrokeCap.Round
                        )
                    }
                },
            enabled = visible,
            shape = RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 4.dp,
            tonalElevation = 4.dp,
            interactionSource = interactionSource,
            content = {}
        )
    }
}