package com.tamir.drift.data.repo

import com.tamir.drift.data.db.DriftDatabase
import com.tamir.drift.data.db.SavingsDepositEntity
import com.tamir.drift.data.db.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

class SavingsRepository(private val db: DriftDatabase) {

    val goals: Flow<List<SavingsGoalEntity>> = db.savings().observeGoals()
    val deposits: Flow<List<SavingsDepositEntity>> = db.savings().observeDeposits()

    suspend fun create(name: String, targetAgorot: Long, targetDate: Long): Long =
        db.savings().insertGoal(SavingsGoalEntity(name = name.trim(), targetAmount = targetAgorot, targetDate = targetDate))

    suspend fun update(goal: SavingsGoalEntity) = db.savings().updateGoal(goal)

    suspend fun delete(goalId: Long) {
        db.savings().deleteDepositsOf(goalId)
        db.savings().deleteGoal(goalId)
    }

    suspend fun deposit(goalId: Long, agorot: Long, timestamp: Long, note: String? = null): Long =
        db.savings().insertDeposit(SavingsDepositEntity(goalId = goalId, amount = agorot, timestamp = timestamp, note = note))

    suspend fun deleteDeposit(id: Long) = db.savings().deleteDeposit(id)
}
