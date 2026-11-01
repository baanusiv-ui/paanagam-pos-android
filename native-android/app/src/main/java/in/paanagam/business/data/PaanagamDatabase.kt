package in.paanagam.business.data

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PaanagamDao {
    @Query("SELECT * FROM catalog_items WHERE active = 1 ORDER BY category, name")
    fun catalog(): Flow<List<CatalogItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalog(items: List<CatalogItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalogItem(item: CatalogItem)
    @Insert
    suspend fun recordSale(sale: Sale)
    @Insert suspend fun recordSaleLines(lines: List<SaleLine>)
    @Transaction suspend fun completeSale(sale: Sale, lines: List<SaleLine>) { recordSale(sale); recordSaleLines(lines) }
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun sales(): Flow<List<Sale>>
    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId")
    suspend fun saleLines(saleId: String): List<SaleLine>
    @Query("SELECT * FROM sop_documents ORDER BY category, title")
    fun sops(): Flow<List<SopDocument>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSop(sop: SopDocument)
    @Insert
    suspend fun completeSopCheck(check: SopCheck)
    @Query("SELECT * FROM stock_lots WHERE ingredientId = :ingredientId AND location = :location AND remainingBaseUnits > 0 ORDER BY receivedAt ASC")
    suspend fun fifoLots(ingredientId: String, location: String): List<StockLot>
}

@Database(entities = [CatalogItem::class, StockLot::class, Sale::class, SaleLine::class, SopDocument::class, SopCheck::class], version = 2, exportSchema = true)
abstract class PaanagamDatabase : RoomDatabase() {
    abstract fun dao(): PaanagamDao
}
