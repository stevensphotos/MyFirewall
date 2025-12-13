package com.fw.myfirewall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val domain: String,
    val category: BlockRuleCategory,
    val isEnabled: Boolean = true
)

enum class BlockRuleCategory {
    ADVERTISING,
    SOCIAL_MEDIA,
    TRACKERS,
    MALWARE,
    CUSTOM
}
