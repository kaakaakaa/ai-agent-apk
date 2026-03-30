// File: app/src/test/java/com/agent/apk/model/TaskTest.kt
package com.agent.apk.model

import org.junit.Test
import org.junit.Assert.*

class TaskTest {

    @Test
    fun `simple command detection - open app`() {
        val task = Task(
            id = "1",
            userInstruction = "打开微信",
            timestamp = System.currentTimeMillis()
        )
        assertTrue(task.isSimpleCommand())
    }

    @Test
    fun `simple command detection - back navigation`() {
        val task = Task(
            id = "2",
            userInstruction = "返回",
            timestamp = System.currentTimeMillis()
        )
        assertTrue(task.isSimpleCommand())
    }

    @Test
    fun `simple command detection - home`() {
        val task = Task(
            id = "3",
            userInstruction = "主页",
            timestamp = System.currentTimeMillis()
        )
        assertTrue(task.isSimpleCommand())
    }

    @Test
    fun `complex task - multi-step`() {
        val task = Task(
            id = "4",
            userInstruction = "帮我订一杯咖啡",
            timestamp = System.currentTimeMillis()
        )
        assertFalse(task.isSimpleCommand())
    }

    @Test
    fun `complex task - cross-app operation`() {
        val task = Task(
            id = "5",
            userInstruction = "把这张照片发给张三",
            timestamp = System.currentTimeMillis()
        )
        assertFalse(task.isSimpleCommand())
    }
}
