package com.youxiang8727.windowsuiautomatorviewer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlinx.coroutines.launch

@Composable
fun App() {
    val automationService = remember { WindowsAutomationService() }
    var screenshot by remember { mutableStateOf<BufferedImage?>(null) }
    var rootNode by remember { mutableStateOf<UiNode?>(null) }
    var selectedNode by remember { mutableStateOf<UiNode?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左側：截圖與紅框
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .onGloballyPositioned { containerSize = it.size }
            ) {
                screenshot?.let { img ->
                    val bitmap = img.toComposeImageBitmap()
                    val imageSize = Size(img.width.toFloat(), img.height.toFloat())
                    
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // 計算縮放比例與偏移量，供點擊與繪圖共用
                        val scaleX = containerSize.width / imageSize.width
                        val scaleY = containerSize.height / imageSize.height
                        val scale = minOf(scaleX, scaleY)
                        val offsetX = (containerSize.width - imageSize.width * scale) / 2
                        val offsetY = (containerSize.height - imageSize.height * scale) / 2

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(rootNode) {
                                    detectTapGestures { tapOffset ->
                                        // 將畫面上點擊的座標轉換回原始截圖座標
                                        val originalX = (tapOffset.x - offsetX) / scale
                                        val originalY = (tapOffset.y - offsetY) / scale
                                        
                                        rootNode?.let { root ->
                                            val found = findNodeAt(root, originalX, originalY)
                                            if (found != null) {
                                                selectedNode = found
                                            }
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

                            // 繪製紅框
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                selectedNode?.let { node ->
                                    val rect = node.bounds
                                    if (rect.width > 0 && rect.height > 0) {
                                        drawRect(
                                            color = Color.Red,
                                            topLeft = Offset(
                                                rect.left * scale + offsetX,
                                                rect.top * scale + offsetY
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

            // 右側：樹狀結構與屬性
            Column(modifier = Modifier.weight(0.4f).fillMaxHeight().padding(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            screenshot = automationService.captureScreenshot()
                            rootNode = automationService.captureUiTree()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Capture Windows Screen")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("UI Hierarchy", style = MaterialTheme.typography.titleMedium)
                
                Box(modifier = Modifier.weight(0.6f).fillMaxWidth().border(1.dp, Color.LightGray)) {
                    val state = rememberLazyListState()
                    LazyColumn(state = state) {
                        rootNode?.let { root ->
                            item {
                                NodeTreeItem(root, 0, selectedNode) { selectedNode = it }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Node Details", style = MaterialTheme.typography.titleMedium)
                Box(modifier = Modifier.weight(0.4f).fillMaxWidth().border(1.dp, Color.LightGray).padding(4.dp)) {
                    selectedNode?.let { node ->
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            PropertyRow("Name", node.name)
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

// 遞迴尋找點擊座標下最深層的節點
fun findNodeAt(node: UiNode, x: Float, y: Float): UiNode? {
    if (!node.bounds.contains(Offset(x, y))) return null
    
    // 先找子節點，因為子節點通常在「上面」且範圍更小
    for (child in node.children) {
        val found = findNodeAt(child, x, y)
        if (found != null) return found
    }
    
    return node
}

@Composable
fun NodeTreeItem(node: UiNode, depth: Int, selectedNode: UiNode?, onSelect: (UiNode) -> Unit) {
    var isExpanded by remember { mutableStateOf(depth < 2) }
    val isSelected = node == selectedNode

    // 當 selectedNode 改變時，如果它是此節點的子孫，則展開此節點
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
                .padding(start = (depth * 16).dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.children.isNotEmpty()) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).clickable { isExpanded = !isExpanded }
                )
            } else {
                Spacer(modifier = Modifier.size(16.dp))
            }
            Text(
                text = "<${node.controlType}> ${node.name.take(30)}",
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
