package ios.silv.gemclient.types

import ios.silv.sqldelight.Page

data class StablePage(
    val id: Long,
    val tabId: Long,
    val url: String,
    val prevId: Long?
) {
    constructor(page: Page): this(
        page.pid,
        page.tab_id,
        page.url,
        page.prev_page
    )
}

fun StablePage(page: Page?): StablePage? {
    return if(page == null) {
        null
    } else {
        StablePage(page)
    }
}