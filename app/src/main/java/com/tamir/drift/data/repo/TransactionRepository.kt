package com.tamir.drift.data.repo

import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.RawCaptureEntity
import com.tamir.drift.data.db.TransactionEntity
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
     * (and existing) transactions of that merchant follow the user's choice.
     */
    suspend fun recategorize(t: TransactionEntity, categoryId: String) {
        db.transactions().update(t.copy(categoryId = categoryId))
        if (t.merchant.isNotBlank()) {
            categories.learnRule(t.merchant, categoryId)
            db.transactions().recategorizeMerchant(t.merchant, categoryId)
        }
    }

    suspend fun delete(id: Long) = db.transactions().deleteById(id)
}
