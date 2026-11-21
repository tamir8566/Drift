package com.tamir.drift.data.repo

import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.RawCaptureEntity
import com.tamir.drift.data.db.TransactionEntity
import com.tamir.drift.domain.classify.MerchantKey
import com.tamir.drift.domain.model.TransactionSource
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val db: DriftDatabase, private val categories: CategoryRepository) {

    val all: Flow<List<TransactionEntity>> = db.transactions().observeAll()

    fun observe(id: Long): Flow<TransactionEntity?> = db.transactions().observeById(id)

    suspend fun get(id: Long): TransactionEntity? = db.transactions().getById(id)

    suspend fun getAll(): List<TransactionEntity> = db.transactions().getAll()

    suspend fun rawCapturesFor(id: Long): List<RawCaptureEntity> = db.rawCaptures().getForTransaction(id)

    /** Manual add (cash / Bit). */
    suspend fun addManual(
        timestamp: Long,
        amountFullAgorot: Long,
        amountMineAgorot: Long,
        isShared: Boolean,
        merchant: String,
        categoryId: String,
        note: String?,
    ): Long = db.transactions().insert(
        TransactionEntity(
            timestamp = timestamp,
            amountFull = amountFullAgorot,
            amountMine = if (isShared) amountMineAgorot else amountFullAgorot,
            isShared = isShared,
            merchant = merchant.trim(),
            categoryId = categoryId,
            source = TransactionSource.MANUAL.name,
            note = note?.takeIf { it.isNotBlank() },
        ),
    )

    suspend fun update(t: TransactionEntity) = db.transactions().update(t)

    /**
     * Changes the category of one transaction and, per PRD, learns a merchant rule so future
     * (and existing) transactions of that merchant follow the user's choice. "The same merchant"
     * is decided in Kotlin by [MerchantKey] over all rows (not by SQL text equality), so other
     * spellings and branch numbers of the merchant follow too.
     *
     * @return how many *other* transactions were moved to the new category.
     */
    suspend fun recategorize(t: TransactionEntity, categoryId: String): Int {
        db.transactions().update(t.copy(categoryId = categoryId))
        val key = MerchantKey.normalize(t.merchant)
        if (key.isEmpty()) return 0
        categories.learnRule(t.merchant, categoryId)
        val others = db.transactions().getAll()
            .filter { it.id != t.id && it.categoryId != categoryId && it.merchant.isNotBlank() && MerchantKey.normalize(it.merchant) == key }
            .map { it.id }
        // SQLite limits the number of bound values per statement; update in chunks.
        others.chunked(500).forEach { ids -> db.transactions().setCategory(ids, categoryId) }
        return others.size
    }

    suspend fun delete(id: Long) = db.transactions().deleteById(id)
}
