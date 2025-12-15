package com.fw.myfirewall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules")
    fun getAllBlockRules(): Flow<List<BlockRule>>
    
    @Query("SELECT * FROM block_rules WHERE category = :category")
    fun getBlockRulesByCategory(category: BlockRuleCategory): Flow<List<BlockRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockRule(blockRule: BlockRule)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlockRules(blockRules: List<BlockRule>)
    
    @Query("DELETE FROM block_rules WHERE id = :id")
    suspend fun deleteBlockRule(id: Int)

    @Query("DELETE FROM block_rules WHERE category = :category")
    suspend fun deleteByCategory(category: BlockRuleCategory)

    @Query("UPDATE block_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateBlockRuleStatus(id: Int, isEnabled: Boolean)

    @Query("SELECT COUNT(*) FROM block_rules WHERE category = :category")
    fun countByCategory(category: BlockRuleCategory): Flow<Int>

    @Query("DELETE FROM block_rules WHERE category = :category")
    suspend  fun deleteRulesByCategory(category: BlockRuleCategory)


}
