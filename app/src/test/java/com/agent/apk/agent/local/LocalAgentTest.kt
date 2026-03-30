// File: app/src/test/java/com/agent/apk/agent/local/LocalAgentTest.kt
package com.agent.apk.agent.local

import org.junit.Test
import org.junit.Assert.*

class LocalAgentTest {

    @Test
    fun `parseActionLine parses open_app action`() {
        val action = parseActionLine("open_app: com.tencent.mm")

        assertTrue(action is OpenAppAction)
        assertEquals("com.tencent.mm", (action as OpenAppAction).packageName)
    }

    @Test
    fun `parseActionLine parses click action`() {
        val action = parseActionLine("click: 500, 1000")

        assertTrue(action is ClickAction)
        val click = action as ClickAction
        assertEquals(500, click.x)
        assertEquals(1000, click.y)
    }

    @Test
    fun `parseActionLine parses swipe action`() {
        val action = parseActionLine("swipe: 100, 200, 300, 400, 500")

        assertTrue(action is SwipeAction)
        val swipe = action as SwipeAction
        assertEquals(100, swipe.fromX)
        assertEquals(200, swipe.fromY)
        assertEquals(300, swipe.toX)
        assertEquals(400, swipe.toY)
        assertEquals(500L, swipe.durationMs)
    }

    @Test
    fun `parseActionLine parses type action`() {
        val action = parseActionLine("type: 你好")

        assertTrue(action is TypeAction)
        assertEquals("你好", (action as TypeAction).text)
    }

    @Test
    fun `parseActionLine parses navigate action`() {
        val backAction = parseActionLine("navigate: back")
        assertTrue(backAction is NavigateAction)
        assertEquals("back", (backAction as NavigateAction).target)

        val homeAction = parseActionLine("navigate: home")
        assertTrue(homeAction is NavigateAction)
        assertEquals("home", (homeAction as NavigateAction).target)
    }

    @Test
    fun `parseActionLine parses complete action`() {
        val action = parseActionLine("complete: 任务已完成")

        assertTrue(action is CompleteTaskAction)
        assertEquals("任务已完成", (action as CompleteTaskAction).result)
    }

    @Test
    fun `parseActionLine returns null for unknown action`() {
        val action = parseActionLine("unknown: something")
        assertNull(action)
    }

    @Test
    fun `parseAction extracts action from LLM response`() {
        val response = """
            思考：用户想要打开微信应用
            操作：open_app: com.tencent.mm
        """.trimIndent()

        val action = parseActionFromResponse(response)

        assertTrue(action is OpenAppAction)
        assertEquals("com.tencent.mm", (action as OpenAppAction).packageName)
    }

    @Test
    fun `parseAction returns null when no action in response`() {
        val response = "这是一个普通的回复，没有操作"

        val action = parseActionFromResponse(response)
        assertNull(action)
    }
}

/**
 * 从 LLM 响应中解析 Action
 */
private fun parseActionFromResponse(response: String): Action? {
    val lines = response.lines()

    for (line in lines) {
        if (line.startsWith("操作：") || line.startsWith("操作:")) {
            val actionPart = line.substringAfter("操作：").substringAfter("操作:").trim()
            return parseActionLine(actionPart)
        }
    }

    return null
}

private fun parseActionLine(actionLine: String): Action? {
    return when {
        actionLine.startsWith("open_app:") -> {
            val packageName = actionLine.substringAfter("open_app:").trim()
            OpenAppAction(packageName = packageName, reason = "打开应用")
        }
        actionLine.startsWith("click:") -> {
            val coords = actionLine.substringAfter("click:").trim().split(",")
            if (coords.size == 2) {
                ClickAction(
                    x = coords[0].trim().toIntOrNull() ?: 0,
                    y = coords[1].trim().toIntOrNull() ?: 0,
                    reason = "点击坐标"
                )
            } else null
        }
        actionLine.startsWith("swipe:") -> {
            val params = actionLine.substringAfter("swipe:").trim().split(",")
            if (params.size == 5) {
                SwipeAction(
                    fromX = params[0].trim().toIntOrNull() ?: 0,
                    fromY = params[1].trim().toIntOrNull() ?: 0,
                    toX = params[2].trim().toIntOrNull() ?: 0,
                    toY = params[3].trim().toIntOrNull() ?: 0,
                    durationMs = params[4].trim().toLongOrNull() ?: 300,
                    reason = "滑动手势"
                )
            } else null
        }
        actionLine.startsWith("type:") -> {
            val text = actionLine.substringAfter("type:").trim()
            TypeAction(text = text, reason = "输入文本")
        }
        actionLine.startsWith("navigate:") -> {
            val target = actionLine.substringAfter("navigate:").trim()
            NavigateAction(target = target, reason = "导航操作")
        }
        actionLine.startsWith("complete:") -> {
            CompleteTaskAction(
                result = actionLine.substringAfter("complete:").trim(),
                reason = "任务完成"
            )
        }
        else -> null
    }
}
