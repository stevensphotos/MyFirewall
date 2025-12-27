package com.fw.myfirewall.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "block_rules")
data class BlockRule(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("domain")
    val domain: String,

    @SerializedName("category")
    val category: BlockRuleCategory,

    @SerializedName("isEnabled")
    val isEnabled: Boolean = true
)

enum class BlockRuleCategory {
    @SerializedName("ADVERTISING")
    ADVERTISING,
    @SerializedName("SOCIAL_MEDIA")
    SOCIAL_MEDIA,
    @SerializedName("ANNOYANCES")
    ANNOYANCES,
    @SerializedName("CUSTOM")
    CUSTOM
}
