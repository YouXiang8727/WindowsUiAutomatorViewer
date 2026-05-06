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
    private val currentPid = Kernel32.INSTANCE.GetCurrentProcessId()

    fun captureScreenshot(): BufferedImage {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return robot.createScreenCapture(Rectangle(screenSize))
    }

    fun captureUiTree(): UiNode {
        val nodes = mutableListOf<UiNode>()
        val screenRect = Toolkit.getDefaultToolkit().screenSize
        
        User32.INSTANCE.EnumWindows({ hWnd, _ ->
            if (User32.INSTANCE.IsWindowVisible(hWnd) && !isCurrentProcess(hWnd)) {
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
            if (User32.INSTANCE.IsWindowVisible(hWnd) && !isCurrentProcess(hWnd)) {
                val titleArray = CharArray(1024)
                User32.INSTANCE.GetWindowText(hWnd, titleArray, 1024)
                val title = Native.toString(titleArray).trim()
                
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

    /**
     * 強制喚醒視窗到前台
     */
    fun focusWindow(hWnd: WinDef.HWND) {
        if (!User32.INSTANCE.IsWindow(hWnd)) return
        
        // 使用 GetWindowLong 檢查是否為最小化 (WS_MINIMIZE = 0x20000000, GWL_STYLE = -16)
        val style = User32.INSTANCE.GetWindowLong(hWnd, -16)
        if ((style and 0x20000000) != 0) {
            User32.INSTANCE.ShowWindow(hWnd, 9) // SW_RESTORE
        }
        
        // 帶到前台
        User32.INSTANCE.SetForegroundWindow(hWnd)
        User32.INSTANCE.ShowWindow(hWnd, 5) // SW_SHOW
    }

    fun getAppHWnd(): WinDef.HWND? {
        // 1. Try foreground window first
        val active = User32.INSTANCE.GetForegroundWindow()
        if (active != null && isCurrentProcess(active)) return active
        
        // 2. Search for visible window with title belonging to this PID
        var appHWnd: WinDef.HWND? = null
        User32.INSTANCE.EnumWindows({ hWnd, _ ->
            if (isCurrentProcess(hWnd) && User32.INSTANCE.IsWindowVisible(hWnd)) {
                if (User32.INSTANCE.GetWindowTextLength(hWnd) > 0) {
                    appHWnd = hWnd
                    false
                } else true
            } else true
        }, null)
        return appHWnd
    }

    private fun isCurrentProcess(hWnd: WinDef.HWND): Boolean {
        val pid = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hWnd, pid)
        return pid.value == currentPid
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
