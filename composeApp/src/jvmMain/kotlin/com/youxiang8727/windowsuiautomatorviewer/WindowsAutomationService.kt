package com.youxiang8727.windowsuiautomatorviewer

import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.uia.*
import androidx.compose.ui.geometry.Rect
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.image.BufferedImage

class WindowsAutomationService {
    private val robot = Robot()
    private var automation: IUIAutomation? = null

    init {
        // 初始化 COM 庫
        Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        try {
            automation = IUIAutomation.create()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun captureScreenshot(): BufferedImage {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return robot.createScreenCapture(Rectangle(screenSize))
    }

    fun captureUiTree(): UiNode {
        val rootElement = automation?.GetRootElement() ?: return UiNode("Error", "None", "None", Rect.Zero)
        return walkElement(rootElement, depth = 0)
    }

    private fun walkElement(element: IUIAutomationElement, depth: Int): UiNode {
        val name = try { element.currentName ?: "" } catch (e: Exception) { "" }
        val className = try { element.currentClassName ?: "" } catch (e: Exception) { "" }
        val controlType = try { 
            getControlTypeName(element.currentControlType)
        } catch (e: Exception) { "Unknown" }

        val bounds = try {
            val rect = element.currentBoundingRectangle
            Rect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
        } catch (e: Exception) {
            Rect.Zero
        }

        val children = mutableListOf<UiNode>()
        // 限制深度以防遞迴過深導致當機，實務上可根據需要調整
        if (depth < 7) { 
            val condition = automation?.CreateTrueCondition()
            val elementArray = element.FindAll(TreeScope.TreeScope_Children, condition)
            if (elementArray != null) {
                for (i in 0 until elementArray.length) {
                    val child = elementArray.GetElement(i)
                    if (child != null) {
                        val childNode = walkElement(child, depth + 1)
                        // 只加入有意義的節點（例如有名字或是特定型態）
                        if (childNode.name.isNotEmpty() || childNode.children.isNotEmpty() || childNode.controlType != "Pane") {
                            children.add(childNode)
                        }
                    }
                }
            }
        }

        return UiNode(
            name = name,
            className = className,
            controlType = controlType,
            bounds = bounds,
            children = children,
            properties = mapOf(
                "AutomationId" to (try { element.currentAutomationId ?: "" } catch (e: Exception) { "" }),
                "IsEnabled" to (try { element.currentIsEnabled.toString() } catch (e: Exception) { "" }),
                "LocalizedControlType" to (try { element.currentLocalizedControlType ?: "" } catch (e: Exception) { "" })
            )
        )
    }

    private fun getControlTypeName(typeId: Int): String {
        return when (typeId) {
            UIA_ControlIds.UIA_WindowControlTypeId -> "Window"
            UIA_ControlIds.UIA_ButtonControlTypeId -> "Button"
            UIA_ControlIds.UIA_TextControlTypeId -> "Text"
            UIA_ControlIds.UIA_EditControlTypeId -> "Edit"
            UIA_ControlIds.UIA_ListControlTypeId -> "List"
            UIA_ControlIds.UIA_PaneControlTypeId -> "Pane"
            UIA_ControlIds.UIA_GroupControlTypeId -> "Group"
            UIA_ControlIds.UIA_MenuItemControlTypeId -> "MenuItem"
            UIA_ControlIds.UIA_TreeItemControlTypeId -> "TreeItem"
            else -> "Control($typeId)"
        }
    }
}
