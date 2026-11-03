package `in`.paanagam.business.data

import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PaanagamDao {

    @Query("SELECT * FROM sales_reconciliations ORDER BY reconciledAt DESC")
    fun salesReconciliations(): Flow<List<SalesReconciliation>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSalesReconciliation(item: SalesReconciliation)


    @Query("SELECT * FROM held_orders ORDER BY createdAt DESC")
    fun heldOrders(): Flow<List<HeldOrder>>
    @Query("SELECT * FROM held_order_lines WHERE orderId = :orderId")
    suspend fun heldOrderLines(orderId: String): List<HeldOrderLine>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHeldOrder(order: HeldOrder)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHeldOrderLines(lines: List<HeldOrderLine>)
    @Query("DELETE FROM held_order_lines WHERE orderId = :orderId")
    suspend fun deleteHeldOrderLines(orderId: String)
    @Query("DELETE FROM held_orders WHERE id = :orderId")
    suspend fun deleteHeldOrderRow(orderId: String)
    @Transaction
    suspend fun saveHeldOrder(order: HeldOrder, lines: List<HeldOrderLine>) {
        require(lines.isNotEmpty() && lines.all { it.orderId == order.id && it.quantity > 0 })
        insertHeldOrder(order)
        insertHeldOrderLines(lines)
    }
    @Transaction
    suspend fun removeHeldOrder(orderId: String) {
        deleteHeldOrderLines(orderId)
        deleteHeldOrderRow(orderId)
    }


    @Query("SELECT * FROM kitchen_tickets ORDER BY createdAt ASC")
fun kitchenTickets(): Flow<List<KitchenTicket>>
@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insertKitchenTicket(ticket: KitchenTicket)
@Query("UPDATE kitchen_tickets SET status = :status, updatedAt = :updatedAt WHERE saleId = :saleId")
suspend fun updateKitchenTicketStatus(saleId: String, status: String, updatedAt: Long)
@Query("SELECT * FROM kitchen_tickets WHERE saleId = :saleId")
suspend fun kitchenTicket(saleId: String): KitchenTicket?

@Query("SELECT * FROM combo_components WHERE comboItemId = :comboItemId ORDER BY componentName")
suspend fun comboComponents(comboItemId: String): List<ComboComponent>
@Query("SELECT * FROM combo_components ORDER BY comboItemId, componentName")
fun allComboComponents(): Flow<List<ComboComponent>>
@Insert(onConflict = OnConflictStrategy.ABORT)
suspend fun insertComboComponent(component: ComboComponent)
@Query("DELETE FROM combo_components WHERE comboItemId = :comboItemId")
suspend fun deleteComboComponents(comboItemId: String)
@Transaction
suspend fun replaceComboComponents(comboItemId: String, components: List<ComboComponent>) {
 require(components.isNotEmpty() && components.all { it.comboItemId == comboItemId && it.quantity > 0 && it.componentItemId != comboItemId })
 require(components.map { it.componentItemId }.distinct().size == components.size)
 deleteComboComponents(comboItemId)
 components.forEach { insertComboComponent(it) }
}

@Query("SELECT * FROM next_day_plans ORDER BY planDate DESC, menuItemName")
    fun nextDayPlans(): Flow<List<NextDayPlan>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNextDayPlan(plan: NextDayPlan)
    @Update
    suspend fun updateNextDayPlan(plan: NextDayPlan)
    @Query("DELETE FROM next_day_plans WHERE id = :id")
    suspend fun deleteNextDayPlan(id: String)

    @Query("SELECT * FROM suppliers ORDER BY active DESC, name")
    fun suppliers(): Flow<List<Supplier>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(supplier: Supplier)
    @Update
    suspend fun updateSupplier(supplier: Supplier)
    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun supplier(id: String): Supplier?

    @Query("SELECT * FROM cash_movements WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    fun cashMovements(shiftId: String): Flow<List<CashMovement>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashMovement(movement: CashMovement)
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM cash_movements WHERE shiftId = :shiftId AND type = 'CASH_IN'")
    suspend fun cashInTotal(shiftId: String): Long
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM cash_movements WHERE shiftId = :shiftId AND type IN ('CASH_OUT', 'REFUND', 'WITHDRAWAL')")
    suspend fun cashOutTotal(shiftId: String): Long
    @Transaction
    suspend fun recordCashMovement(movement: CashMovement) {
        require(movement.amountPaise > 0 && movement.type in setOf("CASH_IN", "CASH_OUT", "REFUND", "WITHDRAWAL"))
        val shift = getOpenCashShift() ?: error("Open a cash shift before recording cash movements.")
        require(shift.id == movement.shiftId) { "Cash movement must belong to the active shift." }
        insertCashMovement(movement)
    }
    @Query("SELECT * FROM cash_shifts WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    fun activeCashShift(): Flow<CashShift?>
    @Query("SELECT * FROM cash_shifts ORDER BY openedAt DESC")
    fun cashShifts(): Flow<List<CashShift>>
    @Query("SELECT * FROM cash_shifts WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getOpenCashShift(): CashShift?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashShift(shift: CashShift)
    @Update
    suspend fun updateCashShift(shift: CashShift)
    @Query("SELECT COALESCE(SUM(cashPaidPaise), 0) FROM sales WHERE status = 'COMPLETED' AND shiftId = :shiftId")
    suspend fun cashSalesTotal(shiftId: String): Long
    @Transaction
    suspend fun openCashShift(shift: CashShift) {
        require(shift.openingFloatPaise >= 0)
        require(getOpenCashShift() == null) { "A cash shift is already open. Close it before starting another." }
        insertCashShift(shift)
    }
    @Transaction
    suspend fun closeCashShift(countedCashPaise: Long, notes: String, closedAt: Long): CashShift {
        require(countedCashPaise >= 0)
        val shift = getOpenCashShift() ?: error("No open cash shift.")
        val cashSales = cashSalesTotal(shift.id)
        val expected = Math.subtractExact(Math.addExact(Math.addExact(shift.openingFloatPaise, cashSales), cashInTotal(shift.id)), cashOutTotal(shift.id))
        val closed = shift.copy(
            closedAt = closedAt,
            expectedCashPaise = expected,
            countedCashPaise = countedCashPaise,
            variancePaise = countedCashPaise - expected,
            notes = notes,
            status = "CLOSED"
        )
        updateCashShift(closed)
        return closed
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockReconciliation(record: StockReconciliation)
    @Query("SELECT * FROM stock_reconciliations ORDER BY reconciledAt DESC")
    fun stockReconciliations(): Flow<List<StockReconciliation>>
    @Transaction
    suspend fun reconcileStock(record: StockReconciliation, item: Ingredient) {
        require(record.location == "STORE" || record.location == "KITCHEN")
        require(record.ingredientId == item.id) { "Reconciliation ingredient does not match the selected ingredient." }
        require(record.physicalQtyBaseUnits >= 0 && record.systemQtyBaseUnits >= 0)
        require(record.adjustmentCostPaise >= 0)
        require(stockBalance(item.id, record.location) == record.systemQtyBaseUnits) {
            "Stock changed after the count was opened. Refresh the system balance and recount."
        }
        val variance = Math.subtractExact(record.physicalQtyBaseUnits, record.systemQtyBaseUnits)
        if (item.isCoffeePowder && variance < 0) {
            require(item.baseUnit == "g" && variance % 250000L == 0L) {
                "Coffee powder stock reductions must be in 250 g multiples."
            }
        }
        require(variance == record.varianceBaseUnits)
        if (variance > 0) {
            val lot = StockLot(UUID.randomUUID().toString(), item.id, record.location, record.reconciledAt, variance, variance, item.baseUnit, record.adjustmentCostPaise)
            insertStockLot(lot)
            insertStockMovement(StockMovement(UUID.randomUUID().toString(), item.id, lot.id, "RECONCILIATION_GAIN", "", record.location, variance, record.adjustmentCostPaise, record.id, record.reconciledAt))
        } else if (variance < 0) {
            consumeFifo(item.id, record.location, -variance, record.id, "RECONCILIATION_LOSS", record.reconciledAt)
        }
        insertStockReconciliation(record)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperationalRecord(record: OperationalRecord)
    @Query("SELECT * FROM operational_records WHERE module = :module ORDER BY createdAt DESC")
    fun operationalRecords(module: String): Flow<List<OperationalRecord>>
    @Query("SELECT * FROM operational_records ORDER BY createdAt DESC")
    fun allOperationalRecords(): Flow<List<OperationalRecord>>

    @Query("SELECT * FROM loyalty_accounts ORDER BY customerName")
    fun loyaltyAccounts(): Flow<List<LoyaltyAccount>>
    @Query("SELECT * FROM loyalty_accounts WHERE phone = :phone")
    suspend fun loyaltyAccount(phone: String): LoyaltyAccount?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLoyaltyAccount(account: LoyaltyAccount)
    @Query("SELECT * FROM staff_members ORDER BY active DESC, name")
    fun staffMembers(): Flow<List<StaffMember>>
    @Query("SELECT * FROM staff_members WHERE active = 1 ORDER BY createdAt LIMIT 1")
    suspend fun firstActiveStaff(): StaffMember?
    @Query("SELECT * FROM staff_members WHERE id = :id")
    suspend fun staffById(id: String): StaffMember?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStaff(member: StaffMember)
    @Update
    suspend fun updateStaff(member: StaffMember)
    @Query("UPDATE staff_members SET pinSalt = :salt, pinHash = :hash WHERE id = :id")
    suspend fun resetStaffPin(id: String, salt: String, hash: String)
    @Query("UPDATE staff_members SET active = :active WHERE id = :id")
    suspend fun setStaffActive(id: String, active: Boolean)

    @Query("SELECT * FROM catalog_items WHERE active = 1 ORDER BY category, name")
    fun catalog(): Flow<List<CatalogItem>>
    @Query("SELECT * FROM catalog_items WHERE id = :id")
    suspend fun catalogItem(id: String): CatalogItem?
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
    @Query("DELETE FROM recipe_lines WHERE menuItemId = :menuItemId")
    suspend fun deleteRecipeLinesForMenuItem(menuItemId: String)
    @Transaction
    suspend fun replaceRecipe(menuItemId: String, lines: List<RecipeLine>) {
        require(lines.isNotEmpty() && lines.all { it.menuItemId == menuItemId && it.quantityBaseUnits > 0 })
        deleteRecipeLinesForMenuItem(menuItemId)
        lines.forEach { saveRecipeLine(it) }
    }
    @Transaction
    suspend fun completeSale(sale: Sale, lines: List<SaleLine>) {
        require(lines.isNotEmpty()) { "Bill has no items" }
        require(lines.all { it.saleId == sale.id && it.quantity > 0 && it.unitPricePaise >= 0 && it.lineTotalPaise == Math.multiplyExact(it.unitPricePaise, it.quantity.toLong()) }) { "Bill line totals are invalid." }
        require(lines.fold(0L) { sum, line -> Math.addExact(sum, line.lineTotalPaise) } == sale.totalPaise) { "Bill total does not match its line items." }
        val cashPortion = when (sale.paymentMethod) {
            "CASH" -> sale.totalPaise
            "UPI" -> 0L
            "SPLIT" -> sale.cashPaidPaise
            else -> error("Unsupported payment method.")
        }
        val upiPortion = when (sale.paymentMethod) {
            "CASH" -> 0L
            "UPI" -> sale.totalPaise
            "SPLIT" -> sale.upiPaidPaise
            else -> 0L
        }
        require(cashPortion >= 0 && upiPortion >= 0 && cashPortion + upiPortion == sale.totalPaise) { "Cash and UPI amounts must equal the bill total." }
        val activeShift = getOpenCashShift()
        require(cashPortion == 0L || activeShift != null) { "Open a cash shift before accepting cash payments." }
        val saleWithShift = sale.copy(shiftId = if (cashPortion > 0) activeShift?.id else null, cashPaidPaise = cashPortion, upiPaidPaise = upiPortion)
        val rewardLines = lines.filter { catalogItem(it.itemId)?.isCoffee == true && it.unitPricePaise == 0L }
        require(rewardLines.size <= 1 && rewardLines.all { it.quantity == 1 }) { "Only one free coffee can be redeemed per bill." }
        val loyaltyBefore = if (rewardLines.isNotEmpty()) {
            require(sale.customerPhone.matches(Regex("[0-9]{10}"))) { "Enter the customer's 10-digit mobile number to redeem." }
            loyaltyAccount(sale.customerPhone)?.also { require(it.freeCoffeesAvailable > 0) { "No free coffee reward is available." } }
                ?: error("No loyalty account found for this mobile number.")
        } else null
        var totalIngredientCost = 0L
        lines.forEach { line ->
            require(line.quantity > 0)
            val components = comboComponents(line.itemId)
            val requirements = mutableMapOf<String, Long>()
            if (components.isNotEmpty()) {
                components.forEach { component ->
                    val componentRecipe = recipeLines(component.componentItemId)
                    require(componentRecipe.isNotEmpty()) { "Recipe missing for combo component ${component.componentName}." }
                    componentRecipe.forEach { recipe ->
                        val qty = Math.multiplyExact(Math.multiplyExact(recipe.quantityBaseUnits, component.quantity.toLong()), line.quantity.toLong())
                        requirements[recipe.ingredientId] = Math.addExact(requirements[recipe.ingredientId] ?: 0L, qty)
                    }
                }
            } else {
                val configuredRecipe = recipeLines(line.itemId)
                require(configuredRecipe.isNotEmpty()) { "Recipe missing for ${line.itemName}. Configure its ingredients before selling." }
                configuredRecipe.forEach { recipe ->
                    val qty = Math.multiplyExact(recipe.quantityBaseUnits, line.quantity.toLong())
                    requirements[recipe.ingredientId] = Math.addExact(requirements[recipe.ingredientId] ?: 0L, qty)
                }
            }
            requirements.forEach { (ingredientId, required) ->
                val ingredient = ingredient(ingredientId) ?: error("Recipe ingredient not found.")
                require(!ingredient.isCoffeePowder) {
                    "Coffee powder is a production ingredient. Prepare decoction first; POS recipes must consume decoction, milk and sugar."
                }
                totalIngredientCost = Math.addExact(totalIngredientCost, consumeFifo(ingredientId, "KITCHEN", required, sale.id, "SALE_CONSUMPTION", sale.createdAt))
            }
        }
        recordSale(saleWithShift)
        recordSaleLines(lines)
        insertKitchenTicket(KitchenTicket(sale.id, "NEW", sale.createdAt, sale.createdAt))
        saveSaleCost(SaleCost(sale.id, totalIngredientCost))
        val paidCoffeeCount = lines.filter { catalogItem(it.itemId)?.isCoffee == true && it.unitPricePaise > 0L }.sumOf { it.quantity }
        if (loyaltyBefore != null) {
            saveLoyaltyAccount(loyaltyBefore.copy(
                freeCoffeesAvailable = loyaltyBefore.freeCoffeesAvailable - 1,
                updatedAt = sale.createdAt
            ))
        }
        if (sale.customerPhone.matches(Regex("[0-9]{10}")) && paidCoffeeCount > 0) {
            val previous = loyaltyAccount(sale.customerPhone)
            val total = (previous?.coffeeProgress ?: 0) + paidCoffeeCount
            val earned = total / 10
            saveLoyaltyAccount(LoyaltyAccount(
                phone = sale.customerPhone,
                customerName = previous?.customerName ?: sale.customerPhone,
                coffeeProgress = total % 10,
                freeCoffeesAvailable = (previous?.freeCoffeesAvailable ?: 0) + earned,
                lifetimeCoffeeCount = (previous?.lifetimeCoffeeCount ?: 0) + paidCoffeeCount,
                updatedAt = sale.createdAt
            ))
        }
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSaleCost(cost: SaleCost)
    @Query("SELECT * FROM sale_costs WHERE saleId = :saleId")
    suspend fun saleCost(saleId: String): SaleCost?
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun sales(): Flow<List<Sale>>
    @Query("SELECT * FROM sale_costs")
    fun saleCosts(): Flow<List<SaleCost>>
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
    @Query("SELECT * FROM wastage_records ORDER BY recordedAt DESC")
    fun wastageRecords(): Flow<List<WastageRecord>>

    @Query("SELECT * FROM ingredients WHERE active = 1 ORDER BY name")
    fun ingredients(): Flow<List<Ingredient>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredient(item: Ingredient)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateIngredient(item: Ingredient)
    @Query("SELECT COUNT(*) FROM ingredients i WHERE i.active = 1 AND ((i.storeMinStockBaseUnits > 0 AND COALESCE((SELECT SUM(remainingBaseUnits) FROM stock_lots WHERE ingredientId = i.id AND location = 'STORE'), 0) < i.storeMinStockBaseUnits) OR (i.kitchenMinStockBaseUnits > 0 AND COALESCE((SELECT SUM(remainingBaseUnits) FROM stock_lots WHERE ingredientId = i.id AND location = 'KITCHEN'), 0) < i.kitchenMinStockBaseUnits))")
    fun lowStockCount(): Flow<Int>
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
    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC")
    fun stockMovements(): Flow<List<StockMovement>>
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
                totalCost = Math.addExact(totalCost, Math.multiplyExact(take, lot.unitCostPaise) / 1000L)
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
        val ingredient = ingredient(item.ingredientId) ?: error("Wastage ingredient not found.")
        if (ingredient.isCoffeePowder) {
            require(ingredient.baseUnit == "g" && item.quantityBaseUnits % 250000L == 0L) {
                "Coffee powder wastage must be recorded in 250 g multiples."
            }
        }
        val cost = consumeFifo(item.ingredientId, item.location, item.quantityBaseUnits, item.id, "WASTAGE", item.recordedAt)
        insertWastage(item.copy(costPaise = cost))
    }

    @Transaction
    suspend fun receivePurchase(purchase: Purchase, lines: List<PurchaseLine>, lots: List<StockLot>) {
        require(lines.isNotEmpty() && lines.size == lots.size)
        require(lines.all { line -> line.purchaseId == purchase.id && line.quantityBaseUnits > 0 && line.unitCostPaise >= 0 })
        require(lines.map { it.id }.distinct().size == lines.size && lines.map { it.lotId }.distinct().size == lines.size)
        require(lines.zip(lots).all { (line, lot) -> line.ingredientId == lot.ingredientId && lot.location == "STORE" && lot.remainingBaseUnits == line.quantityBaseUnits && lot.qtyBaseUnits == line.quantityBaseUnits && lot.id == line.lotId && lot.unitCostPaise == line.unitCostPaise })
        require(lines.fold(0L) { total, line -> Math.addExact(total, line.lineTotalPaise) } == purchase.totalPaise) { "Purchase total does not match its lines." }
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
        require(transfer.fromLocation in listOf("STORE", "KITCHEN") && transfer.toLocation in listOf("STORE", "KITCHEN"))
        require(transfer.fromLocation != transfer.toLocation)
        if (item.isCoffeePowder) require(quantityBaseUnits % 250000L == 0L) { "Coffee powder transfers must be in 250 g multiples" }
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

@Database(entities = [CatalogItem::class, Ingredient::class, StockLot::class, Purchase::class, PurchaseLine::class, StockTransfer::class, StockMovement::class, ProductionBatch::class, ProductionInput::class, WastageRecord::class, Sale::class, SaleLine::class, SopDocument::class, SopCheck::class, RecipeLine::class, SaleCost::class, StaffMember::class, OperationalRecord::class, StockReconciliation::class, LoyaltyAccount::class, CashShift::class, CashMovement::class, Supplier::class, NextDayPlan::class, ComboComponent::class, KitchenTicket::class, HeldOrder::class, HeldOrderLine::class, SalesReconciliation::class], version = 24, exportSchema = true)
abstract class PaanagamDatabase : RoomDatabase() {
    abstract fun dao(): PaanagamDao
}
