package com.fw.myfirewall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val wifiAllowed: Boolean,
    val mobileAllowed: Boolean,
    val roamingAllowed: Boolean
)