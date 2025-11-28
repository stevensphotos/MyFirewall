package com.fw.myfirewall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules")
    fun getAllRules(): Flow<List<AppRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(appRule: AppRule)

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getRule(packageName: String): AppRule?
}