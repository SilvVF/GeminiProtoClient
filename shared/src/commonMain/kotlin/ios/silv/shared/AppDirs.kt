package ios.silv.shared

import okio.FileSystem
import okio.Path

interface AppDirs {
    val fs: FileSystem
    val userConfig: Path
    val userData: Path
    val userCache: Path
}
