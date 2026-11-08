@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ios.silv.gembrowse

import androidx.compose.runtime.Stable
import androidx.paging.PagingSource
import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Database
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
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
    entities = [
        PageEntity::class,
        TabEntity::class,
        HistoryEntity::class,
        VisitEntity::class,
        BookmarkFolderEntity::class,
        BookmarkEntity::class
    ],
    version = 4,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPageDao(): PageDao
    abstract fun getTabDao(): TabDao
    abstract fun getHistoryDao(): HistoryDao
    abstract fun getBookmarkDao(): BookmarkDao
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
    val thumbnailUpdatedAt: Long? = null,
    /** Incognito tabs never write history, thumbnails or cache, and are purged on app start. */
    @ColumnInfo(defaultValue = "0")
    val isIncognito: Boolean = false,
)

@Stable
@Entity(
    tableName = "history",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["searchKey"]),
        Index(value = ["lastVisitedAt"]),
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Final URL after redirects (PageEntity.baseUrl). */
    val url: String,
    /** Lowercased url with the scheme stripped, e.g. "example.org/docs". Used for prefix matching. */
    val searchKey: String,
    val title: String? = null,
    val visitCount: Int = 1,
    /** Times the user typed this URL into the bar; ranks higher in suggestions than followed links. */
    @ColumnInfo(defaultValue = "0")
    val typedCount: Int = 0,
    val lastVisitedAt: Long,
)

@Stable
@Entity(
    tableName = "visit",
    foreignKeys = [
        ForeignKey(
            entity = HistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["historyId"]),
        Index(value = ["visitedAt"]),
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val historyId: Long,
    val visitedAt: Long,
)

@Stable
@Entity(tableName = "bookmark_folder")
data class BookmarkFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Stable
@Entity(
    tableName = "bookmark",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            // deleting a folder moves its bookmarks to the root instead of deleting them
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["folderId", "sortOrder"]),
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String? = null,
    val folderId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
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
    val visitedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val visitRecorded: Boolean = false
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


/** Folder list row: the folder plus how many bookmarks it holds. */
data class FolderRow(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val bookmarkCount: Int,
)

@Dao
interface BookmarkDao {

    /** Bookmarks directly inside a folder; pass null for the root. */
    @Query("""
        SELECT * FROM bookmark
        WHERE folderId IS :folderId
        ORDER BY sortOrder, id
    """)
    fun observeInFolder(folderId: Long?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    /** Drives the star / "bookmarked" state for the page on screen. */
    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Query("SELECT * FROM bookmark WHERE url = :url")
    suspend fun getByUrl(url: String): BookmarkEntity?

    /** Pass escapeLike(userInput). Used for the bookmark list search and for address-bar suggestions. */
    @Query("""
        SELECT * FROM bookmark
        WHERE url   LIKE '%' || :escapedQuery || '%' ESCAPE '\'
           OR title LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun search(escapedQuery: String, limit: Int = 8): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(bookmark: BookmarkEntity): Long   // -1 when the url already exists

    /** Next free position at the end of a folder (null = root). */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM bookmark WHERE folderId IS :folderId")
    suspend fun nextBookmarkOrder(folderId: Long?): Int

    /** Appends to the end of the folder. Returns the new id, or null if the url was already bookmarked. */
    @Transaction
    suspend fun add(url: String, title: String?, folderId: Long? = null, now: Long = nowSeconds()): Long? {
        val id = insertIgnore(
            BookmarkEntity(
                url = url,
                title = title,
                folderId = folderId,
                sortOrder = nextBookmarkOrder(folderId),
                createdAt = now,
            )
        )
        return id.takeIf { it != -1L }
    }

    @Query("DELETE FROM bookmark WHERE url = :url")
    suspend fun deleteByUrl(url: String): Int

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmark WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Star-button behavior. Returns true if the page is bookmarked afterwards. */
    @Transaction
    suspend fun toggle(url: String, title: String?, folderId: Long? = null, now: Long): Boolean {
        if (deleteByUrl(url) > 0) return false
        add(url, title, folderId, now)
        return true
    }

    @Query("UPDATE bookmark SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String?)

    /** Refresh the stored title when the page has a newer one; leaves a user-edited title alone if you pass null. */
    @Query("UPDATE bookmark SET title = COALESCE(:title, title) WHERE url = :url")
    suspend fun updateTitleByUrl(url: String, title: String?)

    @Query("UPDATE bookmark SET folderId = :folderId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun setFolderAndOrder(id: Long, folderId: Long?, sortOrder: Int)

    /** Move to another folder (null = root), landing at the end. */
    @Transaction
    suspend fun moveToFolder(id: Long, folderId: Long?) {
        setFolderAndOrder(id, folderId, nextBookmarkOrder(folderId))
    }

    @Query("UPDATE bookmark SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setBookmarkOrder(id: Long, sortOrder: Int)

    /** Pass the ids of one folder in their new display order. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setBookmarkOrder(id, index) }
    }

    @Query("""
        SELECT f.id AS id, f.name AS name, f.sortOrder AS sortOrder,
               COUNT(b.id) AS bookmarkCount
        FROM bookmark_folder f
        LEFT JOIN bookmark b ON b.folderId = f.id
        GROUP BY f.id
        ORDER BY f.sortOrder, f.id
    """)
    fun observeFolders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM bookmark_folder WHERE id = :id")
    suspend fun getFolder(id: Long): BookmarkFolderEntity?

    @Insert
    suspend fun insertFolder(folder: BookmarkFolderEntity): Long

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM bookmark_folder")
    suspend fun nextFolderOrder(): Int

    @Transaction
    suspend fun createFolder(name: String, now: Long): Long =
        insertFolder(BookmarkFolderEntity(name = name, sortOrder = nextFolderOrder(), createdAt = now))

    @Query("UPDATE bookmark_folder SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE bookmark_folder SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setFolderOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun reorderFolders(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setFolderOrder(id, index) }
    }

    /** Shifts a folder's bookmarks to the root, after what's already there, keeping their relative order. */
    @Query("UPDATE bookmark SET folderId = NULL, sortOrder = sortOrder + :offset WHERE folderId = :folderId")
    suspend fun moveFolderContentsToRoot(folderId: Long, offset: Int)

    @Query("DELETE FROM bookmark_folder WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    /** Deletes the folder; its bookmarks move to the root. */
    @Transaction
    suspend fun deleteFolderKeepBookmarks(id: Long) {
        moveFolderContentsToRoot(id, offset = nextBookmarkOrder(null))
        deleteFolderRow(id)
    }

    @Query("DELETE FROM bookmark WHERE folderId = :folderId")
    suspend fun deleteBookmarksInFolder(folderId: Long)

    /** Deletes the folder and every bookmark in it. */
    @Transaction
    suspend fun deleteFolderWithBookmarks(id: Long) {
        deleteBookmarksInFolder(id)
        deleteFolderRow(id)
    }
}

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

/** Must match how searchKey is written, and how suggestion queries normalize input. */
fun normalizeSearchKey(url: String): String =
    url.trim().lowercase().removePrefix("gemini://")

/** For LIKE ... ESCAPE '\' so user input can't act as wildcards. */
fun escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

data class VisitRow(
    val visitId: Long,
    val visitedAt: Long,
    val historyId: Long,
    val url: String,
    val title: String?,
)

@Dao
interface HistoryDao {

    @Query(
        """
        INSERT INTO history (url, searchKey, title, visitCount, typedCount, lastVisitedAt)
        VALUES (:url, :searchKey, :title, 1, :typedCount, :now)
        ON CONFLICT(url) DO UPDATE SET
            visitCount    = visitCount + 1,
            typedCount    = typedCount + excluded.typedCount,
            lastVisitedAt = excluded.lastVisitedAt,
            title         = COALESCE(excluded.title, title)
    """
    )
    suspend fun upsertHistory(
        url: String,
        searchKey: String,
        title: String?,
        typedCount: Int,
        now: Long,
    )

    @Query("SELECT id FROM history WHERE url = :url")
    suspend fun historyIdForUrl(url: String): Long?

    @Insert
    suspend fun insertVisit(visit: VisitEntity): Long

    /** The only entry point the presenter should call to record a page view. */
    @Transaction
    suspend fun recordVisit(
        url: String,
        title: String?,
        typed: Boolean = false,
        now: Long = nowSeconds(),
    ): Long {
        upsertHistory(url, normalizeSearchKey(url), title, if (typed) 1 else 0, now)
        val historyId = checkNotNull(historyIdForUrl(url)) { "history row missing for $url" }
        insertVisit(VisitEntity(historyId = historyId, visitedAt = now))
        return historyId
    }

    @Query(
        """
        SELECT v.id AS visitId, v.visitedAt AS visitedAt,
               h.id AS historyId, h.url AS url, h.title AS title
        FROM visit v JOIN history h ON h.id = v.historyId
        WHERE h.searchKey LIKE '%' || :escapedQuery || '%' ESCAPE '\'
           OR h.title     LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY v.visitedAt DESC
    """
    )
    fun pagingVisitsMatching(escapedQuery: String = ""): PagingSource<Int, VisitRow>

    @Query("SELECT * FROM history ORDER BY lastVisitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    /** Forget a page entirely. Visits go with it via ON DELETE CASCADE. */
    @Query("DELETE FROM history WHERE id = :historyId")
    suspend fun deleteHistory(historyId: Long)

    /** Forget a page entirely. Visits go with it via ON DELETE CASCADE. */
    @Query("DELETE FROM history WHERE id IN (:historyIds)")
    suspend fun deleteHistory(historyIds: List<Long>)

    /** "Forget this site". Pass the host exactly as in searchKey, e.g. "example.org". */
    @Query("DELETE FROM history WHERE searchKey = :host OR searchKey GLOB :host || '/*'")
    suspend fun deleteByHost(host: String)

    @Query("SELECT historyId FROM visit WHERE id = :visitId")
    suspend fun historyIdOfVisit(visitId: Long): Long?

    @Query("DELETE FROM visit WHERE id = :visitId")
    suspend fun deleteVisitRow(visitId: Long)

    @Query(
        """
        DELETE FROM history
        WHERE id = :historyId
          AND NOT EXISTS (SELECT 1 FROM visit WHERE historyId = :historyId)
    """
    )
    suspend fun deleteHistoryIfOrphan(historyId: Long)

    @Query(
        """
        UPDATE history SET
            visitCount    = (SELECT COUNT(*)        FROM visit v WHERE v.historyId = history.id),
            lastVisitedAt = (SELECT MAX(v.visitedAt) FROM visit v WHERE v.historyId = history.id)
        WHERE id = :historyId
    """
    )
    suspend fun recomputeStats(historyId: Long)

    /** Delete one row from the history list (one visit), not the whole page. */
    @Transaction
    suspend fun deleteVisit(visitId: Long) {
        val historyId = historyIdOfVisit(visitId) ?: return
        deleteVisitRow(visitId)
        deleteHistoryIfOrphan(historyId)
        recomputeStats(historyId)
    }

    /** URLs who's every visit lies in [start, end), i.e. pages that will disappear entirely. */
    @Query(
        """
        SELECT h.url FROM history h
        WHERE EXISTS (
            SELECT 1 FROM visit v
            WHERE v.historyId = h.id AND v.visitedAt >= :start AND v.visitedAt < :end)
          AND NOT EXISTS (
            SELECT 1 FROM visit v
            WHERE v.historyId = h.id AND (v.visitedAt < :start OR v.visitedAt >= :end))
    """
    )
    suspend fun urlsFullyInRange(start: Long, end: Long): List<String>

    @Query("DELETE FROM visit WHERE visitedAt >= :start AND visitedAt < :end")
    suspend fun deleteVisitsInRange(start: Long, end: Long)

    @Query("DELETE FROM history WHERE NOT EXISTS (SELECT 1 FROM visit WHERE historyId = history.id)")
    suspend fun deleteOrphanedHistory()

    /** Any row that lost a visit in the range has lastVisitedAt >= start, so this bounds the update. */
    @Query(
        """
        UPDATE history SET
            visitCount    = (SELECT COUNT(*)        FROM visit v WHERE v.historyId = history.id),
            lastVisitedAt = (SELECT MAX(v.visitedAt) FROM visit v WHERE v.historyId = history.id)
        WHERE lastVisitedAt >= :start
    """
    )
    suspend fun recomputeStatsSince(start: Long)

    /** Returns the URLs that are gone for good so the caller can evict caches for them. */
    @Transaction
    suspend fun clearRange(start: Long, end: Long): List<String> {
        val gone = urlsFullyInRange(start, end)
        deleteVisitsInRange(start, end)
        deleteOrphanedHistory()
        recomputeStatsSince(start)
        return gone
    }

    @Query("DELETE FROM history")
    suspend fun clearAll()
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

    @Query("UPDATE page SET visitRecorded = 1 WHERE id = :pageId AND visitRecorded = 0")
    suspend fun claimVisit(pageId: Long): Int   // rows changed: 1 = you won, 0 = already recorded
}

private fun nowSeconds() = Clock.System.now().epochSeconds

suspend fun AppDatabase.recordVisitOnce(pageId: Long, url: String, title: String?) {
    val pageDao = getPageDao()
    val historyDao = getHistoryDao()
    return withWriteTransaction {
        if (pageDao.claimVisit(pageId) == 1) {
            historyDao.recordVisit(url, title, now = nowSeconds())
        }
    }
}

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