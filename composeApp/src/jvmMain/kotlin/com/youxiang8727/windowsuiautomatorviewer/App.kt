package com.youxiang8727.windowsuiautomatorviewer

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sun.jna.platform.win32.User32
import java.awt.image.BufferedImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ViewMode {
    FullScreen, Window
}

@Composable
fun App() {
    val automationService = remember { WindowsAutomationService() }
    var viewMode by remember { mutableStateOf(ViewMode.FullScreen) }
    
    var screenshot by remember { mutableStateOf<BufferedImage?>(null) }
    var rootNode by remember { mutableStateOf<UiNode?>(null) }
    var selectedNode by remember { mutableStateOf<UiNode?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Window Mode States
    var runningApps by remember { mutableStateOf(listOf<WindowInfo>()) }
    var selectedApp by remember { mutableStateOf<WindowInfo?>(null) }
    var isWindowMissing by remember { mutableStateOf(false) }

    // Filter State (Full Screen Mode)
    var selectedPackage by remember { mutableStateOf("All") }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    // Derive available packages
    val availablePackages = remember(rootNode) {
        val packages = mutableSetOf("All")
        rootNode?.children?.map { it.packageName }?.let { packages.addAll(it) }
        packages.toList().sorted()
    }

    // Filtered tree
    val filteredRootNode = remember(rootNode, selectedPackage, viewMode) {
        val currentRoot = rootNode
        if (viewMode == ViewMode.Window || selectedPackage == "All") {
            currentRoot
        } else {
            currentRoot?.let { root ->
                root.copy(
                    children = root.children.filter { it.packageName == selectedPackage }
                )
            }
        }
    }

    // Animation for the focus mask
    val maskAlpha by animateFloatAsState(
        targetValue = if (viewMode == ViewMode.Window || selectedPackage == "All") 0f else 0.7f,
        animationSpec = tween(500)
    )

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Navigation / App List (Only for Window Mode)
            AnimatedVisibility(
                visible = viewMode == ViewMode.Window,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                Column(modifier = Modifier.width(250.dp).fillMaxHeight().border(1.dp, Color.LightGray)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Running Apps", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { runningApps = automationService.getRunningApplications() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    Divider()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(runningApps) { app ->
                            ListItem(
                                headlineContent = { Text(app.title, maxLines = 1) },
                                supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.clickable {
                                    selectedApp = app
                                    isWindowMissing = false
                                    scope.launch {
                                        val appHWnd = automationService.getAppHWnd()
                                        // Minimize self
                                        appHWnd?.let {
                                            User32.INSTANCE.ShowWindow(it, 6) // SW_MINIMIZE
                                            delay(600)
                                        }
                                        try {
                                            if (automationService.isWindowValid(app.hWnd)) {
                                                // Focus the target window
                                                automationService.focusWindow(app.hWnd)
                                                delay(600) // Wait for focus animation
                                                
                                                screenshot = automationService.captureWindowScreenshot(app.hWnd)
                                                rootNode = automationService.captureWindowUiTree(app.hWnd)
                                                selectedNode = null
                                            } else {
                                                isWindowMissing = true
                                            }
                                        } finally {
                                            // Restore self
                                            appHWnd?.let {
                                                User32.INSTANCE.ShowWindow(it, 9) // SW_RESTORE
                                            }
                                        }
                                    }
                                }.background(if (selectedApp?.hWnd == app.hWnd) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            )
                        }
                    }
                }
            }

            // Middle: Screenshot View
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .onGloballyPositioned { containerSize = it.size }
                    .clipToBounds()
            ) {
                if (isWindowMissing) {
                    Text(
                        "${selectedApp?.packageName ?: "Application"} 未開啟",
                        color = Color.Red,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    screenshot?.let { img ->
                        val bitmap = img.toComposeImageBitmap()
                        val imageSize = Size(img.width.toFloat(), img.height.toFloat())
                        
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val scaleX = containerSize.width.toFloat() / imageSize.width
                            val scaleY = containerSize.height.toFloat() / imageSize.height
                            val scale = if (scaleX > 0 && scaleY > 0) minOf(scaleX, scaleY) else 1f
                            val offsetX = (containerSize.width - imageSize.width * scale) / 2
                            val offsetY = (containerSize.height - imageSize.height * scale) / 2

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(filteredRootNode, viewMode) {
                                        detectTapGestures { tapOffset ->
                                            val originalX = (tapOffset.x - offsetX) / scale
                                            val originalY = (tapOffset.y - offsetY) / scale
                                            
                                            val searchX = if (viewMode == ViewMode.Window) {
                                                originalX + (rootNode?.bounds?.left ?: 0f)
                                            } else {
                                                originalX
                                            }
                                            val searchY = if (viewMode == ViewMode.Window) {
                                                originalY + (rootNode?.bounds?.top ?: 0f)
                                            } else {
                                                originalY
                                            }

                                            filteredRootNode?.let { root ->
                                                selectedNode = findSmallestNodeAt(root, searchX, searchY)
                                            }
                                        }
                                    }
                            ) {
                                Image(
                                    painter = BitmapPainter(bitmap),
                                    contentDescription = "Screenshot",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                ) {
                                    val winOffsetLeft = if (viewMode == ViewMode.Window) (rootNode?.bounds?.left ?: 0f) else 0f
                                    val winOffsetTop = if (viewMode == ViewMode.Window) (rootNode?.bounds?.top ?: 0f) else 0f

                                    if (maskAlpha > 0f) {
                                        drawRect(Color.Black.copy(alpha = maskAlpha))
                                        filteredRootNode?.children?.forEach { node ->
                                            val rect = node.bounds
                                            drawRect(
                                                color = Color.Transparent,
                                                topLeft = Offset((rect.left - winOffsetLeft) * scale + offsetX, (rect.top - winOffsetTop) * scale + offsetY),
                                                size = Size(rect.width * scale, rect.height * scale),
                                                blendMode = BlendMode.Clear
                                            )
                                        }
                                    }

                                    selectedNode?.let { node ->
                                        val rect = node.bounds
                                        if (rect.width > 0 && rect.height > 0) {
                                            drawRect(
                                                color = Color.Red,
                                                topLeft = Offset(
                                                    (rect.left - winOffsetLeft) * scale + offsetX,
                                                    (rect.top - winOffsetTop) * scale + offsetY
                                                ),
                                                size = Size(rect.width * scale, rect.height * scale),
                                                style = Stroke(width = 2.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } ?: Text("Click Capture to start", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }

            // Right Column: Controls and Details
            Column(modifier = Modifier.weight(0.4f).fillMaxHeight().padding(8.dp)) {
                TabRow(selectedTabIndex = viewMode.ordinal) {
                    Tab(selected = viewMode == ViewMode.FullScreen, onClick = { viewMode = ViewMode.FullScreen }) {
                        Text("Full Screen", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = viewMode == ViewMode.Window, onClick = { 
                        viewMode = ViewMode.Window 
                        if (runningApps.isEmpty()) {
                            runningApps = automationService.getRunningApplications()
                        }
                    }) {
                        Text("Window Mode", modifier = Modifier.padding(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            scope.launch {
                                val appHWnd = automationService.getAppHWnd()
                                
                                // Step 1: Hide self before capture
                                appHWnd?.let {
                                    User32.INSTANCE.ShowWindow(it, 6) // SW_MINIMIZE
                                    delay(600) 
                                }

                                try {
                                    if (viewMode == ViewMode.FullScreen) {
                                        screenshot = automationService.captureScreenshot()
                                        val newRoot = automationService.captureUiTree()
                                        rootNode = newRoot
                                        if (selectedPackage != "All") {
                                            val packageExists = newRoot.children.any { it.packageName == selectedPackage }
                                            if (!packageExists) selectedPackage = "All"
                                        }
                                        selectedNode = null
                                        isWindowMissing = false
                                    } else {
                                        selectedApp?.let { app ->
                                            if (automationService.isWindowValid(app.hWnd)) {
                                                // Focus the target window
                                                automationService.focusWindow(app.hWnd)
                                                delay(600)

                                                isWindowMissing = false
                                                screenshot = automationService.captureWindowScreenshot(app.hWnd)
                                                rootNode = automationService.captureWindowUiTree(app.hWnd)
                                                selectedNode = null
                                            } else {
                                                isWindowMissing = true
                                                screenshot = null
                                                rootNode = null
                                            }
                                        }
                                    }
                                } finally {
                                    // Step 2: Restore self
                                    appHWnd?.let {
                                        User32.INSTANCE.ShowWindow(it, 9) // SW_RESTORE
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Capture ${if (viewMode == ViewMode.FullScreen) "Screen" else "Window"}")
                    }
                    
                    if (viewMode == ViewMode.FullScreen) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            IconButton(onClick = { isFilterMenuExpanded = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                            DropdownMenu(
                                expanded = isFilterMenuExpanded,
                                onDismissRequest = { isFilterMenuExpanded = false }
                            ) {
                                availablePackages.forEach { pkg ->
                                    DropdownMenuItem(
                                        text = { Text(pkg) },
                                        onClick = {
                                            selectedPackage = pkg
                                            isFilterMenuExpanded = false
                                            selectedNode = null
                                        },
                                        leadingIcon = {
                                            RadioButton(selected = selectedPackage == pkg, onClick = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("UI Hierarchy", style = MaterialTheme.typography.titleMedium)
                
                Box(modifier = Modifier.weight(0.6f).fillMaxWidth().border(1.dp, Color.LightGray)) {
                    Crossfade(targetState = filteredRootNode, animationSpec = tween(500)) { node ->
                        if (node != null) {
                            val state = rememberLazyListState()
                            LazyColumn(state = state) {
                                item {
                                    NodeTreeItem(node, 0, selectedNode) { selectedNode = it }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Node Details", style = MaterialTheme.typography.titleMedium)
                Box(modifier = Modifier.weight(0.4f).fillMaxWidth().border(1.dp, Color.LightGray).padding(4.dp)) {
                    AnimatedContent(
                        targetState = selectedNode,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        }
                    ) { node ->
                        if (node != null) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                PropertyRow("Name", node.name)
                                PropertyRow("Package", node.packageName)
                                PropertyRow("ControlType", node.controlType)
                                PropertyRow("Class", node.className)
                                PropertyRow("Bounds", "[${node.bounds.left.toInt()}, ${node.bounds.top.toInt()}][${node.bounds.right.toInt()}, ${node.bounds.bottom.toInt()}]")
                                node.properties.forEach { (k, v) ->
                                    PropertyRow(k, v)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 尋找包含該點且面積最小的節點
fun findSmallestNodeAt(root: UiNode, x: Float, y: Float): UiNode? {
    val candidates = mutableListOf<UiNode>()
    collectCandidates(root, x, y, candidates)
    return candidates.minByOrNull { it.bounds.width * it.bounds.height }
}

fun collectCandidates(node: UiNode, x: Float, y: Float, list: MutableList<UiNode>) {
    if (node.bounds.contains(Offset(x, y))) {
        list.add(node)
        for (child in node.children) {
            collectCandidates(child, x, y, list)
        }
    }
}

@Composable
fun NodeTreeItem(node: UiNode, depth: Int, selectedNode: UiNode?, onSelect: (UiNode) -> Unit) {
    var isExpanded by remember { mutableStateOf(depth < 2) }
    val isSelected = node == selectedNode

    LaunchedEffect(selectedNode) {
        if (selectedNode != null && isDescendant(node, selectedNode)) {
            isExpanded = true
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(node) }
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(start = (depth * 16).dp)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (if (node.children.isNotEmpty()) (if (isExpanded) "[-] " else "[+] ") else "    ") + 
                       "<${node.controlType}> ${node.name.take(30)}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        
        if (isExpanded) {
            node.children.forEach { child ->
                NodeTreeItem(child, depth + 1, selectedNode, onSelect)
            }
        }
    }
}

fun isDescendant(parent: UiNode, target: UiNode): Boolean {
    return parent.children.any { it == target || isDescendant(it, target) }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, modifier = Modifier.weight(0.65f), style = MaterialTheme.typography.bodySmall)
    }
}
