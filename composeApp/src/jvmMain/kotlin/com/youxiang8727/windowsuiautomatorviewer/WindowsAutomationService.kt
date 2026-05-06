package com.youxiang8727.windowsuiautomatorviewer

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Psapi
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import androidx.compose.ui.geometry.Rect
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File

data class WindowInfo(
    val title: String,
    val packageName: String,
    val hWnd: WinDef.HWND
)

class WindowsAutomationService {
    private val robot = Robot()

    fun captureScreenshot(): BufferedImage {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return robot.createScreenCapture(Rectangle(screenSize))
    }

    fun captureUiTree(): UiNode {
        val nodes = mutableListOf<UiNode>()
        val screenRect = Toolkit.getDefaultToolkit().screenSize
        
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
            name = "Root",
            className = "Desktop",
            packageName = "explorer.exe",
            controlType = "Pane",
            bounds = Rect(0f, 0f, screenRect.width.toFloat(), screenRect.height.toFloat()),
            children = nodes
        )
    }

    fun getRunningApplications(): List<WindowInfo> {
        val apps = mutableListOf<WindowInfo>()
        User32.INSTANCE.EnumWindows({ hWnd, _ ->
            if (User32.INSTANCE.IsWindowVisible(hWnd)) {
                val titleArray = CharArray(1024)
                User32.INSTANCE.GetWindowText(hWnd, titleArray, 1024)
                val title = Native.toString(titleArray).trim()
                
                // 只過濾掉完全沒標題的背景視窗
                if (title.isNotEmpty()) {
                    val processName = getProcessName(hWnd)
                    apps.add(WindowInfo(title, processName, hWnd))
                }
            }
            true
        }, null)
        return apps.sortedBy { it.title }
    }

    fun captureWindowScreenshot(hWnd: WinDef.HWND): BufferedImage? {
        if (!isWindowValid(hWnd)) return null
        
        val rect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hWnd, rect)
        val width = rect.right - rect.left
        val height = rect.bottom - rect.top
        
        if (width <= 0 || height <= 0) return null
        
        return try {
            robot.createScreenCapture(Rectangle(rect.left, rect.top, width, height))
        } catch (e: Exception) {
            null
        }
    }

    fun captureWindowUiTree(hWnd: WinDef.HWND): UiNode? {
        if (!isWindowValid(hWnd)) return null
        return buildNode(hWnd, 0)
    }

    fun isWindowValid(hWnd: WinDef.HWND): Boolean {
        return User32.INSTANCE.IsWindow(hWnd) && User32.INSTANCE.IsWindowVisible(hWnd)
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

        val processName = getProcessName(hWnd)

        val children = mutableListOf<UiNode>()
        // 限制深度避免過深
        if (depth < 5) {
            User32.INSTANCE.EnumChildWindows(hWnd, { childHWnd, _ ->
                if (User32.INSTANCE.GetParent(childHWnd) == hWnd) {
                    children.add(buildNode(childHWnd, depth + 1))
                }
                true
            }, null)
        }

        return UiNode(
            name = if (title.isEmpty()) (if (className.isEmpty()) "Unnamed" else className) else title,
            className = className,
            packageName = processName,
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

    private fun getProcessName(hWnd: WinDef.HWND): String {
        val pid = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hWnd, pid)
        
        val processHandle = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
            false,
            pid.value
        )

        if (processHandle != null) {
            try {
                val pathArray = CharArray(1024)
                val length = Psapi.INSTANCE.GetModuleFileNameExW(processHandle, null, pathArray, 1024)
                if (length > 0) {
                    val fullPath = Native.toString(pathArray).trim()
                    return File(fullPath).name
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(processHandle)
            }
        }
        return "Unknown"
    }
}
