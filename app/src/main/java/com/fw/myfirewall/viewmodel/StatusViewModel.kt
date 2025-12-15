package com.fw.myfirewall.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fw.myfirewall.data.AppDatabase
import com.fw.myfirewall.data.BlockRuleCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn



class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).blockRuleDao()

    val advertisingCount = dao.countByCategory(BlockRuleCategory.ADVERTISING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val annoyancesCount = dao.countByCategory(BlockRuleCategory.ANNOYANCES)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val socialCount = dao.countByCategory(BlockRuleCategory.SOCIAL_MEDIA)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val customCount = dao.countByCategory(BlockRuleCategory.CUSTOM)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
