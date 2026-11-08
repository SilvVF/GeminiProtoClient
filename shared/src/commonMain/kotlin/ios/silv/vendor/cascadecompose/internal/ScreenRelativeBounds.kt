package ios.silv.vendor.cascadecompose.internal


import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize

@Immutable
internal data class ScreenRelativeBounds(
    val boundsInRoot: Rect,
    val root: RootLayoutCoordinatesInfo,
) {
    val boundsInWindow: Rect
        get() = Rect(
            offset = boundsInRoot.topLeft + root.layoutPositionInWindow,
            size = boundsInRoot.size,
        )
}

@Immutable
internal data class ScreenRelativeOffset(
    val positionInWindow: Offset,
    val root: RootLayoutCoordinatesInfo,
) {
    val positionInRoot: Offset
        get() = positionInWindow - root.layoutPositionInWindow
}

@Immutable
internal data class RootLayoutCoordinatesInfo(
    /** The boundaries of this layout relative to the window's origin. */
    val layoutPositionInWindow: Offset,
    /**
     * This can include system bars because PopupPositionProvider
     * does not take insets into account.
     */
    val windowBoundsMinusIme: Rect,
)

/** Stand-in for the Android `View` that used to be passed as `owner`. */
internal interface WindowOwner {
    /** Bounds of the root (window) relative to the screen. */
    fun getBoundsOnScreenAsRoot(root: LayoutCoordinates): Rect
    fun getImeInsets(): PlatformInsets
}

private class ComposeWindowOwner(
    private val windowInfo: WindowInfo,
    private val ime: WindowInsets,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
) : WindowOwner {

    override fun getBoundsOnScreenAsRoot(root: LayoutCoordinates): Rect {
        val size = windowInfo.containerSize
        return Rect(
            offset = root.localToScreen(Offset.Zero),
            size = Size(size.width.toFloat(), size.height.toFloat()),
        )
    }

    override fun getImeInsets() = platformInsets(
        getLeft = { ime.getLeft(density, layoutDirection) },
        getTop = { ime.getTop(density) },
        getRight = { ime.getRight(density, layoutDirection) },
        getBottom = { ime.getBottom(density) },
    )
}

@Composable
internal fun rememberWindowOwner(): WindowOwner {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val ime = WindowInsets.ime
    return remember(windowInfo, density, layoutDirection, ime) {
        ComposeWindowOwner(windowInfo, ime, density, layoutDirection)
    }
}

internal fun ScreenRelativeBounds(
    coordinates: LayoutCoordinates,
    owner: WindowOwner,
): ScreenRelativeBounds {
    val root = coordinates.findRootCoordinates()
    return ScreenRelativeBounds(
        boundsInRoot = Rect(
            offset = coordinates.positionInRoot(),
            size = coordinates.size.toSize()
        ),
        root = RootLayoutCoordinatesInfo(
            layoutPositionInWindow = root.positionInWindow(),
            windowBoundsMinusIme = owner.getBoundsOnScreenAsRoot(root).let { bounds ->
                val insets = owner.getImeInsets()
                bounds.copy(
                    left = bounds.left + insets.left,
                    top = bounds.top + insets.top,
                    right = bounds.right - insets.right,
                    bottom = bounds.bottom - insets.bottom,
                )
            }
        )
    )
}

/**
 * Calculate a position in another window such that its visual location on screen
 * remains unchanged. That is, its offset from screen's 0,0 remains the same.
 */
internal fun ScreenRelativeOffset.positionInWindowOf(other: ScreenRelativeBounds): Offset {
    return positionInRoot -
            (other.root.layoutPositionInWindow - root.layoutPositionInWindow) -
            (other.root.windowBoundsMinusIme.topLeft - root.windowBoundsMinusIme.topLeft)
}

// Own insets type. Do NOT import androidx.compose.ui.platform.PlatformInsets.
internal interface PlatformInsets {
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
}

internal fun platformInsets(
    getLeft: () -> Int = { 0 },
    getTop: () -> Int = { 0 },
    getRight: () -> Int = { 0 },
    getBottom: () -> Int = { 0 },
): PlatformInsets = object : PlatformInsets {
    override val left get() = getLeft()
    override val top get() = getTop()
    override val right get() = getRight()
    override val bottom get() = getBottom()
}