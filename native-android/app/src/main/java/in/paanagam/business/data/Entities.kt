package `in`.paanagam.business.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_items")
data class CatalogItem(@PrimaryKey val id: String, val name: String, val category: String, val pricePaise: Long, val active: Boolean = true)

@Entity(tableName = "ingredients")
data class Ingredient(@PrimaryKey val id: String, val name: String, val baseUnit: String, val isCoffeePowder: Boolean = false, val active: Boolean = true, val storeMinStockBaseUnits: Long = 0L, val kitchenMinStockBaseUnits: Long = 0L)

@Entity(tableName = "purchases")
data class Purchase(@PrimaryKey val id: String, val supplierName: String, val invoiceNumber: String, val purchasedAt: Long, val totalPaise: Long, val status: String = "RECEIVED", val supplierId: String? = null)

@Entity(tableName = "purchase_lines")
data class PurchaseLine(@PrimaryKey val id: String, val purchaseId: String, val ingredientId: String, val quantityBaseUnits: Long, val unitCostPaise: Long, val lineTotalPaise: Long, val lotId: String)

@Entity(tableName = "stock_transfers")
data class StockTransfer(@PrimaryKey val id: String, val fromLocation: String, val toLocation: String, val transferredAt: Long, val status: String = "COMPLETED")

@Entity(tableName = "stock_movements")
data class StockMovement(@PrimaryKey val id: String, val ingredientId: String, val lotId: String, val movementType: String, val fromLocation: String, val toLocation: String, val quantityBaseUnits: Long, val unitCostPaise: Long, val referenceId: String, val createdAt: Long)

@Entity(tableName = "stock_lots")
data class StockLot(@PrimaryKey val id: String, val ingredientId: String, val location: String, val receivedAt: Long, val qtyBaseUnits: Long, val remainingBaseUnits: Long, val unit: String, val unitCostPaise: Long, val sourceLotId: String? = null)

@Entity(tableName = "production_batches")
data class ProductionBatch(@PrimaryKey val id: String, val productName: String, val producedAt: Long, val inputPowderGrams: Long, val inputWaterMl: Long, val outputDecoctionMl: Long, val retainedGroundsMl: Long, val status: String = "COMPLETED")

@Entity(tableName = "production_inputs")
data class ProductionInput(@PrimaryKey val id: String, val batchId: String, val ingredientId: String, val quantityBaseUnits: Long, val costPaise: Long)

@Entity(tableName = "wastage_records")
data class WastageRecord(@PrimaryKey val id: String, val ingredientId: String, val location: String, val quantityBaseUnits: Long, val reason: String, val recordedAt: Long, val costPaise: Long)

@Entity(tableName = "sales")
data class Sale(@PrimaryKey val id: String, val createdAt: Long, val totalPaise: Long, val paymentMethod: String, val status: String = "COMPLETED", val syncState: String = "LOCAL", val customerPhone: String = "", val shiftId: String? = null)

@Entity(tableName = "sop_documents")
data class SopDocument(@PrimaryKey val id: String, val title: String, val category: String, val version: Int, val status: String, val instructions: String, val updatedAt: Long)

@Entity(tableName = "sop_checks")
data class SopCheck(@PrimaryKey val id: String, val sopId: String, val completedBy: String, val completedAt: Long, val notes: String = "")

@Entity(tableName = "sale_lines")
data class SaleLine(@PrimaryKey val id: String, val saleId: String, val itemId: String, val itemName: String, val quantity: Int, val unitPricePaise: Long, val lineTotalPaise: Long)

@Entity(tableName = "recipe_lines")
data class RecipeLine(@PrimaryKey val id: String, val menuItemId: String, val ingredientId: String, val quantityBaseUnits: Long)

@Entity(tableName = "sale_costs")
data class SaleCost(@PrimaryKey val saleId: String, val ingredientCostPaise: Long)

@Entity(tableName = "staff_members")
data class StaffMember(@PrimaryKey val id: String, val name: String, val role: String, val pinSalt: String, val pinHash: String, val active: Boolean = true, val createdAt: Long)

@Entity(tableName = "operational_records")
data class OperationalRecord(
    @PrimaryKey val id: String,
    val module: String,
    val title: String,
    val details: String,
    val amountPaise: Long,
    val status: String,
    val createdAt: Long
)


@Entity(tableName = "stock_reconciliations")
data class StockReconciliation(
    @PrimaryKey val id: String,
    val ingredientId: String,
    val ingredientName: String,
    val location: String,
    val systemQtyBaseUnits: Long,
    val physicalQtyBaseUnits: Long,
    val varianceBaseUnits: Long,
    val adjustmentCostPaise: Long,
    val note: String,
    val reconciledAt: Long
)

@Entity(tableName = "loyalty_accounts")
data class LoyaltyAccount(@PrimaryKey val phone: String, val customerName: String, val coffeeProgress: Int = 0, val freeCoffeesAvailable: Int = 0, val lifetimeCoffeeCount: Int = 0, val updatedAt: Long)

@Entity(tableName = "cash_shifts")
data class CashShift(
    @PrimaryKey val id: String,
    val openedAt: Long,
    val openingFloatPaise: Long,
    val closedAt: Long? = null,
    val expectedCashPaise: Long? = null,
    val countedCashPaise: Long? = null,
    val variancePaise: Long? = null,
    val notes: String = "",
    val status: String = "OPEN"
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey val id: String,
    val name: String,
    val contactPerson: String = "",
    val phone: String = "",
    val gstin: String = "",
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long
)

@Entity(tableName = "next_day_plans")
data class NextDayPlan(
    @PrimaryKey val id: String,
    val planDate: String,
    val menuItemId: String,
    val menuItemName: String,
    val plannedQuantity: Int,
    val status: String = "PLANNED",
    val createdAt: Long
)
