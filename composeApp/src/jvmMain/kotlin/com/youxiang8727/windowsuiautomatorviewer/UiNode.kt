package com.youxiang8727.windowsuiautomatorviewer

import androidx.compose.ui.geometry.Rect

data class UiNode(
    val name: String,
    val className: String,
    val controlType: String,
    val bounds: Rect,
    val children: List<UiNode> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)
