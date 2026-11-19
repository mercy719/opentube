package com.opentube.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 只有少数几项真正有用的设置，其余全部用默认值 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _sponsorBlock = MutableStateFlow(prefs.getBoolean("sponsorblock", true))
    val sponsorBlock: StateFlow<Boolean> = _sponsorBlock
    fun setSponsorBlock(v: Boolean) { prefs.edit().putBoolean("sponsorblock", v).apply(); _sponsorBlock.value = v }

    /** 0 = 自动（不设上限），否则为最高画质高度 */
    private val _maxHeight = MutableStateFlow(prefs.getInt("max_height", 0))
    val maxHeight: StateFlow<Int> = _maxHeight
    fun setMaxHeight(v: Int) { prefs.edit().putInt("max_height", v).apply(); _maxHeight.value = v }

    companion object {
        @Volatile private var instance: Settings? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Settings(context.applicationContext).also { instance = it } }
    }
}
