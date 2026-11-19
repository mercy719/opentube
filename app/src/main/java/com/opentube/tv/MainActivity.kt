package com.opentube.tv

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.opentube.tv.data.YouTube
import com.opentube.tv.player.OtPlayer
import com.opentube.tv.player.Prefetcher
import kotlinx.coroutines.flow.first

/** 临时冒烟测试：拉首页 → 播放第一条，日志里打印各阶段耗时 */
class MainActivity : ComponentActivity() {
    private lateinit var player: OtPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = OtPlayer(this)
        setContent {
            val st by player.state.collectAsState()
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { SurfaceView(it).also(player::attach) }, modifier = Modifier.fillMaxSize())
                Text("${st.phase} ${st.positionMs / 1000}s/${st.durationMs / 1000}s ${st.currentHeight}p ${st.availableHeights} ${st.message ?: ""} segs=${st.segments.size}")
            }
            LaunchedEffect(Unit) {
                val t0 = SystemClock.elapsedRealtime()
                val rows = mutableListOf<com.liskovsoft.mediaserviceinterfaces.data.MediaGroup>()
                YouTube.home().collect { batch -> Log.i(TAG, "batch ${batch.size}"); rows += batch }
                Log.i(TAG, "home: ${rows.size} rows in ${SystemClock.elapsedRealtime() - t0}ms")
                rows.forEach { g -> Log.i(TAG, "  row '${g.title}' type=${g.type} items=${g.mediaItems?.size}") }
                rows += YouTube.search("kurzgesagt")
                Log.i(TAG, "after search: ${rows.size} rows")
                val item = rows.flatMap { it.mediaItems.orEmpty() }.first { it.videoId != null && !it.isLive }
                Log.i(TAG, "play: ${item.title} (${item.videoId})")
                val t1 = SystemClock.elapsedRealtime()
                val fi = Prefetcher.get(item.videoId)
                Log.i(TAG, "formatInfo in ${SystemClock.elapsedRealtime() - t1}ms dash=${fi?.containsDashFormats()} sabr=${fi?.containsSabrFormats()} unplayable=${fi?.isUnplayable} reason=${fi?.playabilityReason}")
                val t2 = SystemClock.elapsedRealtime()
                player.open(item.videoId, item)
                player.state.first { it.phase == com.opentube.tv.player.PlayerState.Phase.Playing || it.phase == com.opentube.tv.player.PlayerState.Phase.Error }
                Log.i(TAG, "first play after ${SystemClock.elapsedRealtime() - t2}ms: ${player.state.value}")
            }
        }
    }

    override fun onDestroy() { player.release(); super.onDestroy() }

    companion object { const val TAG = "OpenTubeSmoke" }
}
