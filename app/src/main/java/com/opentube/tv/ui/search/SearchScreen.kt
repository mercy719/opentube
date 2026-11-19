package com.opentube.tv.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.opentube.tv.data.YouTube
import com.opentube.tv.player.Prefetcher
import com.opentube.tv.ui.components.FilmStrip
import com.opentube.tv.ui.components.LoadingLine
import com.opentube.tv.ui.components.PillButton
import com.opentube.tv.ui.components.PivotSpec
import com.opentube.tv.ui.components.greasePencil
import com.opentube.tv.ui.components.isShowable
import com.opentube.tv.ui.feed.Strip
import com.opentube.tv.ui.theme.Dims
import com.opentube.tv.ui.theme.Film
import com.opentube.tv.ui.theme.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    val query = MutableStateFlow("")
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val results = MutableStateFlow<List<Strip>>(emptyList())
    val searching = MutableStateFlow(false)
    val searched = MutableStateFlow<String?>(null)
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onQuery(q: String) {
        query.value = q
        suggestJob?.cancel()
        if (q.isBlank()) { suggestions.value = emptyList(); return }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions.value = runCatching { YouTube.searchSuggestions(q) }.getOrDefault(emptyList()).take(8)
        }
    }

    fun search(q: String = query.value) {
        if (q.isBlank()) return
        query.value = q
        searchJob?.cancel()
        searching.value = true
        searchJob = viewModelScope.launch {
            val groups = runCatching { YouTube.search(q) }.getOrDefault(emptyList())
            results.value = groups.mapNotNull { g -> g.mediaItems.orEmpty().filter { it.isShowable }.takeIf { it.isNotEmpty() }?.let { g to it } }
                .mapIndexed { i, (g, items) -> Strip("s$i-${g.title}", g.title?.takeIf { it.isNotBlank() } ?: "“$q”的结果", items, g) }
            searched.value = q
            searching.value = false
        }
    }

    fun loadMore(strip: Strip) {
        val g = strip.group ?: return
        if (g.nextPageKey == null || strip.loadingMore) return
        results.value = results.value.map { if (it.id == strip.id) it.copy(loadingMore = true) else it }
        viewModelScope.launch {
            val next = runCatching { YouTube.continueGroup(g) }.getOrNull()
            results.value = results.value.map {
                if (it.id != strip.id) it else it.copy(items = it.items + next?.mediaItems.orEmpty().filter { m -> m.isShowable }, group = next ?: it.group, loadingMore = false)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(vm: SearchViewModel, onOpen: (MediaItem) -> Unit) {
    val query by vm.query.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val searched by vm.searched.collectAsState()
    val fieldFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf<MediaItem?>(null) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val firstResult = remember { FocusRequester() }
    var pendingFocusResults by remember { mutableStateOf(false) }
    val submit: (String?) -> Unit = { q -> keyboard?.hide(); pendingFocusResults = true; if (q == null) vm.search() else vm.search(q) }
    LaunchedEffect(results, pendingFocusResults) {
        if (pendingFocusResults && results.isNotEmpty()) { delay(80); runCatching { firstResult.requestFocus() }; pendingFocusResults = false }
    }

    val preload = com.opentube.tv.ui.components.LocalPreload.current
    LaunchedEffect(focused) { val f = focused ?: return@LaunchedEffect; delay(450); f.videoId?.let(Prefetcher::prefetch); preload(f) }
    val allowAuto = com.opentube.tv.ui.components.LocalAllowAutoFocus.current
    LaunchedEffect(Unit) { if (results.isEmpty() && allowAuto) runCatching { fieldFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(top = Dims.SafeV)) {
        Text("搜索", style = Type.ScreenTitle, modifier = Modifier.padding(start = Dims.SafeH))
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = Dims.SafeH).fillMaxWidth()
                .greasePencil(fieldFocused, "search-field", outset = 4.dp)
                .background(Film.Rebate, RoundedCornerShape(4.dp))
                .border(1.dp, Film.Seam, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = Film.Edge2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("频道、视频、关键词", style = Type.Body1.copy(color = Film.Edge3))
                BasicTextField(
                    value = query,
                    onValueChange = vm::onQuery,
                    singleLine = true,
                    textStyle = Type.Body1.copy(fontSize = Type.RowTitle.fontSize),
                    cursorBrush = SolidColor(Film.Grease),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(null) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus).onFocusChanged { fieldFocused = it.isFocused },
                )
            }
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dims.SafeH, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(suggestions) { s -> PillButton(s, onClick = { submit(s) }, primary = false) }
            }
        } else Spacer(Modifier.height(14.dp))

        when {
            searching -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { LoadingLine(Modifier.width(160.dp)) }
            searched != null && results.isEmpty() -> Text(
                "没有找到“$searched”的结果，换个关键词试试",
                style = Type.Body1.copy(color = Film.Edge2), modifier = Modifier.padding(start = Dims.SafeH, top = 24.dp),
            )
            else -> CompositionLocalProvider(LocalBringIntoViewSpec provides run {
                val lead = with(androidx.compose.ui.platform.LocalDensity.current) { com.opentube.tv.ui.theme.Dims.StripHeader.toPx() }
                remember(lead) { PivotSpec(0f, leadPx = lead) }
            }) {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(bottom = 200.dp),
                ) {
                    items(results, key = { it.id }) { strip ->
                        FilmStrip(strip.title, strip.items, onFocused = { focused = it }, onClick = onOpen, onNearEnd = { vm.loadMore(strip) },
                            firstFrameFocus = if (strip.id == results.first().id) firstResult else null)
                    }
                }
            }
        }
    }
}
