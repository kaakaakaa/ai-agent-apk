// File: app/src/test/java/com/agent/apk/model/UiNodeTest.kt
package com.agent.apk.model

import org.junit.Test
import org.junit.Assert.*

class UiNodeTest {

    @Test
    fun `UiTree toJson filters invisible nodes`() {
        // 验证 UiTree.toJson() 只包含可见节点
        val uiTree = UiTree(
            screenWidth = 1080,
            screenHeight = 2400,
            packageName = "com.example",
            nodes = listOf(
                // 可见节点
                UiNode(
                    id = "node1",
                    className = "Button",
                    text = "确定",
                    contentDescription = null,
                    bounds = Bounds(0, 0, 100, 100),
                    actions = listOf("click"),
                    parent = null,
                    children = emptyList(),
                    isVisible = true,
                    isEnabled = true
                ),
                // 不可见节点
                UiNode(
                    id = "node2",
                    className = "TextView",
                    text = "Hidden",
                    contentDescription = null,
                    bounds = Bounds(0, 0, 100, 100),
                    actions = emptyList(),
                    parent = null,
                    children = emptyList(),
                    isVisible = false,
                    isEnabled = true
                )
            )
        )

        val json = uiTree.toJson()
        assertTrue(json.contains("确定"))
        assertFalse(json.contains("Hidden"))
    }

    @Test
    fun `UiNode toDescription includes text`() {
        val node = UiNode(
            id = "node1",
            className = "Button",
            text = "提交",
            contentDescription = null,
            bounds = Bounds(0, 0, 100, 100),
            actions = listOf("click"),
            parent = null,
            children = emptyList(),
            isVisible = true,
            isEnabled = true
        )

        val desc = node.toDescription()
        assertTrue(desc.contains("Button"))
        assertTrue(desc.contains("text=\"提交\""))
    }

    @Test
    fun `UiNode toDescription includes disabled state`() {
        val node = UiNode(
            id = "node1",
            className = "Button",
            text = "提交",
            contentDescription = null,
            bounds = Bounds(0, 0, 100, 100),
            actions = listOf("click"),
            parent = null,
            children = emptyList(),
            isVisible = true,
            isEnabled = false
        )

        val desc = node.toDescription()
        assertTrue(desc.contains("disabled"))
    }
}
