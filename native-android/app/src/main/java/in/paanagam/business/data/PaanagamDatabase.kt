package `in`.paanagam.business.data

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
    @Query("SELECT * FROM recipe_lines WHERE menuItemId = :menuItemId")
    suspend fun recipeLines(menuItemId: String): List<RecipeLine>
    @Query("SELECT * FROM recipe_lines ORDER BY menuItemId")
    fun allRecipeLines(): Flow<List<RecipeLine>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRecipeLine(line: RecipeLine)
    @Query("DELETE FROM recipe_lines WHERE id = :id")
    suspend fun deleteRecipeLine(id: String)
    @Transaction
    suspend fun completeSale(sale: Sale, lines: List<SaleLine>) {
        require(lines.isNotEmpty()) { "Bill has no items" }
        var totalIngredientCost = 0L
        lines.forEach { line ->
            require(line.quantity > 0)
            val configuredRecipe = recipeLines(line.itemId)
            require(configuredRecipe.isNotEmpty()) { "Recipe missing for ${line.itemName}. Configure its ingredients before selling." }
            configuredRecipe.forEach { recipe ->
                val required = Math.multiplyExact(recipe.quantityBaseUnits, line.quantity.toLong())
                totalIngredientCost = Math.addExact(totalIngredientCost,
                    consumeFifo(recipe.ingredientId, "KITCHEN", required, sale.id, "SALE_CONSUMPTION", sale.createdAt))
            }
        }
        recordSale(sale)
        saveSaleCost(SaleCost(sale.id, totalIngredientCost))
        recordSaleLines(lines)
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSaleCost(cost: SaleCost)
    @Query("SELECT * FROM sale_costs WHERE saleId = :saleId")
    suspend fun saleCost(saleId: String): SaleCost?
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductionBatch(item: ProductionBatch)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductionInput(item: ProductionInput)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWastage(item: WastageRecord)

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
    suspend fun consumeFifo(ingredientId: String, location: String, quantity: Long, referenceId: String, movementType: String, at: Long): Long {
        require(quantity > 0)
        val lots = fifoLots(ingredientId, location)
        require(lots.sumOf { it.remainingBaseUnits } >= quantity) { "Insufficient stock" }
        var left = quantity
        var totalCost = 0L
        lots.forEach { lot ->
            if (left > 0) {
                val take = minOf(left, lot.remainingBaseUnits)
                updateLotBalance(lot.id, lot.remainingBaseUnits - take)
                totalCost += take * lot.unitCostPaise / 1000L
                insertStockMovement(StockMovement(UUID.randomUUID().toString(), ingredientId, lot.id, movementType, location, "", take, lot.unitCostPaise, referenceId, at))
                left -= take
            }
        }
        return totalCost
    }

    @Transaction
    suspend fun produceCoffeeDecoction(batch: ProductionBatch, powderId: String, waterId: String, decoctionId: String) {
        require(batch.inputPowderGrams == 250L && batch.inputWaterMl == 1100L && batch.outputDecoctionMl == 1000L && batch.retainedGroundsMl == 100L)
        val powder = ingredient(powderId) ?: error("Coffee powder not found")
        val water = ingredient(waterId) ?: error("Water not found")
        val decoction = ingredient(decoctionId) ?: error("Decoction ingredient not found")
        require(powder.isCoffeePowder && powder.baseUnit == "g" && water.baseUnit == "ml" && decoction.baseUnit == "ml")
        val powderCost = consumeFifo(powderId, "KITCHEN", 250000L, batch.id, "PRODUCTION_CONSUMPTION", batch.producedAt)
        val waterCost = consumeFifo(waterId, "KITCHEN", 1100000L, batch.id, "PRODUCTION_CONSUMPTION", batch.producedAt)
        val totalCost = powderCost + waterCost
        insertProductionBatch(batch)
        insertProductionInput(ProductionInput(UUID.randomUUID().toString(), batch.id, powderId, 250000L, powderCost))
        insertProductionInput(ProductionInput(UUID.randomUUID().toString(), batch.id, waterId, 1100000L, waterCost))
        val perMlCost = totalCost / 1000L
        val lot = StockLot(UUID.randomUUID().toString(), decoctionId, "KITCHEN", batch.producedAt, 1000000L, 1000000L, "ml", perMlCost)
        insertStockLot(lot)
        insertStockMovement(StockMovement(UUID.randomUUID().toString(), decoctionId, lot.id, "PRODUCTION_OUTPUT", "", "KITCHEN", 1000000L, perMlCost, batch.id, batch.producedAt))
    }

    @Transaction
    suspend fun saveWastage(item: WastageRecord) {
        val cost = consumeFifo(item.ingredientId, item.location, item.quantityBaseUnits, item.id, "WASTAGE", item.recordedAt)
        insertWastage(item.copy(costPaise = cost))
    }

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
        if (item.isCoffeePowder) require(quantityBaseUnits % 250000L == 0L) { "Coffee powder transfers must be in 250 g multiples" }
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

@Database(entities = [CatalogItem::class, Ingredient::class, StockLot::class, Purchase::class, PurchaseLine::class, StockTransfer::class, StockMovement::class, ProductionBatch::class, ProductionInput::class, WastageRecord::class, Sale::class, SaleLine::class, SopDocument::class, SopCheck::class, RecipeLine::class, SaleCost::class], version = 6, exportSchema = true)
abstract class PaanagamDatabase : RoomDatabase() {
    abstract fun dao(): PaanagamDao
}
