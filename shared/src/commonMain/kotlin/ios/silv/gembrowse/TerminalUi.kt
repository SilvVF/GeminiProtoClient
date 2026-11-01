package ios.silv.gembrowse

import androidx.annotation.FloatRange
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import ios.silv.libgemini.gemini.suspendRunCatching
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

const val MutedAlpha =  0.78f

interface ErrorHandler {
    fun onError(error: Throwable)
}
val LocalErrorHandler = compositionLocalOf<ErrorHandler> {
    object : ErrorHandler {
        override fun onError(error: Throwable) = logcat { error.asLog() }
    }
}

@Composable
fun LazyListState.sampleScrollingState(sample: Duration = 800.milliseconds): State<Boolean> {
    return produceState(false) {
        combine(
            snapshotFlow { firstVisibleItemIndex },
            snapshotFlow { isScrollInProgress },
            ::Pair
        )
            .onEach { (visibleIdx, scrolling) ->
                if (scrolling) value = true
                if (visibleIdx == 0) value = false
            }
            .sample(sample)
            .collect { (visibleIdx, scrolling) ->
                value = visibleIdx > 0 && scrolling
            }
    }
}

@Stable
class EventFlow<T>(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) : MutableSharedFlow<T> by MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)

@Composable
fun <T> rememberEventFlow(): EventFlow<T> {
    return remember {
        EventFlow(extraBufferCapacity = 20)
    }
}

@Composable
fun <EVENT> EventEffect(
    eventFlow: EventFlow<EVENT>,
    block: suspend CoroutineScope.(EVENT) -> Unit,
) {
    val errorHandler = LocalErrorHandler.current
    LaunchedEffect(eventFlow) {
        eventFlow.collect { event ->
            this@LaunchedEffect.launch {
                suspendRunCatching { block(event) }
                    .onFailure {
                        errorHandler.onError(it)
                    }
            }
        }
    }
}

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

val SwipeToDismissBoxState.actualProgress: Float
    @FloatRange(0.0, 1.0) get() = if (
        dismissDirection != SwipeToDismissBoxValue.Settled &&
        (progress != 1f || targetValue != SwipeToDismissBoxValue.Settled)
    ) {
        1f - progress
    } else {
        1f
    }

@Composable
fun isImeVisibleAsState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
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

private val CheckboxSize = 24.dp
private val CheckboxDefaultPadding = 2.dp

@Composable
fun CheckBoxCanvas(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Checkbox,
) {
    val checkColor = LocalContentColor.current
    val textMeasurer = rememberTextMeasurer()

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
        drawCheckbox(checked, checkColor, textMeasurer)
    }
}

private fun DrawScope.drawCheckbox(
    checked: Boolean,
    checkColor: Color,
    textMeasurer: TextMeasurer
) {
    val fontSize = (size.height * 0.8f).toSp()
    val textStyle = TextStyle(
        color = checkColor,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace
    )

    val bracketLeft = "["
    val bracketRight = "]"
    val xChar = "x"
    val placeholder = "x"

    val leftSize = textMeasurer.measure(bracketLeft, textStyle)
    val contentSize = textMeasurer.measure(placeholder, textStyle)
    val rightSize = textMeasurer.measure(bracketRight, textStyle)

    val totalWidth = leftSize.size.width + contentSize.size.width + rightSize.size.width
    val startX = (size.width - totalWidth) / 2f
    val baselineY = (size.height - contentSize.size.height) / 2f

    // Draw left bracket
    drawText(
        textMeasurer = textMeasurer,
        text = bracketLeft,
        style = textStyle,
        topLeft = androidx.compose.ui.geometry.Offset(startX, baselineY)
    )

    // Draw x if checked
    val contentX = startX + leftSize.size.width
    if (checked) {
        drawText(
            textMeasurer = textMeasurer,
            text = xChar,
            style = textStyle,
            topLeft = androidx.compose.ui.geometry.Offset(contentX, baselineY)
        )
    }

    // Draw right bracket
    val rightX = contentX + contentSize.size.width
    drawText(
        textMeasurer = textMeasurer,
        text = bracketRight,
        style = textStyle,
        topLeft = androidx.compose.ui.geometry.Offset(rightX, baselineY)
    )
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


@Stable
enum class NavLayoutAnchors {
    Start,
    End,
}

private val NavBlockSize = 128.dp

private fun createDraggable(
    density: Density,
    initial: NavLayoutAnchors
): AnchoredDraggableState<NavLayoutAnchors> {
    return AnchoredDraggableState(
        initialValue = initial,
        anchors = DraggableAnchors {
            with(density) {
                NavLayoutAnchors.End at (NavBlockSize + TerminalSectionDefaults.horizontalPadding).toPx()
                NavLayoutAnchors.Start at 0.dp.toPx()
            }
        }
    )
}

typealias NavLayoutDragState = AnchoredDraggableState<NavLayoutAnchors>

@Composable
fun rememberNavLayoutDraggableState(
    density: Density = LocalDensity.current
) = rememberSaveable(
    saver = Saver(
        save = { value -> value.currentValue.ordinal },
        restore = { saved ->
            createDraggable(density, NavLayoutAnchors.entries[saved])
        }
    )
) {
    createDraggable(density, NavLayoutAnchors.Start)
}

@Composable
fun DraggableNavLayout(
    navBlock: @Composable () -> Unit,
    stdOutBlock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    draggableState: NavLayoutDragState = rememberNavLayoutDraggableState()
) {
    Layout(
        modifier = modifier
            .anchoredDraggable(
                draggableState,
                Orientation.Horizontal
            ),
        content = {
            navBlock()
            stdOutBlock()
        }
    ) { measurables, constraints ->

        val navM = measurables[0]
        val stdoutM = measurables[1]

        val offset = draggableState.offset.roundToInt()

        val navP = navM.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = NavBlockSize.roundToPx(),
                maxHeight = constraints.maxHeight
            )
        )
        val stdoutP = stdoutM.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = constraints.maxWidth - offset,
                maxHeight = constraints.maxHeight
            )
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            stdoutP.place(offset, 0)
            navP.place(offset - navP.width, 0)
        }
    }
}