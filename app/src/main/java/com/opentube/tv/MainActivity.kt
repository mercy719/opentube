package com.opentube.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.opentube.tv.player.OtPlayer
import com.opentube.tv.ui.shell.AppRoot
import com.opentube.tv.ui.theme.OpenTubeTheme

class MainActivity : ComponentActivity() {
    // 播放器整个 Activity 只建一次，进出播放页不重复初始化解码器
    private lateinit var player: OtPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = OtPlayer(this)
        setContent { OpenTubeTheme { AppRoot(player) } }
    }

    override fun onStop() { super.onStop(); player.pauseAll() }
    override fun onDestroy() { player.release(); super.onDestroy() }
}
