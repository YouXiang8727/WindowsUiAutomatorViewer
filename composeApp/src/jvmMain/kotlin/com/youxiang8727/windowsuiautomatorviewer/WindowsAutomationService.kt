package com.youxiang8727.windowsuiautomatorviewer

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import androidx.compose.ui.geometry.Rect
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage

class WindowsAutomationService {
    private val robot = Robot()

    fun captureScreenshot(): BufferedImage {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return robot.createScreenCapture(Rectangle(screenSize))
    }

    fun captureUiTree(): UiNode {
        val nodes = mutableListOf<UiNode>()
        val screenRect = Toolkit.getDefaultToolkit().screenSize
        
        // 遍歷所有頂層視窗
        User32.INSTANCE.EnumWindows({ hWnd, _ ->
            if (User32.INSTANCE.IsWindowVisible(hWnd)) {
                val node = buildNode(hWnd, 0)
                if (node.name.isNotEmpty() || node.children.isNotEmpty()) {
                    nodes.add(node)
                }
            }
            true
        }, null)

        return UiNode(
            "Root", 
            "Desktop", 
            "Pane", 
            Rect(0f, 0f, screenRect.width.toFloat(), screenRect.height.toFloat()), 
            nodes
        )
    }

    private fun buildNode(hWnd: WinDef.HWND, depth: Int): UiNode {
        val rect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hWnd, rect)
        
        val titleArray = CharArray(1024)
        User32.INSTANCE.GetWindowText(hWnd, titleArray, 1024)
        val title = Native.toString(titleArray).trim()
        
        val classNameArray = CharArray(1024)
        User32.INSTANCE.GetClassName(hWnd, classNameArray, 1024)
        val className = Native.toString(classNameArray).trim()

        val children = mutableListOf<UiNode>()
        // 限制深度，並遍歷子控制項 (HWND)
        if (depth < 5) {
            User32.INSTANCE.EnumChildWindows(hWnd, { childHWnd, _ ->
                // 只處理直接子元素，避免 EnumChildWindows 預設的遞迴行為導致樹狀結構混亂
                if (User32.INSTANCE.GetParent(childHWnd) == hWnd) {
                    children.add(buildNode(childHWnd, depth + 1))
                }
                true
            }, null)
        }

        return UiNode(
            name = if (title.isEmpty()) (if (className.isEmpty()) "Unnamed" else className) else title,
            className = className,
            controlType = if (depth == 0) "Window" else "Control",
            bounds = Rect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat()
            ),
            children = children,
            properties = mapOf(
                "hWnd" to hWnd.toString(),
                "Visible" to User32.INSTANCE.IsWindowVisible(hWnd).toString()
            )
        )
    }
}
