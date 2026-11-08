package ios.silv.vendor.cascadecompose.internal

import androidx.compose.runtime.Composable

internal fun interface MinSdkReader {
    @Composable
    fun minSdk(): Int
}

// TODO(get actual min sdk)
internal object RealMinSdkReader : MinSdkReader {
    @Composable
    override fun minSdk(): Int {
        return 28
    }
}
