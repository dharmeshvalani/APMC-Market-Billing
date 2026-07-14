package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "FARMER" or "TRADER"
    val phone: String,
    val address: String,
    val licenseNo: String? = null,
    val bankDetails: String? = null,
    val balance: Double = 0.0, // positive is receivable, negative is payable
    val isSynced: Boolean = true
)

@Entity(tableName = "commodities")
data class Commodity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val marketFeePercent: Double = 1.0,  // e.g. 1.0%
    val commissionPercent: Double = 5.0, // e.g. 5.0%
    val stockBags: Int = 0,
    val currentRate: Double = 0.0,
    val category: String = "General",
    val barcode: String? = null,
    val gstPercent: Double = 0.0,
    val hsnCode: String? = null,
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val isSynced: Boolean = true
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val billNumber: String,
    val date: Long = System.currentTimeMillis(),
    val partyId: Int, // Farmer ID for Patti, Trader ID for Sale Invoice
    val partyName: String, // Denormalized for easy viewing
    val partyType: String, // "FARMER" (Patti / Purchase) or "TRADER" (Sale Bill)
    val commodityId: Int,
    val commodityName: String, // Denormalized
    val bagCount: Int,
    val weight: Double, // in Quintals (100 kgs) or Kgs
    val rate: Double, // per Quintal or per Kg
    val grossAmount: Double, // weight * rate
    val commissionAmount: Double,
    val laborCharges: Double = 0.0, // Hamali / labor
    val transportCharges: Double = 0.0,
    val marketFeeAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val roundOff: Double = 0.0,
    val netAmount: Double, // grossAmount +/- other additions/deductions
    val isPaid: Boolean = false,
    val paymentNotes: String = "",
    val isSynced: Boolean = true
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val partyId: Int,
    val date: Long = System.currentTimeMillis(),
    val entryType: String, // "DEBIT" or "CREDIT"
    val amount: Double,
    val referenceId: String = "", // bill number or manual payment receipt id
    val description: String = ""
)

@Entity(tableName = "inventory_transactions")
data class InventoryTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val commodityId: Int,
    val commodityName: String,
    val type: String, // "STOCK_IN", "STOCK_OUT", "PURCHASE_ENTRY", "ADJUST_STOCK"
    val bags: Int, // bag count changed
    val totalWeight: Double, // in Quintals
    val ratePerQtl: Double = 0.0,
    val totalPrice: Double = 0.0,
    val partyId: Int? = null,
    val partyName: String? = null,
    val date: Long = System.currentTimeMillis(),
    val remarks: String = "",
    val referenceNo: String? = null,
    val isSynced: Boolean = true
)

data class DashboardActivity(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val amount: Double,
    val type: String, // "BILL" or "PAYMENT"
    val isPositive: Boolean
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String, // "Labor", "Transport", "Rent", "Office Supplies", "Mandi Levy", "Other"
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val isSynced: Boolean = true
)

