package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllParties(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE type = :type ORDER BY name ASC")
    fun getPartiesByType(type: String): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getPartyById(id: Int): Party?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: Party): Long

    @Update
    suspend fun updateParty(party: Party)

    @Query("UPDATE parties SET balance = balance + :amount, isSynced = 0 WHERE id = :id")
    suspend fun adjustPartyBalance(id: Int, amount: Double)

    @Delete
    suspend fun deleteParty(party: Party)

    @Query("SELECT * FROM parties WHERE isSynced = 0")
    suspend fun getUnsyncedParties(): List<Party>

    @Query("UPDATE parties SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean)
}

@Dao
interface CommodityDao {
    @Query("SELECT * FROM commodities ORDER BY name ASC")
    fun getAllCommodities(): Flow<List<Commodity>>

    @Query("SELECT * FROM commodities WHERE id = :id")
    suspend fun getCommodityById(id: Int): Commodity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommodity(commodity: Commodity): Long

    @Update
    suspend fun updateCommodity(commodity: Commodity)

    @Query("UPDATE commodities SET stockBags = stockBags + :bagDelta, isSynced = 0 WHERE id = :id")
    suspend fun adjustStock(id: Int, bagDelta: Int)

    @Delete
    suspend fun deleteCommodity(commodity: Commodity)

    @Query("SELECT * FROM commodities WHERE isSynced = 0")
    suspend fun getUnsyncedCommodities(): List<Commodity>

    @Query("UPDATE commodities SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY date DESC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE partyType = :partyType ORDER BY date DESC")
    fun getBillsByPartyType(partyType: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE partyId = :partyId ORDER BY date DESC")
    fun getBillsByParty(partyId: Int): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: Int): Bill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill): Long

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteBillById(id: Int)

    @Query("SELECT * FROM bills WHERE isSynced = 0")
    suspend fun getUnsyncedBills(): List<Bill>

    @Query("UPDATE bills SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE partyId = :partyId ORDER BY date DESC")
    fun getLedgerEntriesForParty(partyId: Int): Flow<List<LedgerEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntry): Long

    @Query("DELETE FROM ledger_entries WHERE referenceId = :referenceId")
    suspend fun deleteLedgerEntriesByReference(referenceId: String)
}

@Dao
interface InventoryTransactionDao {
    @Query("SELECT * FROM inventory_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<InventoryTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: InventoryTransaction): Long

    @Query("DELETE FROM inventory_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM inventory_transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT * FROM inventory_transactions WHERE isSynced = 0")
    suspend fun getUnsyncedTransactions(): List<InventoryTransaction>

    @Query("UPDATE inventory_transactions SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<Expense>

    @Query("UPDATE expenses SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, isSynced: Boolean)
}
