package com.smartstorage.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartstorage.app.StorageViewModel
import com.smartstorage.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StorageApp(vm: StorageViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("main") }
    var itemId by rememberSaveable { mutableStateOf<String?>(null) }
    var roomId by rememberSaveable { mutableStateOf<String?>(null) }
    var spacePlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var formMode by rememberSaveable { mutableStateOf("new") }
    var filter by remember { mutableStateOf(Filter()) }
    val snackbar = remember { SnackbarHostState() }
    var today by remember { mutableStateOf(java.time.LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = java.time.LocalDate.now() }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); today = java.time.LocalDate.now() } }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    fun back() { page = if (page == "form" && formMode != "new") "detail" else "main" }
    BackHandler(page != "main" && page != "form") { back() }
    val openItem: (String) -> Unit = { itemId = it; page = "detail" }
    val applyFilter: (Filter) -> Unit = { filter = it; tab = 2; page = "main" }
    CompositionLocalProvider(LocalToday provides today) { Scaffold(
        topBar = { TopAppBar(title = { Text(when(page) { "form" -> when(formMode) { "edit" -> "编辑物品"; "restock" -> "补充库存"; else -> "记录新物品" }
            "detail" -> "物品详情"; "stats" -> "家里的物品账本"; "templates" -> "收纳灵感"; "layout" -> "房间布局"; else -> listOf("家有好物", "我的空间", "全部物品", "我的")[tab] }) },
            navigationIcon = { if (page != "main" && page != "form") IconButton({ back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }) },
        bottomBar = { if (page == "main") NavigationBar {
            listOf("首页" to Icons.Outlined.Home, "空间" to Icons.Outlined.GridView, "物品" to Icons.Outlined.Inventory2, "我的" to Icons.Outlined.PersonOutline).forEachIndexed { i, (title, icon) ->
                NavigationBarItem(tab == i, { tab = i }, icon = { Icon(icon, title) }, label = { Text(title) })
            }
        } },
        floatingActionButton = { if (page == "main" && tab in listOf(0, 2) && state != null) ExtendedFloatingActionButton(
            onClick = { formMode = "new"; itemId = null; page = "form" }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("录入物品") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
        val s = state
        if (s == null) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (failure == null) { CircularProgressIndicator(); Text("正在打开你的收纳空间", Modifier.padding(16.dp)) }
            else { Text("数据读取失败：$failure"); Button(vm::reload) { Text("重试") } }
        } else when (page) {
            "form" -> ItemForm(s, vm, itemId, formMode, onDone = { id -> itemId = id; page = "detail" }, onCancel = { back() })
            "detail" -> ItemDetail(s, vm, itemId, onEdit = { formMode = "edit"; page = "form" }, onRestock = { formMode = "restock"; page = "form" })
            "stats" -> StatisticsScreen(s, applyFilter)
            "templates" -> TemplatesScreen(s, vm)
            "layout" -> LayoutScreen(s, vm, roomId, onContainer = { spacePlaceId = it; tab = 1; page = "main" })
            else -> when(tab) {
                0 -> HomeScreen(s, openItem, applyFilter, { page = "stats" }, { page = "templates" })
                1 -> SpacesScreen(s, vm, spacePlaceId, { spacePlaceId = it }, { applyFilter(Filter(place = it)) }, { roomId = it; page = "layout" }, openItem)
                2 -> ItemsScreen(s, vm, filter, { filter = it }, openItem)
                3 -> SettingsScreen(s, vm)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    } } }
}
