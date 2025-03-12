package ios.silv.database_android.dao

import ios.silv.database.Database
import ios.silv.database_android.DatabaseHandler
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class TabsRepo(
    private val database: Database,
    private val databaseHandler: DatabaseHandler,
    private val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    @Throws(IllegalStateException::class, NullPointerException::class)
    suspend fun insertTab(url: String?): Tab = databaseHandler.awaitOneExecutable(true) {
        tabQueries.insertTab(null)
        val tabId = tabQueries.lastInsertRowId().executeAsOne()

        if (url != null) {
            insertPage(tabId, url)
        }
        tabQueries.selectTabById(tabId)
    }

    @Throws(IllegalStateException::class, NullPointerException::class)
    suspend fun insertPage(tabId: Long, url: String): Page =
        databaseHandler.awaitOneExecutable(true) {
            val tab = tabQueries.selectTabById(tabId).executeAsOne()

            pageQueries.insertPage(tab.tid, url, tab.active_page_id)
            val pageId = pageQueries.lastInsertRowId().executeAsOne()
            tabQueries.updateTabActivePage(pageId, tabId)
            pageQueries.selectPageById(pageId)
        }


    @Throws(IllegalStateException::class, NullPointerException::class)
    suspend fun deleteTab(tab: Long) = databaseHandler.await {
        tabQueries.deleteById(tab)
    }

    @Throws(IllegalStateException::class, NullPointerException::class)
    suspend fun deletePage(pageId: Long) = databaseHandler.await(true) {
        val page = pageQueries.selectPageById(pageId).executeAsOne()
        pageQueries.deletePageById(page.pid)
        tabQueries.updateTabActivePage(page.prev_page, page.tab_id)
    }

    suspend fun selectTabById(id: Long): Tab? {
        return databaseHandler.awaitOneOrNull { tabQueries.selectTabById(id) }
    }

    fun observeTabsWithActivePage(): Flow<List<Pair<Tab, Page?>>> {
        return databaseHandler.subscribeToList {
            tabQueries.selectTabsWithActivePage(
                mapper = { tabId, activePageId, pageId, pageTabId, url, prevPage ->
                    Pair(
                        Tab(tabId, activePageId),
                        if (pageId != null && pageTabId != null && url != null) {
                            Page(tabId, pageTabId, url, prevPage)
                        } else {
                            null
                        }
                    )
                }
            )
        }
    }

    fun observeTabStackById(tabId: Long): Flow<Pair<Tab, List<Page>>?> {
        return databaseHandler.subscribeToList { tabQueries.selectTabWithPagesById(tabId) }
            .map { items ->
                if (items.isEmpty()) {
                    null
                } else {
                    Pair(
                        Tab(items[0].tid, items[0].active_page_id),
                        items.mapNotNull { item ->
                            if (item.pid != null && item.tab_id != null && item.url != null) {
                                Page(item.pid, item.tab_id, item.url, item.prev_page)
                            } else {
                                null
                            }
                        }
                    )
                }
            }
    }
}