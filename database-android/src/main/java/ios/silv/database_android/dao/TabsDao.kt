package ios.silv.database_android.dao

import ios.silv.database.Database
import ios.silv.database_android.DatabaseHandler
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal object TabMapper {

    val tabsWithPageMapper =
        { tabId: Long, activePageId: Long?, pageId: Long?, pageTabId: Long?, url: String?, prevPage: Long? ->
            Pair(
                Tab(tabId, activePageId),
                if (pageId != null && pageTabId != null && url != null) {
                    Page(tabId, pageTabId, url, prevPage)
                } else {
                    null
                }
            )
        }
}

class TabsRepo(
    private val databaseHandler: DatabaseHandler,
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

            pageQueries.insertPage(tabId = tab.tid, url, tab.active_page_id)
            val pageId = pageQueries.lastInsertRowId().executeAsOne()
            tabQueries.updateTabActivePage(activePageId = pageId, tabId)
            pageQueries.selectPageById(pageId)
        }


    @Throws(IllegalStateException::class, NullPointerException::class)
    suspend fun deleteTab(tab: Long) = databaseHandler.await {
        tabQueries.deleteById(tab)
    }

    @Throws(IllegalStateException::class, NullPointerException::class)
    suspend fun deletePage(pageId: Long): Page? = databaseHandler.await(true) {
        val page = pageQueries.selectPageById(pageId).executeAsOneOrNull()
        if (page != null) {
            pageQueries.deletePageById(page.pid)
            tabQueries.updateTabActivePage(page.prev_page, page.tab_id)
        }
        page
    }

    suspend fun popActivePageByTabId(tid: Long): Boolean {
        return databaseHandler.await(true) {
            val activePageId = tabQueries
                .selectTabById(tid)
                .executeAsOneOrNull()
                ?.active_page_id

            val deletedPage = activePageId?.let {
                deletePage(activePageId)
            }

            deletedPage != null
        }
    }

    suspend fun selectTabById(id: Long): Tab? {
        return databaseHandler.awaitOneOrNull { tabQueries.selectTabById(id) }
    }

    fun observeTabWithActivePage(tabId: Long): Flow<Pair<Tab, Page?>?> {
        return databaseHandler.subscribeToOneOrNull {
            tabQueries.selectTabWithActivePage(tabId, mapper = TabMapper.tabsWithPageMapper)
        }
    }

    fun observeTabsWithActivePage(): Flow<List<Pair<Tab, Page?>>> {
        return databaseHandler.subscribeToList {
            tabQueries.selectTabsWithActivePage(mapper = TabMapper.tabsWithPageMapper)
        }
    }

    fun observeTabStackById(tabId: Long): Flow<Pair<Tab, List<Page>>?> {
        return databaseHandler.subscribeToList { tabQueries.selectTabWithPagesById(tabId, mapper = TabMapper.tabsWithPageMapper) }
            .map { items ->
                if (items.isEmpty()) {
                    null
                } else {
                    Pair(
                        items[0].first,
                        items.mapNotNull { it.second }
                    )
                }
            }
    }
}