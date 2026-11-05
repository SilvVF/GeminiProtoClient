@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ios.silv.gembrowse

import androidx.compose.runtime.Stable
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Database(
    entities = [PageEntity::class, TabEntity::class],
    version = 2,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPageDao(): PageDao
    abstract fun getTabDao(): TabDao
}

/* -------------------------------------------------------------------------- */
/* Entities                                                                   */
/* -------------------------------------------------------------------------- */

@Stable
@Entity(tableName = "tab")
data class TabEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Position (within this tab's history) of the page currently shown. -1 = empty tab. */
    val currentPosition: Int = -1,
    /** User-visible ordering in the tab switcher. */
    val sortOrder: Int = 0,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val thumbnailUpdatedAt: Long? = null
)

@Stable
@Entity(
    tableName = "page",
    foreignKeys = [
        ForeignKey(
            entity = TabEntity::class,
            parentColumns = ["id"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Also serves as the lookup index for "page at position N of tab T",
    // and enforces that a tab never has two pages at the same position.
    indices = [Index(value = ["tabId", "position"], unique = true)]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tabId: Long,
    val position: Int,
    val url: String,
    val resolvedUrl: String? = null,
    val title: String? = null,
    val scrollY: Int = 0,
    val visitedAt: Long
) {
    val baseUrl get() = resolvedUrl ?: url
}

/**
 * Everything the UI needs per tab in one query: the tab, its current page,
 * and how many pages are in its history (for canGoForward).
 */
@Stable
data class TabState(
    @Embedded val tab: TabEntity,
    @Embedded(prefix = "page_") val page: PageEntity?,
    val pageCount: Int
) {
    val canGoBack: Boolean get() = tab.currentPosition > 0
    val canGoForward: Boolean get() = tab.currentPosition < pageCount - 1
}

private const val TAB_STATE_SELECT = """
    SELECT T.*,
           P.id        AS page_id,
           P.tabId     AS page_tabId,
           P.position  AS page_position,
           P.url       AS page_url,
           P.title     AS page_title,
           P.scrollY   AS page_scrollY,
           P.visitedAt AS page_visitedAt,
           (SELECT COUNT(*) FROM page WHERE tabId = T.id) AS pageCount
    FROM tab T
    LEFT JOIN page P ON P.tabId = T.id AND P.position = T.currentPosition
"""

/* -------------------------------------------------------------------------- */
/* DAOs                                                                       */
/* -------------------------------------------------------------------------- */

@Dao
interface TabDao {

    @Insert
    suspend fun insert(tab: TabEntity): Long

    @Query("SELECT * FROM tab WHERE id = :id LIMIT 1")
    suspend fun selectTabById(id: Long): TabEntity?

    @Query("SELECT id FROM tab ORDER BY sortOrder")
    suspend fun selectTabIdsOrdered(): List<Long>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tab")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE tab SET currentPosition = :position, lastAccessedAt = :now WHERE id = :id")
    suspend fun updateCurrentPosition(id: Long, position: Int, now: Long)

    @Query("UPDATE tab SET thumbnailUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateThumbnailUpdatedAt(id: Long, updatedAt: Long = nowSeconds())

    @Query("UPDATE tab SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM tab WHERE id = :id")
    suspend fun deleteTabById(id: Long)

    @Query("$TAB_STATE_SELECT ORDER BY T.sortOrder")
    fun observeTabStates(): Flow<List<TabState>>

    @Query("$TAB_STATE_SELECT WHERE T.id = :tabId")
    fun observeTabState(tabId: Long): Flow<TabState?>
}

@Dao
interface PageDao {

    @Insert
    suspend fun insert(page: PageEntity): Long

    @Query("SELECT * FROM page WHERE id = :id LIMIT 1")
    suspend fun selectPageById(id: Long): PageEntity?

    @Query("SELECT * FROM page WHERE tabId = :tabId AND position = :position LIMIT 1")
    suspend fun selectPageAt(tabId: Long, position: Int): PageEntity?

    /** Full history of a tab, oldest first. */
    @Query("SELECT * FROM page WHERE tabId = :tabId ORDER BY position")
    suspend fun selectPagesForTab(tabId: Long): List<PageEntity>

    /** Drops forward history. Pass the current position to keep it and everything before it. */
    @Query("DELETE FROM page WHERE tabId = :tabId AND position > :position")
    suspend fun deletePagesAfter(tabId: Long, position: Int)

    @Query("UPDATE page SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String?)

    @Query("UPDATE page SET scrollY = :scrollY WHERE id = :id")
    suspend fun updateScroll(id: Long, scrollY: Int)

    @Query("UPDATE page SET resolvedUrl = :resolvedUrl WHERE id = :id")
    suspend fun updateResolvedUrl(id: Long, resolvedUrl: String?)

    /** Url suggestions / debugging; distinct so duplicates across history don't repeat. */
    @Query("SELECT DISTINCT url FROM page")
    suspend fun selectPageUrls(): List<String>
}

private fun nowSeconds() = Clock.System.now().epochSeconds

suspend fun AppDatabase.createTab(url: String?, now: Long = nowSeconds()): TabEntity? {
    val tabDao = getTabDao()
    val pageDao = getPageDao()
    return withWriteTransaction {
        val tabId = tabDao.insert(
            TabEntity(
                sortOrder = tabDao.maxSortOrder() + 1,
                createdAt = now,
                lastAccessedAt = now
            )
        )
        if (url != null) {
            pageDao.insert(PageEntity(tabId = tabId, position = 0, url = url, visitedAt = now))
            tabDao.updateCurrentPosition(tabId, 0, now)
        }
        tabDao.selectTabById(tabId)
    }
}

/** User navigates to a new url: truncates forward history, appends a page. */
suspend fun AppDatabase.navigate(tabId: Long, url: String, now: Long = nowSeconds()): PageEntity? {
    val tabDao = getTabDao()
    val pageDao = getPageDao()
    return withWriteTransaction {
        val tab = tabDao.selectTabById(tabId) ?: return@withWriteTransaction null
        val next = tab.currentPosition + 1
        pageDao.deletePagesAfter(tabId, tab.currentPosition)
        val pageId = pageDao.insert(
            PageEntity(tabId = tabId, position = next, url = url, visitedAt = now)
        )
        tabDao.updateCurrentPosition(tabId, next, now)
        pageDao.selectPageById(pageId)
    }
}

/** Returns the page now showing, or null if there is nothing to go back to. */
suspend fun AppDatabase.goBack(tabId: Long, now: Long = nowSeconds()): PageEntity? =
    move(tabId, delta = -1, now)

/** Returns the page now showing, or null if there is nothing to go forward to. */
suspend fun AppDatabase.goForward(tabId: Long, now: Long = nowSeconds()): PageEntity? =
    move(tabId, delta = +1, now)

private suspend fun AppDatabase.move(tabId: Long, delta: Int, now: Long): PageEntity? {
    val tabDao = getTabDao()
    val pageDao = getPageDao()
    return withWriteTransaction {
        val tab = tabDao.selectTabById(tabId) ?: return@withWriteTransaction null
        val target = tab.currentPosition + delta
        val page = pageDao.selectPageAt(tabId, target) ?: return@withWriteTransaction null
        tabDao.updateCurrentPosition(tabId, target, now)
        page
    }
}

/**
 * Deletes the tab (its pages cascade) and returns the id of the tab that
 * should become selected: the next one, else the previous one, else null.
 */
suspend fun AppDatabase.closeTab(tabId: Long): Long? {
    val tabDao = getTabDao()
    return withWriteTransaction {
        val ids = tabDao.selectTabIdsOrdered()
        val index = ids.indexOf(tabId)
        if (index < 0) return@withWriteTransaction null
        tabDao.deleteTabById(tabId)
        ids.getOrNull(index + 1) ?: ids.getOrNull(index - 1)
    }
}

/** Reorders tabs in the switcher to match [orderedIds]. */
suspend fun AppDatabase.reorderTabs(orderedIds: List<Long>) {
    val tabDao = getTabDao()
    withWriteTransaction {
        orderedIds.forEachIndexed { index, id -> tabDao.updateSortOrder(id, index) }
    }
}