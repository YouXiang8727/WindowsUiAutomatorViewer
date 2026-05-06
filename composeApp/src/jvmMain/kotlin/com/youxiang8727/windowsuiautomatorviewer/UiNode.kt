package com.youxiang8727.windowsuiautomatorviewer

import androidx.compose.ui.geometry.Rect

data class UiNode(
    val name: String,
    val className: String,
    val packageName: String, // 對應 Windows 的進程名稱 (如 notepad.exe)
    val controlType: String,
    val bounds: Rect,
    val children: List<UiNode> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)
