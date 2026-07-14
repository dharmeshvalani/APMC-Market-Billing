package com.example.data

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

class BillingRepository(private val db: AppDatabase) {
    private val partyDao = db.partyDao()
    private val commodityDao = db.commodityDao()
    private val billDao = db.billDao()
    private val ledgerDao = db.ledgerDao()
    private val inventoryTransactionDao = db.inventoryTransactionDao()
    private val expenseDao = db.expenseDao()

    // Party flows
    val allParties: Flow<List<Party>> = partyDao.getAllParties()
    fun getPartiesByType(type: String): Flow<List<Party>> = partyDao.getPartiesByType(type)

    // Expense flow
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    // Commodity flows
    val allCommodities: Flow<List<Commodity>> = commodityDao.getAllCommodities()

    // Bill flows
    val allBills: Flow<List<Bill>> = billDao.getAllBills()
    fun getBillsByPartyType(partyType: String): Flow<List<Bill>> = billDao.getBillsByPartyType(partyType)
    fun getBillsByParty(partyId: Int): Flow<List<Bill>> = billDao.getBillsByParty(partyId)

    // Ledger flows
    val allLedgerEntries: Flow<List<LedgerEntry>> = ledgerDao.getAllLedgerEntries()
    fun getLedgerEntriesForParty(partyId: Int): Flow<List<LedgerEntry>> = ledgerDao.getLedgerEntriesForParty(partyId)

    // Inventory Transaction flows
    val allInventoryTransactions: Flow<List<InventoryTransaction>> = inventoryTransactionDao.getAllTransactions()

    // Basic CRUD Operations
    suspend fun insertParty(party: Party): Long = partyDao.insertParty(party)
    suspend fun updateParty(party: Party) = partyDao.updateParty(party)
    suspend fun deleteParty(party: Party) = partyDao.deleteParty(party)

    suspend fun insertCommodity(commodity: Commodity): Long = commodityDao.insertCommodity(commodity)
    suspend fun updateCommodity(commodity: Commodity) = commodityDao.updateCommodity(commodity)
    suspend fun deleteCommodity(commodity: Commodity) = commodityDao.deleteCommodity(commodity)

    // Dynamic APMC Billing Transactions
    suspend fun processBill(bill: Bill) {
        // Run as a single transaction using Room withTransaction
        db.withTransaction {
            // 1. Insert the bill with isSynced = false
            val insertedId = billDao.insertBill(bill.copy(isSynced = false))
            val finalBill = if (bill.billNumber.isEmpty()) {
                val paddedId = insertedId.toString().padStart(5, '0')
                val computedNo = if (bill.partyType == "FARMER") "F-$paddedId" else "T-$paddedId"
                val updatedBill = bill.copy(id = insertedId.toInt(), billNumber = computedNo, isSynced = false)
                billDao.insertBill(updatedBill) // update with computed bill number
                updatedBill
            } else {
                bill.copy(isSynced = false)
            }

            // 2. Adjust commodity stock and party balance based on partyType
            if (finalBill.partyType == "FARMER") {
                // Farmer Patti: Goods purchased/received from Farmer.
                // - Stock of Commodity goes UP
                commodityDao.adjustStock(finalBill.commodityId, finalBill.bagCount)
                
                // - We owe the Farmer money (payable). This decreases the Farmer's balance (receivable balance becomes negative/payable)
                partyDao.adjustPartyBalance(finalBill.partyId, -finalBill.netAmount)

                // - Record a Credit ledger entry for Farmer
                ledgerDao.insertLedgerEntry(
                    LedgerEntry(
                        partyId = finalBill.partyId,
                        date = finalBill.date,
                        entryType = "CREDIT",
                        amount = finalBill.netAmount,
                        referenceId = finalBill.billNumber,
                        description = "Produce Patti #${finalBill.billNumber} (${finalBill.bagCount} bags of ${finalBill.commodityName})"
                    )
                )

                // - Record an Inventory Transaction with isSynced = false
                inventoryTransactionDao.insertTransaction(
                    InventoryTransaction(
                        commodityId = finalBill.commodityId,
                        commodityName = finalBill.commodityName,
                        type = "PURCHASE_ENTRY",
                        bags = finalBill.bagCount,
                        totalWeight = finalBill.weight,
                        ratePerQtl = finalBill.rate,
                        totalPrice = finalBill.netAmount,
                        partyId = finalBill.partyId,
                        partyName = finalBill.partyName,
                        remarks = "Invoiced via Patti #${finalBill.billNumber}",
                        referenceNo = finalBill.billNumber,
                        date = finalBill.date,
                        isSynced = false
                    )
                )
            } else {
                // Trader Sale Bill: Goods sold to Trader.
                // - Stock of Commodity goes DOWN
                commodityDao.adjustStock(finalBill.commodityId, -finalBill.bagCount)

                // - Trader owes us money (receivable). This increases Trader's balance
                partyDao.adjustPartyBalance(finalBill.partyId, finalBill.netAmount)

                // - Record a Debit ledger entry for Trader
                ledgerDao.insertLedgerEntry(
                    LedgerEntry(
                        partyId = finalBill.partyId,
                        date = finalBill.date,
                        entryType = "DEBIT",
                        amount = finalBill.netAmount,
                        referenceId = finalBill.billNumber,
                        description = "Sale Invoice #${finalBill.billNumber} (${finalBill.bagCount} bags of ${finalBill.commodityName})"
                    )
                )

                // - Record an Inventory Transaction with isSynced = false
                inventoryTransactionDao.insertTransaction(
                    InventoryTransaction(
                        commodityId = finalBill.commodityId,
                        commodityName = finalBill.commodityName,
                        type = "STOCK_OUT",
                        bags = finalBill.bagCount,
                        totalWeight = finalBill.weight,
                        ratePerQtl = finalBill.rate,
                        totalPrice = finalBill.netAmount,
                        partyId = finalBill.partyId,
                        partyName = finalBill.partyName,
                        remarks = "Invoiced via Sale #${finalBill.billNumber}",
                        referenceNo = finalBill.billNumber,
                        date = finalBill.date,
                        isSynced = false
                    )
                )
            }
        }
    }

    suspend fun processInventoryChange(
        commodityId: Int,
        type: String, // "STOCK_IN", "STOCK_OUT", "PURCHASE_ENTRY", "ADJUST_STOCK"
        bagsDelta: Int, // positive to increase stock, negative to decrease stock
        totalWeight: Double,
        ratePerQtl: Double = 0.0,
        totalPrice: Double = 0.0,
        partyId: Int? = null,
        partyName: String? = null,
        remarks: String = "",
        referenceNo: String? = null
    ) {
        db.withTransaction {
            val commodity = commodityDao.getCommodityById(commodityId) ?: return@withTransaction
            
            // Adjust the actual stock in Commodity table
            commodityDao.adjustStock(commodityId, bagsDelta)

            // If it's a purchase entry, adjust Farmer balance and log to ledger
            if (type == "PURCHASE_ENTRY" && partyId != null) {
                val price = if (totalPrice > 0.0) totalPrice else (totalWeight * ratePerQtl)
                if (price > 0.0) {
                    partyDao.adjustPartyBalance(partyId, -price)
                    val ref = referenceNo ?: ("PUR-" + System.currentTimeMillis().toString().takeLast(6))
                    ledgerDao.insertLedgerEntry(
                        LedgerEntry(
                            partyId = partyId,
                            date = System.currentTimeMillis(),
                            entryType = "CREDIT",
                            amount = price,
                            referenceId = ref,
                            description = "Voucher Purchase: $bagsDelta bags of ${commodity.name} @ $ratePerQtl/qtl. $remarks"
                        )
                    )
                }
            } else if (type == "STOCK_OUT" && partyId != null) {
                // If it is an out flow with a Trader party, adjust Trader balance and log to ledger
                val price = if (totalPrice > 0.0) totalPrice else (totalWeight * ratePerQtl)
                if (price > 0.0) {
                    partyDao.adjustPartyBalance(partyId, price)
                    val ref = referenceNo ?: ("SLS-" + System.currentTimeMillis().toString().takeLast(6))
                    ledgerDao.insertLedgerEntry(
                        LedgerEntry(
                            partyId = partyId,
                            date = System.currentTimeMillis(),
                            entryType = "DEBIT",
                            amount = price,
                            referenceId = ref,
                            description = "Direct Sale: ${-bagsDelta} bags of ${commodity.name} @ $ratePerQtl/qtl. $remarks"
                        )
                    )
                }
            }

            // Record transaction with isSynced = false
            inventoryTransactionDao.insertTransaction(
                InventoryTransaction(
                    commodityId = commodityId,
                    commodityName = commodity.name,
                    type = type,
                    bags = bagsDelta,
                    totalWeight = totalWeight,
                    ratePerQtl = ratePerQtl,
                    totalPrice = totalPrice,
                    partyId = partyId,
                    partyName = partyName,
                    remarks = remarks,
                    referenceNo = referenceNo,
                    date = System.currentTimeMillis(),
                    isSynced = false
                )
            )
        }
    }

    suspend fun deleteBill(billId: Int) {
        db.withTransaction {
            val bill = billDao.getBillById(billId) ?: return@withTransaction

            // Rollback effects
            if (bill.partyType == "FARMER") {
                // Rollback stock (goes down)
                commodityDao.adjustStock(bill.commodityId, -bill.bagCount)
                // Rollback farmer balance (receivable goes up)
                partyDao.adjustPartyBalance(bill.partyId, bill.netAmount)
            } else {
                // Rollback stock (goes up)
                commodityDao.adjustStock(bill.commodityId, bill.bagCount)
                // Rollback trader balance (receivable goes down)
                partyDao.adjustPartyBalance(bill.partyId, -bill.netAmount)
            }

            // Remove ledger entries
            ledgerDao.deleteLedgerEntriesByReference(bill.billNumber)

            // Delete actual bill
            billDao.deleteBillById(billId)
        }
    }

    // Process a manual Payment (Cash or Bank Transfer)
    suspend fun recordPayment(
        partyId: Int,
        partyType: String,
        amount: Double,
        isPaymentMadeToFarmer: Boolean, // True if we paid a farmer. False if we received from trader.
        paymentMethod: String, // "CASH", "BANK"
        notes: String
    ) {
        db.withTransaction {
            val referenceId = "PAY-" + System.currentTimeMillis().toString().takeLast(6)
            
            if (partyType == "FARMER") {
                // If we pay the farmer:
                // Farmer's balance increases (since they were negative/payable, paying them makes it go towards 0)
                partyDao.adjustPartyBalance(partyId, amount)

                ledgerDao.insertLedgerEntry(
                    LedgerEntry(
                        partyId = partyId,
                        date = System.currentTimeMillis(),
                        entryType = "DEBIT",
                        amount = amount,
                        referenceId = referenceId,
                        description = "Payment made via $paymentMethod: $notes"
                    )
                )
            } else {
                // If trader pays us:
                // Trader's balance decreases (since they owed us, receiving payment reduces their outstanding balance)
                partyDao.adjustPartyBalance(partyId, -amount)

                ledgerDao.insertLedgerEntry(
                    LedgerEntry(
                        partyId = partyId,
                        date = System.currentTimeMillis(),
                        entryType = "CREDIT",
                        amount = amount,
                        referenceId = referenceId,
                        description = "Payment received via $paymentMethod: $notes"
                    )
                )
            }
        }
    }

    suspend fun insertInventoryTransactionDirectly(tx: InventoryTransaction) {
        inventoryTransactionDao.insertTransaction(tx)
    }

    // Expense operations
    suspend fun insertExpense(expense: Expense): Long = expenseDao.insertExpense(expense)
    suspend fun deleteExpenseById(id: Int) = expenseDao.deleteExpenseById(id)

    // Direct insert and database clear helpers for Backup and Restore
    suspend fun insertBillDirectly(bill: Bill): Long = billDao.insertBill(bill)
    suspend fun insertLedgerEntryDirectly(entry: LedgerEntry): Long = ledgerDao.insertLedgerEntry(entry)
    suspend fun clearAllTables() {
        db.clearAllTables()
    }

    // Unsynced fetch and status update delegates
    suspend fun getUnsyncedParties(): List<Party> = partyDao.getUnsyncedParties()
    suspend fun updatePartySyncStatus(id: Int, isSynced: Boolean) = partyDao.updateSyncStatus(id, isSynced)

    suspend fun getUnsyncedCommodities(): List<Commodity> = commodityDao.getUnsyncedCommodities()
    suspend fun updateCommoditySyncStatus(id: Int, isSynced: Boolean) = commodityDao.updateSyncStatus(id, isSynced)

    suspend fun getUnsyncedBills(): List<Bill> = billDao.getUnsyncedBills()
    suspend fun updateBillSyncStatus(id: Int, isSynced: Boolean) = billDao.updateSyncStatus(id, isSynced)

    suspend fun getUnsyncedTransactions(): List<InventoryTransaction> = inventoryTransactionDao.getUnsyncedTransactions()
    suspend fun updateTransactionSyncStatus(id: Int, isSynced: Boolean) = inventoryTransactionDao.updateSyncStatus(id, isSynced)

    suspend fun getUnsyncedExpenses(): List<Expense> = expenseDao.getUnsyncedExpenses()
    suspend fun updateExpenseSyncStatus(id: Int, isSynced: Boolean) = expenseDao.updateSyncStatus(id, isSynced)
}
