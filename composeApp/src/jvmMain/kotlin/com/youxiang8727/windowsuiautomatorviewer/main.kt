package com.youxiang8727.windowsuiautomatorviewer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WindowsUiAutomatorViewer",
    ) {
        App()
    }
}