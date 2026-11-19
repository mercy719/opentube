package com.opentube.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.CompositionLocalProvider
import com.opentube.tv.ui.components.LocalAllowAutoFocus
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.opentube.tv.data.Settings
import com.opentube.tv.player.OtPlayer
import com.opentube.tv.ui.components.StatusPanel
import com.opentube.tv.ui.feed.FeedKind
import com.opentube.tv.ui.feed.FeedScreen
import com.opentube.tv.ui.feed.FeedViewModel
import com.opentube.tv.ui.player.PlayerScreen
import com.opentube.tv.ui.search.SearchScreen
import com.opentube.tv.ui.search.SearchViewModel
import com.opentube.tv.ui.settings.SettingsScreen
import com.opentube.tv.ui.signin.SignInScreen
import com.opentube.tv.ui.theme.Film
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

private sealed interface Route {
    data object Main : Route
    data class Player(val item: MediaItem) : Route
    data object SignIn : Route
}

private fun feedFactory(kind: FeedKind) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(kind) as T
}

@Composable
fun AppRoot(player: OtPlayer) {
    var route by remember { mutableStateOf<Route>(Route.Main) }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var railFocused by remember { mutableStateOf(false) }
    // 非首页标签按返回先回首页，首页再按返回才退出
    androidx.activity.compose.BackHandler(enabled = route == Route.Main && tab != Tab.Home) { tab = Tab.Home }
    val ctx = LocalContext.current
    val settings = remember { Settings.get(ctx) }
    val sponsor by settings.sponsorBlock.collectAsState()
    val maxH by settings.maxHeight.collectAsState()
    LaunchedEffect(sponsor) { player.sponsorCategories = if (sponsor) OtPlayer.DEFAULT_SPONSOR_CATEGORIES else emptySet() }
    LaunchedEffect(maxH) { player.maxHeightCap = maxH }

    val home: FeedViewModel = viewModel(key = "home", factory = feedFactory(FeedKind.Home))
    val subs: FeedViewModel = viewModel(key = "subs", factory = feedFactory(FeedKind.Subscriptions))
    val history: FeedViewModel = viewModel(key = "history", factory = feedFactory(FeedKind.History))
    val search: SearchViewModel = viewModel()

    val open: (MediaItem) -> Unit = { item ->
        if (item.videoId != null) route = Route.Player(item)
        // 频道 / 播放列表：第一版先打开搜索该频道名（频道页下一步做）
        else { search.search(item.title ?: ""); tab = Tab.Search }
    }
    val signIn = { route = Route.SignIn }

    when (val r = route) {
        is Route.Player -> PlayerScreen(player, r.item, onExit = { route = Route.Main })
        Route.SignIn -> SignInScreen(
            onDone = { route = Route.Main; home.reload(); subs.reload(); history.reload() },
            onCancel = { route = Route.Main },
        )
        Route.Main -> Row(Modifier.fillMaxSize().background(Film.Base)) {
            NavRail(tab, onSelect = { tab = it }, modifier = Modifier.onFocusChanged { railFocused = it.hasFocus })
            Box(Modifier.fillMaxHeight().width(1.dp).background(Film.Seam))
            Box(Modifier.fillMaxSize()) {
              CompositionLocalProvider(LocalAllowAutoFocus provides !railFocused, com.opentube.tv.ui.components.LocalPlayer provides player) {
                when (tab) {
                    Tab.Home -> FeedScreen(home, "首页还是空的", "登录后这里会显示为你推荐的视频", open, signIn)
                    Tab.Subscriptions -> FeedScreen(subs, "还没有订阅内容", "登录账号后显示你订阅频道的新视频", open, signIn)
                    Tab.History -> FeedScreen(history, "还没有观看记录", "看过的视频会按时间排在这里", open, signIn)
                    Tab.Search -> SearchScreen(search, open)
                    Tab.Settings -> SettingsScreen(onSignIn = signIn)
                }
              }
            }
        }
    }
}
