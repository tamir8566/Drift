package com.tamir.drift.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities. All amounts are stored in agorot (Long) per the PRD.
 */

@Entity(tableName = "transactions", indices = [Index("timestamp"), Index("categoryId"), Index("merchant")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Full charged amount, agorot. */
    val amountFull: Long,
    /** My share, agorot (= amountFull unless shared). All calculations use this. */
    val amountMine: Long,
    val isShared: Boolean = false,
    val merchant: String = "",
    val categoryId: String,
    /** GPAY / SMS / MANUAL. */
    val source: String,
    val note: String? = null,
    /** True when no amount could be extracted; the user completes it in the detail screen. */
    val isUnparsed: Boolean = false,
    val subscriptionId: String? = null,
    /** Raw captured texts (both texts when a duplicate from two sources was merged). */
    val rawText: String? = null,
    /** Last 4 card digits when the source message had them. */
    val cardLast4: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "raw_captures", indices = [Index("capturedAt")])
data class RawCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val senderOrPackage: String,
    val text: String,
    val capturedAt: Long,
    val transactionId: Long? = null,
    /** Parser outcome for the debug log: PARSED / AMOUNT_ONLY / UNPARSED / DUPLICATE / IGNORED. */
    val result: String = "",
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ARGB color. */
    val color: Long,
    val sortOrder: Int,
    /** Agorot; null = no budget for this category. */
    val monthlyBudget: Long? = null,
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val keyword: String,
    val categoryId: String,
    /** BUILTIN / LEARNED. */
    val origin: String,
)

@Entity(tableName = "parse_patterns")
data class ParsePatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** GPAY / SMS, or null for any source. */
    val source: String?,
    /** Combined regex with named groups amount / merchant (see PatternBuilder). */
    val amountRegex: String,
    /** Optional separate merchant regex (unused when amountRegex has a merchant group). */
    val merchantRegex: String? = null,
    val sample: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Monthly total budget. A row applies from its month onward until a later row exists. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    /** "yyyy-MM". */
    @PrimaryKey val month: String,
    /** Agorot. */
    val total: Long,
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Agorot. */
    val targetAmount: Long,
    val targetDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "savings_deposits", indices = [Index("goalId")])
data class SavingsDepositEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** Agorot. */
    val amount: Long,
    val timestamp: Long,
    val note: String? = null,
)

/** Remembers which budget alerts were already sent (once per month per scope per level). */
@Entity(tableName = "alerts_sent")
data class AlertSentEntity(
    /** "yyyy-MM|total|80" or "yyyy-MM|<categoryId>|100". */
    @PrimaryKey val key: String,
    val sentAt: Long,
)

object Origin {
    const val BUILTIN = "BUILTIN"
    const val LEARNED = "LEARNED"
}
