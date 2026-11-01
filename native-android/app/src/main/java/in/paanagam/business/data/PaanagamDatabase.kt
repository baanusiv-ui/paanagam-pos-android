package in.paanagam.business.data

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

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

    @Query("SELECT * FROM ingredients WHERE active = 1 ORDER BY name")
    fun ingredients(): Flow<List<Ingredient>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredient(item: Ingredient)
    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun ingredient(id: String): Ingredient?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(item: Purchase)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseLine(item: PurchaseLine)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockLot(item: StockLot)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockTransfer(item: StockTransfer)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockMovement(item: StockMovement)
    @Query("SELECT * FROM stock_lots WHERE ingredientId = :ingredientId AND location = :location AND remainingBaseUnits > 0 ORDER BY receivedAt ASC")
    suspend fun fifoLots(ingredientId: String, location: String): List<StockLot>
    @Query("UPDATE stock_lots SET remainingBaseUnits = :remaining WHERE id = :lotId")
    suspend fun updateLotBalance(lotId: String, remaining: Long)
    @Query("SELECT COALESCE(SUM(remainingBaseUnits), 0) FROM stock_lots WHERE ingredientId = :ingredientId AND location = :location")
    suspend fun stockBalance(ingredientId: String, location: String): Long

    @Transaction
    suspend fun receivePurchase(purchase: Purchase, lines: List<PurchaseLine>, lots: List<StockLot>) {
        require(lines.isNotEmpty() && lines.size == lots.size)
        insertPurchase(purchase)
        lines.zip(lots).forEach { (line, lot) ->
            require(line.quantityBaseUnits > 0 && line.unitCostPaise >= 0)
            require(lot.location == "STORE" && lot.remainingBaseUnits == line.quantityBaseUnits && lot.id == line.lotId)
            insertPurchaseLine(line)
            insertStockLot(lot)
            insertStockMovement(StockMovement(UUID.randomUUID().toString(), line.ingredientId, lot.id, "PURCHASE_RECEIPT", "", "STORE", line.quantityBaseUnits, line.unitCostPaise, purchase.id, purchase.purchasedAt))
        }
    }

    @Transaction
    suspend fun transferFifo(transfer: StockTransfer, ingredientId: String, quantityBaseUnits: Long) {
        require(quantityBaseUnits > 0)
        val item = ingredient(ingredientId) ?: error("Ingredient not found")
        if (item.isCoffeePowder) require(quantityBaseUnits % 250L == 0L) { "Coffee powder transfers must be in 250 g multiples" }
        require(transfer.fromLocation != transfer.toLocation && transfer.fromLocation in listOf("STORE", "KITCHEN") && transfer.toLocation in listOf("STORE", "KITCHEN"))
        insertStockTransfer(transfer)
        val lots = fifoLots(ingredientId, transfer.fromLocation)
        require(lots.sumOf { it.remainingBaseUnits } >= quantityBaseUnits) { "Insufficient stock" }
        var remaining = quantityBaseUnits
        lots.forEach { source ->
            if (remaining > 0) {
                val take = minOf(source.remainingBaseUnits, remaining)
                updateLotBalance(source.id, source.remainingBaseUnits - take)
                val destinationLot = StockLot(UUID.randomUUID().toString(), ingredientId, transfer.toLocation, transfer.transferredAt, take, take, item.baseUnit, source.unitCostPaise, source.id)
                insertStockLot(destinationLot)
                insertStockMovement(StockMovement(UUID.randomUUID().toString(), ingredientId, source.id, "TRANSFER_OUT", transfer.fromLocation, transfer.toLocation, take, source.unitCostPaise, transfer.id, transfer.transferredAt))
                insertStockMovement(StockMovement(UUID.randomUUID().toString(), ingredientId, destinationLot.id, "TRANSFER_IN", transfer.fromLocation, transfer.toLocation, take, source.unitCostPaise, transfer.id, transfer.transferredAt))
                remaining -= take
            }
        }
    }

}

@Database(entities = [CatalogItem::class, Ingredient::class, StockLot::class, Purchase::class, PurchaseLine::class, StockTransfer::class, StockMovement::class, Sale::class, SaleLine::class, SopDocument::class, SopCheck::class], version = 3, exportSchema = true)
abstract class PaanagamDatabase : RoomDatabase() {
    abstract fun dao(): PaanagamDao
}
