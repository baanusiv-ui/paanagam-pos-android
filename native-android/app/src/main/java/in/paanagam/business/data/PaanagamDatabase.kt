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
    @Query("SELECT COALESCE(SUM(cashPaidPaise), 0) FROM sales WHERE status IN ('COMPLETED', 'PARTIALLY_REFUNDED', 'REFUNDED') AND shiftId = :shiftId")
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
        require(isCoffeePowderReductionAllowed(item.isCoffeePowder, item.baseUnit, variance)) {
            "Coffee powder stock reductions must be in 250 g multiples."
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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoyaltyLedgerEntry(entry: LoyaltyLedgerEntry)
    @Query("SELECT * FROM loyalty_ledger WHERE phone = :phone ORDER BY createdAt, id")
    suspend fun loyaltyLedgerEntries(phone: String): List<LoyaltyLedgerEntry>
    @Query("SELECT * FROM loyalty_ledger WHERE saleId = :saleId AND entryType != 'REVERSAL' ORDER BY createdAt, id")
    suspend fun loyaltyLedgerEntriesForSale(saleId: String): List<LoyaltyLedgerEntry>
    @Query("SELECT * FROM sales WHERE customerPhone = :phone ORDER BY createdAt, id")
    suspend fun salesForCustomer(phone: String): List<Sale>
    @Query("SELECT * FROM sale_refunds WHERE saleId = :saleId ORDER BY refundedAt")
    suspend fun refundHistoryForSale(saleId: String): List<SaleRefund>
    @Query("SELECT * FROM sale_refunds ORDER BY refundedAt DESC")
    fun allSaleRefunds(): Flow<List<SaleRefund>>
    @Query("SELECT reversesEntryId FROM loyalty_ledger WHERE reversesEntryId IS NOT NULL")
    suspend fun reversedLoyaltyEntryIds(): List<String?>
    @Query("SELECT * FROM loyalty_accounts WHERE phone = :phone")
    suspend fun loyaltyAccount(phone: String): LoyaltyAccount?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLoyaltyAccount(account: LoyaltyAccount)
    @Query("SELECT * FROM device_binding WHERE id = 1")
    suspend fun deviceBinding(): DeviceBinding?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDeviceBinding(binding: DeviceBinding)

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
    @Query("SELECT * FROM catalog_items ORDER BY active DESC, category, name")
    fun allCatalog(): Flow<List<CatalogItem>>
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
        require(lines.map { it.ingredientId }.distinct().size == lines.size) {
            "A recipe can contain each ingredient only once. Combine duplicate ingredient quantities before saving."
        }
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
        val validLoyaltyPhone = sale.customerPhone.matches(Regex("[0-9]{10}"))
        if (validLoyaltyPhone) ensureLoyaltyLedgerSeeded(sale.customerPhone, sale.createdAt)
        val loyaltyBefore = if (rewardLines.isNotEmpty()) {
            require(validLoyaltyPhone) { "Enter the customer's 10-digit mobile number to redeem." }
            loyaltyAccount(sale.customerPhone)?.also { require(it.freeCoffeesAvailable > 0) { "No free coffee reward is available." } }
                ?: error("No loyalty account found for this mobile number.")
        } else null
        var totalIngredientCost = 0L
        lines.forEach { line ->
            require(line.quantity > 0)
            // Build one menu-unit recipe, then consume it unit-by-unit. This preserves
            // exact FIFO lot attribution for every sale unit, enabling safe quantity refunds.
            val perUnitRequirements = mutableMapOf<String, Long>()
            suspend fun expandMenuRequirements(menuItemId: String, multiplier: Long, path: Set<String>) {
                require(menuItemId !in path) { "Circular combo detected. Correct the combo components before checkout." }
                require(multiplier > 0L) { "Combo component quantity must be positive." }
                val nextPath = path + menuItemId
                val components = comboComponents(menuItemId)
                if (components.isNotEmpty()) {
                    components.forEach { component ->
                        require(component.quantity > 0) { "Combo component quantity is invalid." }
                        require(catalogItem(component.componentItemId) != null) { "Combo component ${component.componentName} is unavailable." }
                        expandMenuRequirements(
                            component.componentItemId,
                            Math.multiplyExact(multiplier, component.quantity.toLong()),
                            nextPath
                        )
                    }
                } else {
                    val configuredRecipe = recipeLines(menuItemId)
                    require(configuredRecipe.isNotEmpty()) {
                        "Recipe missing for ${catalogItem(menuItemId)?.name ?: menuItemId}. Configure its ingredients before selling."
                    }
                    configuredRecipe.forEach { recipe ->
                        require(recipe.quantityBaseUnits > 0L) { "Recipe quantity must be positive." }
                        val required = Math.multiplyExact(recipe.quantityBaseUnits, multiplier)
                        perUnitRequirements[recipe.ingredientId] = Math.addExact(perUnitRequirements[recipe.ingredientId] ?: 0L, required)
                    }
                }
            }
            expandMenuRequirements(line.itemId, 1L, emptySet())
            perUnitRequirements.forEach { (ingredientId, _) ->
                val ingredient = ingredient(ingredientId) ?: error("Recipe ingredient not found.")
                require(!ingredient.isCoffeePowder) {
                    "Coffee powder is a production ingredient. Prepare decoction first; POS recipes must consume decoction, milk and sugar."
                }
            }
            for (unitIndex in 0 until line.quantity) {
                perUnitRequirements.forEach { (ingredientId, requiredPerUnit) ->
                    consumeFifo(
                        ingredientId, "KITCHEN", requiredPerUnit, sale.id, "SALE_CONSUMPTION",
                        sale.createdAt, line.id, unitIndex
                    )
                }
            }
        }
        // Aggregate cost at the same sale-line/ingredient boundary as the original
        // FIFO costing rule, avoiding a per-unit rounding loss when splitting lots.
        val consumedMovements = saleConsumptionMovements(sale.id)
        totalIngredientCost = consumedMovements
            .groupBy { it.saleLineId to it.ingredientId }
            .values
            .fold(0L) { total, movements ->
                Math.addExact(total, calculateFifoCostPaise(movements.map { it.quantityBaseUnits to it.unitCostPaise }))
            }
        recordSale(saleWithShift)
        recordSaleLines(lines)
        insertKitchenTicket(KitchenTicket(sale.id, "NEW", sale.createdAt, sale.createdAt))
        saveSaleCost(SaleCost(sale.id, totalIngredientCost))
        val paidCoffeeCount = lines.filter { catalogItem(it.itemId)?.isCoffee == true && it.unitPricePaise > 0L }.sumOf { it.quantity }
        if (validLoyaltyPhone) {
            if (loyaltyBefore != null) insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(UUID.randomUUID().toString(), sale.customerPhone, sale.id, "REDEEM", 0, -1, 0, sale.createdAt))
            if (paidCoffeeCount > 0) insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(UUID.randomUUID().toString(), sale.customerPhone, sale.id, "EARN", paidCoffeeCount, 0, paidCoffeeCount, sale.createdAt))
            rebuildLoyaltyAccount(sale.customerPhone, sale.createdAt)
        }
    }
    private suspend fun ensureLoyaltyLedgerSeeded(phone: String, at: Long) {
        if (loyaltyLedgerEntries(phone).isNotEmpty()) return
        val historicalSales = salesForCustomer(phone)
        if (historicalSales.isEmpty()) {
            val existing = loyaltyAccount(phone)
            if (existing != null) insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(UUID.randomUUID().toString(), phone, "", "OPENING_BALANCE", existing.coffeeProgress, existing.freeCoffeesAvailable, existing.lifetimeCoffeeCount, minOf(existing.updatedAt, at)))
            return
        }
        var reconstructedEntries = 0
        historicalSales.forEach { historicalSale ->
            val saleLinesForHistory = saleLines(historicalSale.id)
            val paid = saleLinesForHistory.filter { catalogItem(it.itemId)?.isCoffee == true && it.unitPricePaise > 0L }.sumOf { it.quantity }
            val redeemed = saleLinesForHistory.filter { catalogItem(it.itemId)?.isCoffee == true && it.unitPricePaise == 0L }.sumOf { it.quantity }
            val originals = mutableListOf<LoyaltyLedgerEntry>()
            if (paid > 0) originals += LoyaltyLedgerEntry(UUID.randomUUID().toString(), phone, historicalSale.id, "EARN", paid, 0, paid, historicalSale.createdAt)
            if (redeemed > 0) originals += LoyaltyLedgerEntry(UUID.randomUUID().toString(), phone, historicalSale.id, "REDEEM", 0, -redeemed, 0, historicalSale.createdAt)
            originals.forEach { insertLoyaltyLedgerEntry(it) }
            reconstructedEntries += originals.size
            if (historicalSale.status == "REFUNDED") {
                val refundAt = refundHistoryForSale(historicalSale.id).firstOrNull()?.refundedAt ?: historicalSale.createdAt
                originals.forEach { entry ->
                    insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(UUID.randomUUID().toString(), phone, historicalSale.id, "REVERSAL", -entry.coffeeDelta, -entry.rewardDelta, -entry.lifetimeDelta, refundAt, entry.id))
                }
            } else if (historicalSale.status == "PARTIALLY_REFUNDED") {
                // Reconstruct legacy partial refunds as a single auditable delta.
                // Fully refunded sales are handled above to avoid double reversal.
                val lineById = saleLinesForHistory.associateBy { it.id }
                val refundedRows = saleLineRefunds(historicalSale.id)
                var paidCoffeeRefunded = 0
                var freeCoffeeRefunded = 0
                refundedRows.forEach { refund ->
                    val line = lineById[refund.saleLineId] ?: return@forEach
                    if (catalogItem(line.itemId)?.isCoffee == true) {
                        if (line.unitPricePaise > 0L) paidCoffeeRefunded = Math.addExact(paidCoffeeRefunded, refund.refundedQuantity)
                        else freeCoffeeRefunded = Math.addExact(freeCoffeeRefunded, refund.refundedQuantity)
                    }
                }
                if (paidCoffeeRefunded > 0 || freeCoffeeRefunded > 0) {
                    val refundAt = refundedRows.maxOfOrNull { it.refundedAt } ?: historicalSale.createdAt
                    insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(
                        UUID.randomUUID().toString(), phone, historicalSale.id, "PARTIAL_REFUND",
                        -paidCoffeeRefunded, freeCoffeeRefunded, -paidCoffeeRefunded, refundAt
                    ))
                }
            }
        }
        if (reconstructedEntries == 0) {
            val existing = loyaltyAccount(phone)
            if (existing != null) insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(UUID.randomUUID().toString(), phone, "", "OPENING_BALANCE", existing.coffeeProgress, existing.freeCoffeesAvailable, existing.lifetimeCoffeeCount, minOf(existing.updatedAt, at)))
        }
        rebuildLoyaltyAccount(phone, at)
    }

    private suspend fun rebuildLoyaltyAccount(phone: String, at: Long) {
        val entries = loyaltyLedgerEntries(phone)
        val previous = loyaltyAccount(phone)
        val balance = LoyaltyLedgerRules.calculate(entries)
        saveLoyaltyAccount(LoyaltyAccount(
            phone = phone,
            customerName = previous?.customerName ?: phone,
            coffeeProgress = balance.coffeeProgress,
            freeCoffeesAvailable = balance.freeCoffeesAvailable,
            lifetimeCoffeeCount = balance.lifetimeCoffeeCount,
            updatedAt = at
        ))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSaleCost(cost: SaleCost)
    @Query("SELECT * FROM sale_costs WHERE saleId = :saleId")
    suspend fun saleCost(saleId: String): SaleCost?
    @Query("SELECT * FROM sales WHERE id = :saleId")
    suspend fun saleById(saleId: String): Sale?
    @Query("UPDATE sales SET status = :status WHERE id = :saleId")
    suspend fun updateSaleStatus(saleId: String, status: String)
    @Query("SELECT * FROM stock_movements WHERE referenceId = :saleId AND movementType = 'SALE_CONSUMPTION'")
    suspend fun saleConsumptionMovements(saleId: String): List<StockMovement>
    @Query("SELECT * FROM stock_movements WHERE saleLineId = :saleLineId AND saleUnitIndex = :unitIndex AND movementType = 'SALE_CONSUMPTION' ORDER BY ingredientId, lotId")
    suspend fun saleUnitConsumptionMovements(saleLineId: String, unitIndex: Int): List<StockMovement>
    @Query("SELECT * FROM sale_line_refunds WHERE saleId = :saleId ORDER BY refundedAt, saleLineId, saleUnitIndex")
    suspend fun saleLineRefunds(saleId: String): List<SaleLineRefund>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleLineRefund(refund: SaleLineRefund)
    @Query("SELECT * FROM stock_lots WHERE id = :lotId")
    suspend fun stockLot(lotId: String): StockLot?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleRefund(refund: SaleRefund)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCctvAuditEvent(event: CctvAuditEvent)
    @Query("SELECT * FROM cctv_audit_events ORDER BY createdAt DESC")
    fun cctvAuditEvents(): Flow<List<CctvAuditEvent>>
    @Query("SELECT * FROM sale_refunds WHERE saleId = :saleId ORDER BY refundedAt DESC")
    suspend fun saleRefunds(saleId: String): List<SaleRefund>
    @Transaction
    suspend fun refundCompletedSale(saleId: String, reason: String, refundedAt: Long, cashRefundPaise: Long, upiRefundPaise: Long, cashierId: String, cashierName: String) {
        require(reason.isNotBlank()) { "Enter a food refund reason. This is required for the wastage record." }
        val sale = saleById(saleId) ?: error("Sale not found.")
        require(sale.status == "COMPLETED") { "Only completed, not-yet-refunded sales can be refunded." }
        require(isRefundSplitValid(sale.totalPaise, sale.cashPaidPaise, sale.upiPaidPaise, cashRefundPaise, upiRefundPaise)) {
            "Refund must preserve the original payment method split and equal the original bill total."
        }
        if (sale.customerPhone.matches(Regex("[0-9]{10}"))) {
            ensureLoyaltyLedgerSeeded(sale.customerPhone, refundedAt)
            val reversed = reversedLoyaltyEntryIds().filterNotNull().toSet()
            loyaltyLedgerEntriesForSale(saleId).filter { it.id !in reversed }.forEach { entry ->
                insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(UUID.randomUUID().toString(), entry.phone, saleId, "REVERSAL", -entry.coffeeDelta, -entry.rewardDelta, -entry.lifetimeDelta, refundedAt, entry.id))
            }
            rebuildLoyaltyAccount(sale.customerPhone, refundedAt)
        }
        val movements = saleConsumptionMovements(saleId)
        require(movements.isNotEmpty()) { "No original stock consumption was found. Refund blocked to avoid an incomplete stock reversal." }
        // Match the original sale-cost rounding boundary (sale line + ingredient).
        // These records document food loss; the original sale already consumed stock.
        val refundLineNames = saleLines(saleId).associate { it.id to it.itemName }
        movements.groupBy { it.saleLineId to it.ingredientId }.forEach { (lineAndIngredient, ingredientMovements) ->
            val (lineId, ingredientId) = lineAndIngredient
            val lots = ingredientMovements.map { movement ->
                val lot = stockLot(movement.lotId) ?: error("Original FIFO lot is missing. Refund blocked.")
                require(lot.ingredientId == movement.ingredientId && lot.location == movement.fromLocation && movement.quantityBaseUnits > 0)
                lot
            }
            require(lots.map { it.location }.distinct().size == 1)
            val quantity = ingredientMovements.fold(0L) { total, movement -> Math.addExact(total, movement.quantityBaseUnits) }
            val cost = calculateFifoCostPaise(ingredientMovements.map { it.quantityBaseUnits to it.unitCostPaise })
            val itemName = lineId?.let { refundLineNames[it] } ?: "Legacy sale item"
            insertWastage(WastageRecord(
                UUID.randomUUID().toString(), ingredientId, lots.first().location, quantity,
                "FOOD REFUND • Sale $saleId • $itemName: ${reason.trim()}", refundedAt, cost,
                sourceType = "FOOD_REFUND", sourceId = saleId, sourceLineId = lineId, stockAdjusted = false
            ))
        }
        if (cashRefundPaise > 0) {
            val shift = getOpenCashShift() ?: error("Open a cash shift before issuing a cash refund.")
            insertCashMovement(CashMovement(UUID.randomUUID().toString(), shift.id, "REFUND", cashRefundPaise, "Sale $saleId • $reason", refundedAt))
        }
        insertSaleRefund(SaleRefund(UUID.randomUUID().toString(), saleId, refundedAt, sale.totalPaise, cashRefundPaise, upiRefundPaise, reason.trim()))
        updateSaleStatus(saleId, "REFUNDED")
        updateKitchenTicketStatus(saleId, "CANCELLED", refundedAt)
        insertCctvAuditEvent(CctvAuditEvent(UUID.randomUUID().toString(), saleId, "SALE_REFUND", reason.trim(), sale.totalPaise, cashierId, cashierName, refundedAt, "LOCAL_BOOKMARK"))
    }
    /**
     * Refunds only the still-unrefunded units on a bill. Every unit goes through the
     * same FIFO trace, tender allocation, loyalty reversal, wastage and CCTV audit
     * path as an ordinary item refund. The outer transaction makes the operation
     * all-or-nothing if any legacy line lacks unit traceability or cash shift.
     */
    @Transaction
    suspend fun refundRemainingSaleBalance(
        saleId: String,
        reason: String,
        refundedAt: Long,
        cashierId: String,
        cashierName: String
    ) {
        require(reason.isNotBlank()) { "Enter a food refund reason." }
        val sale = saleById(saleId) ?: error("Sale not found.")
        require(sale.status == "COMPLETED" || sale.status == "PARTIALLY_REFUNDED") {
            "Only completed or partially refunded sales can receive a remaining-balance refund."
        }
        val lines = saleLines(saleId)
        require(lines.isNotEmpty()) { "No bill lines found." }
        val priorRows = saleRefunds(saleId)
        val priorAmount = priorRows.fold(0L) { total, row -> Math.addExact(total, row.amountPaise) }
        require(priorAmount < sale.totalPaise) { "This bill has no remaining refundable balance." }
        val alreadyRefunded = saleLineRefunds(saleId).groupBy { it.saleLineId }
        lines.forEach { line ->
            val refundedQuantity = alreadyRefunded[line.id].orEmpty().sumOf { it.refundedQuantity }
            val remainingQuantity = line.quantity - refundedQuantity
            if (remainingQuantity > 0) {
                refundSaleLineQuantity(
                    saleId = saleId,
                    saleLineId = line.id,
                    requestedQuantity = remainingQuantity,
                    reason = reason.trim(),
                    refundedAt = refundedAt,
                    cashierId = cashierId,
                    cashierName = cashierName
                )
            }
        }
        val finalSale = saleById(saleId) ?: error("Sale not found after refund.")
        require(finalSale.status == "REFUNDED") {
            "The bill still has refundable units. Check its item-level refund history."
        }
    }

    @Transaction
    suspend fun refundSaleLineQuantity(
        saleId: String,
        saleLineId: String,
        requestedQuantity: Int,
        reason: String,
        refundedAt: Long,
        cashierId: String,
        cashierName: String
    ) {
        require(reason.isNotBlank()) { "Enter a food refund reason." }
        require(requestedQuantity > 0) { "Refund quantity must be at least one." }
        val sale = saleById(saleId) ?: error("Sale not found.")
        require(sale.status == "COMPLETED" || sale.status == "PARTIALLY_REFUNDED") {
            "Only completed or partially refunded sales can receive another item refund."
        }
        val line = saleLines(saleId).firstOrNull { it.id == saleLineId } ?: error("Sale line does not belong to this bill.")
        val allSaleMovements = saleConsumptionMovements(saleId)
        val allLineMovements = allSaleMovements.filter { it.saleLineId == line.id }
        require(allLineMovements.isNotEmpty() && allLineMovements.all { it.saleUnitIndex != null && it.saleUnitIndex in 0 until line.quantity }) {
            "This bill predates per-unit FIFO traceability. Item refunds are blocked rather than guessing ingredient quantities."
        }
        val previousLineRefunds = saleLineRefunds(saleId).filter { it.saleLineId == saleLineId }
        val alreadyRefundedIndexes = previousLineRefunds.map { it.saleUnitIndex }.toSet()
        require(alreadyRefundedIndexes.none { it < 0 }) { "Legacy refund records do not contain unit traceability." }
        val availableIndexes = (0 until line.quantity).filterNot { it in alreadyRefundedIndexes }
        require(requestedQuantity <= availableIndexes.size) {
            "Refund quantity exceeds the remaining quantity for this item."
        }
        val refundIndexes = availableIndexes.take(requestedQuantity)
        val saleRefundsBefore = saleRefunds(saleId)
        val previousAmount = saleRefundsBefore.fold(0L) { total, item -> Math.addExact(total, item.amountPaise) }
        val previousCash = saleRefundsBefore.fold(0L) { total, item -> Math.addExact(total, item.cashRefundPaise) }
        val previousUpi = saleRefundsBefore.fold(0L) { total, item -> Math.addExact(total, item.upiRefundPaise) }
        require(Math.addExact(previousAmount, Math.multiplyExact(line.unitPricePaise, requestedQuantity.toLong())) <= sale.totalPaise) {
            "Refund exceeds the remaining bill value."
        }
        if (sale.customerPhone.matches(Regex("[0-9]{10}"))) ensureLoyaltyLedgerSeeded(sale.customerPhone, refundedAt)
        val isCoffee = catalogItem(line.itemId)?.isCoffee == true
        var addedCash = 0L
        var addedUpi = 0L
        var addedAmount = 0L
        refundIndexes.forEach { unitIndex ->
            val unitMovements = allLineMovements.filter { it.saleUnitIndex == unitIndex }
            require(unitMovements.isNotEmpty() && unitMovements.all { it.quantityBaseUnits > 0 }) {
                "Exact FIFO trace is unavailable for this sold unit. Refund blocked rather than guessing ingredient quantities."
            }
            val unitAmount = line.unitPricePaise
            val allocation = PartialRefundRules.allocateTender(
                sale.totalPaise, sale.cashPaidPaise, sale.upiPaidPaise,
                Math.addExact(previousAmount, addedAmount),
                Math.addExact(previousCash, addedCash),
                Math.addExact(previousUpi, addedUpi),
                unitAmount
            )
            unitMovements.groupBy { it.ingredientId }.forEach { (ingredientId, ingredientMovements) ->
                val lots = ingredientMovements.map { movement ->
                    val lot = stockLot(movement.lotId) ?: error("Original FIFO lot is missing. Refund blocked.")
                    require(lot.ingredientId == ingredientId && lot.location == movement.fromLocation)
                    lot
                }
                require(lots.map { it.location }.distinct().size == 1)
                val ingredientLineMovements = allLineMovements.filter { it.ingredientId == ingredientId }
                val exactUnitCost = calculateFifoUnitCostPaise(
                    unitIndex,
                    ingredientLineMovements.map { Triple(it.saleUnitIndex ?: -1, it.quantityBaseUnits, it.unitCostPaise) }
                )
                val quantity = ingredientMovements.fold(0L) { total, movement -> Math.addExact(total, movement.quantityBaseUnits) }
                insertWastage(WastageRecord(
                    UUID.randomUUID().toString(), ingredientId, lots.first().location, quantity,
                    "FOOD REFUND • Sale $saleId • ${line.itemName} • Unit ${unitIndex + 1}: ${reason.trim()}",
                    refundedAt, exactUnitCost,
                    sourceType = "FOOD_REFUND", sourceId = saleId, sourceLineId = line.id, stockAdjusted = false
                ))
            }
            insertSaleLineRefund(SaleLineRefund(
                UUID.randomUUID().toString(), saleId, line.id, 1, unitIndex,
                unitAmount, allocation.cashPaise, allocation.upiPaise, "WASTED",
                reason.trim(), refundedAt
            ))
            insertSaleRefund(SaleRefund(
                UUID.randomUUID().toString(), saleId, refundedAt, unitAmount,
                allocation.cashPaise, allocation.upiPaise,
                "${line.itemName} ×1 • ${reason.trim()}"
            ))
            addedAmount = Math.addExact(addedAmount, unitAmount)
            addedCash = Math.addExact(addedCash, allocation.cashPaise)
            addedUpi = Math.addExact(addedUpi, allocation.upiPaise)
        }
        if (addedCash > 0L) {
            val shift = getOpenCashShift() ?: error("Open a cash shift before issuing a cash refund.")
            insertCashMovement(CashMovement(UUID.randomUUID().toString(), shift.id, "REFUND", addedCash, "Sale $saleId • ${line.itemName} • $reason", refundedAt))
        }
        if (isCoffee && sale.customerPhone.matches(Regex("[0-9]{10}"))) {
            val paidCoffee = line.unitPricePaise > 0L
            insertLoyaltyLedgerEntry(LoyaltyLedgerEntry(
                UUID.randomUUID().toString(), sale.customerPhone, saleId, "PARTIAL_REFUND",
                if (paidCoffee) -requestedQuantity else 0,
                if (paidCoffee) 0 else requestedQuantity,
                if (paidCoffee) -requestedQuantity else 0,
                refundedAt
            ))
            rebuildLoyaltyAccount(sale.customerPhone, refundedAt)
        }
        val allLines = saleLines(saleId)
        val refundedRows = saleLineRefunds(saleId)
        val allRefunded = allLines.all { saleLine ->
            refundedRows.filter { it.saleLineId == saleLine.id }.sumOf { it.refundedQuantity } >= saleLine.quantity
        }
        updateSaleStatus(saleId, if (allRefunded) "REFUNDED" else "PARTIALLY_REFUNDED")
        if (allRefunded) updateKitchenTicketStatus(saleId, "CANCELLED", refundedAt)
        insertCctvAuditEvent(CctvAuditEvent(
            UUID.randomUUID().toString(), saleId, "SALE_PARTIAL_REFUND", reason.trim(),
            addedAmount, cashierId, cashierName, refundedAt, "LOCAL_BOOKMARK"
        ))
    }

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
    suspend fun consumeFifo(ingredientId: String, location: String, quantity: Long, referenceId: String, movementType: String, at: Long, saleLineId: String? = null, saleUnitIndex: Int? = null): Long {
        require(quantity > 0)
        val lots = fifoLots(ingredientId, location)
        require(lots.sumOf { it.remainingBaseUnits } >= quantity) { "Insufficient stock" }
        var left = quantity
        val lotTakes = mutableListOf<Pair<Long, Long>>()
        lots.forEach { lot ->
            if (left > 0) {
                val take = minOf(left, lot.remainingBaseUnits)
                updateLotBalance(lot.id, lot.remainingBaseUnits - take)
                lotTakes += take to lot.unitCostPaise
                insertStockMovement(StockMovement(UUID.randomUUID().toString(), ingredientId, lot.id, movementType, location, "", take, lot.unitCostPaise, referenceId, at, saleLineId, saleUnitIndex))
                left -= take
            }
        }
        return calculateFifoCostPaise(lotTakes)
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
        val totalCost = Math.addExact(powderCost, waterCost)
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
        require(item.sourceType == "MANUAL" && item.sourceId == null && item.sourceLineId == null && item.stockAdjusted) {
            "Refund-linked wastage is audit-only and must not deduct stock a second time."
        }
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

@Database(entities = [CatalogItem::class, Ingredient::class, StockLot::class, Purchase::class, PurchaseLine::class, StockTransfer::class, StockMovement::class, ProductionBatch::class, ProductionInput::class, WastageRecord::class, Sale::class, SaleLine::class, SopDocument::class, SopCheck::class, RecipeLine::class, SaleCost::class, StaffMember::class, OperationalRecord::class, StockReconciliation::class, LoyaltyAccount::class, CashShift::class, CashMovement::class, Supplier::class, NextDayPlan::class, ComboComponent::class, KitchenTicket::class, HeldOrder::class, HeldOrderLine::class, SalesReconciliation::class, SaleRefund::class, SaleLineRefund::class, DeviceBinding::class, CctvAuditEvent::class, LoyaltyLedgerEntry::class], version = 33, exportSchema = true)
abstract class PaanagamDatabase : RoomDatabase() {
    abstract fun dao(): PaanagamDao
}
