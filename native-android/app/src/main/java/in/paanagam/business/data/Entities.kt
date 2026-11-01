package in.paanagam.business.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_items")
data class CatalogItem(@PrimaryKey val id: String, val name: String, val category: String, val pricePaise: Long, val active: Boolean = true)

@Entity(tableName = "stock_lots")
data class StockLot(@PrimaryKey val id: String, val ingredientId: String, val location: String, val receivedAt: Long, val qtyBaseUnits: Long, val remainingBaseUnits: Long, val unit: String, val unitCostPaise: Long)

@Entity(tableName = "sales")
data class Sale(@PrimaryKey val id: String, val createdAt: Long, val totalPaise: Long, val paymentMethod: String, val status: String = "COMPLETED", val syncState: String = "LOCAL")

@Entity(tableName = "sop_documents")
data class SopDocument(@PrimaryKey val id: String, val title: String, val category: String, val version: Int, val status: String, val instructions: String, val updatedAt: Long)

@Entity(tableName = "sop_checks")
data class SopCheck(@PrimaryKey val id: String, val sopId: String, val completedBy: String, val completedAt: Long, val notes: String = "")

@Entity(tableName = "sale_lines")
data class SaleLine(@PrimaryKey val id: String, val saleId: String, val itemId: String, val itemName: String, val quantity: Int, val unitPricePaise: Long, val lineTotalPaise: Long)
