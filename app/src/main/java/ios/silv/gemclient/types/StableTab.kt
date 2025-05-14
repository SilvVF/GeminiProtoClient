package ios.silv.gemclient.types

import androidx.compose.runtime.Stable
import ios.silv.sqldelight.Tab


data class StableTab(
    val id: Long,
    val activePageId: Long?,
    val previewUpdatedAt: Long
) {
    constructor(tab: Tab): this(
        tab.tid,
        tab.active_page_id,
        tab.prev_image_updated_at
    )
}