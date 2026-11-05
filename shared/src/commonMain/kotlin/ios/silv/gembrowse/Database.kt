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
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
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
        .build()
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Database(entities = [PageEntity::class, TabEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getPageDao(): PageDao
    abstract fun getTabDao(): TabDao
    abstract fun getCombinedDao(): CombinedDao
}
@Dao
interface CombinedDao : TabDao, PageDao {

    @Transaction
    suspend fun insertTabAndPage(url: String?): TabEntity? {
        val id = insertTab(null)
        if (url != null) {
            insertPage(id.toInt(), url, null)
        }
        return selectTabById(id.toInt())
    }
}

@Stable
@Entity(
    tableName = "page",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["prevPageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TabEntity::class,
            parentColumns = ["id"],
            childColumns = ["tabId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tabId"]),
        Index(value = ["prevPageId"])
    ]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val tabId: Int,
    val url: String,
    val prevPageId: Int? = null
)

@Dao
interface PageDao {

    @Query("SELECT url FROM page")
    suspend fun selectPageUrls(): List<String>

    @Query("INSERT INTO page(id, tabId, url, prevPageId) VALUES(NULL, :tabId, :url, :prevPageId)")
    suspend fun insertPage(tabId: Int, url: String, prevPageId: Int?): Long

    @Query("SELECT * FROM page WHERE id = :id LIMIT 1")
    suspend fun selectPageById(id: Int): PageEntity?

    @Query("DELETE FROM page WHERE id = :id")
    suspend fun deletePageById(id: Int)
}


@Stable
@Entity(tableName = "tab")
data class TabEntity(
    @PrimaryKey
    val id: Int = 0,
    val activePageId: Int? = null,
    val prevImageUpdatedAt: Long = -1
)

@Stable
data class TabWithActivePage(
    @Embedded val tab: TabEntity,
    @Relation(
        parentColumns = ["activePageId"],
        entityColumns = ["id"]
    )
    val page: PageEntity?
)

@Stable
data class TabWithPages(
    @Embedded
    val tab: TabEntity,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["tabId"]
    )
    val pages: List<PageEntity>
)

@Dao
interface TabDao {

    @Query("UPDATE tab SET prevImageUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePrevImageUpdatedAt(
        id: Int,
        updatedAt: Long = Clock.System.now().epochSeconds
    )

    @Query("""
    INSERT INTO tab(id, activePageId, prevImageUpdatedAt)
    VALUES (NULL, :activePageId, :prevImageUpdatedAt)
    ON CONFLICT(id) DO UPDATE SET activePageId = excluded.activePageId
    """)
    suspend fun insertTab(
        activePageId: Int?,
        prevImageUpdatedAt: Long = -1
    ): Long

    @Query("SELECT id FROM tab")
    suspend fun selectTabIds(): List<Int>

    @Query("DELETE FROM tab WHERE id = :id")
    suspend fun deleteTabById(id: Int)

    @Query("SELECT * FROM tab WHERE id = :id LIMIT 1")
    suspend fun selectTabById(id: Int): TabEntity?

    @Query("UPDATE tab SET activePageId = :activePageId WHERE id = :id")
    suspend fun updateTabActivePage(id: Int, activePageId: Int?)

    @Query(
        """
        SELECT T.*, P.*
        FROM Tab T
        LEFT JOIN Page P ON (P.tabId = T.id AND P.id = T.activePageId)
    """
    )
    fun selectTabsWithActivePage(): Flow<List<TabWithActivePage>>

    @Query(
        """
        SELECT T.*, P.*
        FROM Tab T
        LEFT JOIN Page P ON (P.tabId = T.id AND P.id = T.activePageId)
        WHERE T.id = :tabId
    """
    )
    fun selectTabWithActivePage(tabId: Int): Flow<TabWithActivePage?>

    @Query(
        """
        SELECT T.*, P.id, P.tabId, P.url, P.prevPageId
        FROM Tab T
        LEFT JOIN Page P ON (P.tabId = T.id)
        WHERE T.id = :id
    """
    )
    suspend fun selectTabWithPagesById(id: Int): List<TabWithPages>
}

suspend fun AppDatabase.createPage(url: String, tabId: Int): PageEntity? {
    val dao = getCombinedDao()
    return withWriteTransaction {
        val tab = dao.selectTabById(tabId) ?: return@withWriteTransaction null
        val pageId = dao.insertPage(tabId = tab.id, url, tab.activePageId)
        dao.updateTabActivePage(id = tab.id, activePageId = pageId.toInt())
        dao.selectPageById(pageId.toInt())
    }
}

suspend fun AppDatabase.createTab(url: String?): TabEntity? {
    val tabDao = getTabDao()
    val pageDao = getPageDao()
    return withWriteTransaction {
        val id = tabDao.insertTab(null)
        if (url != null) {
            val pageId = pageDao.insertPage(id.toInt(), url, null)
            tabDao.updateTabActivePage(id.toInt(), pageId.toInt())
        }
        tabDao.selectTabById(id.toInt())
    }
}