# Android Agent APK 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 开发一款支持多厂商大模型、ReAct 模式任务规划、语音优先的 Android Agent APK

**Architecture:** 分层架构（UI Layer → Agent Core Layer → Perception Layer → Action Layer → Voice Layer → Infrastructure）

**Tech Stack:** Kotlin, AndroidX, MediaPipe LLM, OkHttp/Retrofit, Room Database, EncryptedSharedPreferences

---

## 文件结构总览

```
app/
├── src/main/
│   ├── java/com/agent/apk/
│   │   ├── AgentApplication.kt
│   │   ├── model/
│   │   │   ├── Task.kt
│   │   │   ├── Action.kt
│   │   │   ├── UiNode.kt
│   │   │   ├── VendorConfig.kt
│   │   │   └── ReActSession.kt
│   │   ├── infra/
│   │   │   ├── ApiKeyManager.kt
│   │   │   ├── DeviceCapabilityDetector.kt
│   │   │   ├── TaskHistoryDao.kt
│   │   │   ├── NetworkMonitor.kt
│   │   │   └── ReActStateManager.kt
│   │   ├── agent/
│   │   │   ├── TaskRouter.kt
│   │   │   ├── local/
│   │   │   │   ├── LocalAgent.kt
│   │   │   │   └── ModelManager.kt
│   │   │   └── cloud/
│   │   │       ├── CloudAgent.kt
│   │   │       ├── LlmClient.kt
│   │   │       └── ReActEngine.kt
│   │   ├── perception/
│   │   │   ├── AccessibilityScanner.kt
│   │   │   ├── ScreenshotManager.kt
│   │   │   └── ScreenAnalyzer.kt
│   │   ├── action/
│   │   │   ├── AccessibilityActionExecutor.kt
│   │   │   └── GesturePerformer.kt
│   │   ├── voice/
│   │   │   ├── SpeechToTextService.kt
│   │   │   └── TextToSpeechService.kt
│   │   └── ui/
│   │       ├── SettingsActivity.kt
│   │       ├── FloatingBallService.kt
│   │       └── components/
│   │
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_settings.xml
│   │   │   └── floating_ball.xml
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   └── themes.xml
│   │   ├── drawable/
│   │   │   └── ic_floating_ball.xml
│   │   └── xml/
│   │       └── accessibility_service_config.xml
│   │
│   └── AndroidManifest.xml
│
├── src/test/java/com/agent/apk/
│   ├── TaskRouterTest.kt
│   ├── ApiKeyManagerTest.kt
│   └── LlmClientTest.kt
│
├── build.gradle.kts
└── docs/
    ├── superpowers/specs/2026-03-26-android-agent-apk-design.md
    └── superpowers/plans/2026-03-26-android-agent-apk-implementation.md
```

---

## Phase 1: 项目脚手架 (基础设置)

### Task 1: 创建 Android 项目基础结构

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `gradle/libs.versions.toml`

（详细代码见上文，已写入）

---

### Task 2: 创建数据模型类

**Files:**
- Create: `app/src/main/java/com/agent/apk/model/Task.kt`
- Create: `app/src/main/java/com/agent/apk/model/Action.kt`
- Create: `app/src/main/java/com/agent/apk/model/UiNode.kt`
- Create: `app/src/main/java/com/agent/apk/model/VendorConfig.kt`
- Create: `app/src/main/java/com/agent/apk/model/ReActSession.kt`
- Create: `app/src/test/java/com/agent/apk/model/TaskTest.kt`

（详细代码见上文，已写入）

---

## Phase 2: 无障碍服务集成

### Task 3: 创建无障碍服务配置文件

**Files:**
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create accessibility service config**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- File: app/src/main/res/xml/accessibility_service_config.xml -->
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:packageNames=""
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:settingsActivity="com.agent.apk.ui.SettingsActivity"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:isAccessibilityTool="true" />
```

- [ ] **Step 2: Create string resource for description**

Modify: `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- File: app/src/main/res/values/strings.xml -->
<resources>
    <string name="app_name">AI Agent</string>
    <string name="accessibility_service_description">AI Agent 使用无障碍服务来理解屏幕内容并执行操作。它可以看到屏幕上的文字和按钮，模拟点击和滑动，但不会收集您的个人信息。您可以在设置中随时关闭此服务。</string>
    <string name="settings_title">AI Agent 设置</string>
    <string name="floating_ball_title">悬浮球</string>
    <string name="api_config_title">API 配置</string>
    <string name="model_config_title">模型配置</string>
    <string name="voice_config_title">语音配置</string>
    <string name="about_title">关于</string>
</resources>
```

- [ ] **Step 3: Commit**

```bash
cd "E:\破解\apk"
git add app/src/main/res/xml/
git add app/src/main/res/values/strings.xml
git commit -m "feat: add accessibility service configuration"
```

---

### Task 4: 实现 AccessibilityScanner 服务

**Files:**
- Create: `app/src/main/java/com/agent/apk/perception/AccessibilityScanner.kt`
- Create: `app/src/test/java/com/agent/apk/perception/AccessibilityScannerTest.kt`

- [ ] **Step 1: Write test for AccessibilityScanner**

```kotlin
// File: app/src/test/java/com/agent/apk/perception/AccessibilityScannerTest.kt
package com.agent.apk.perception

import com.agent.apk.model.UiTree
import org.junit.Test
import org.junit.Assert.*

class AccessibilityScannerTest {

    @Test
    fun `UiTree compression filters invisible nodes`() {
        // 验证 UiTree.toJson()只包含可见节点
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
                    bounds = com.agent.apk.model.Bounds(0, 0, 100, 100),
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
                    bounds = com.agent.apk.model.Bounds(0, 0, 100, 100),
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
}
```

- [ ] **Step 2: Create AccessibilityScanner**

```kotlin
// File: app/src/main/java/com/agent/apk/perception/AccessibilityScanner.kt
package com.agent.apk.perception

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.agent.apk.model.Bounds
import com.agent.apk.model.UiNode
import com.agent.apk.model.UiTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 无障碍服务：获取屏幕 UI 树
 */
class AccessibilityScanner : AccessibilityService() {

    companion object {
        /**
         * 获取全局实例（用于从其他组件调用）
         */
        var instance: AccessibilityScanner? = null
            private set

        /**
         * 当前屏幕 UI 树的 Flow
         */
        val uiTreeFlow: StateFlow<UiTree?> = _uiTreeFlow
        private val _uiTreeFlow = MutableStateFlow<UiTree?>(null)

        /**
         * 从当前屏幕获取 UI 树
         */
        fun getUiTreeSync(): UiTree? = _uiTreeFlow.value
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = (
                AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            )
        }
        setServiceInfo(info)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // 窗口状态变化时更新 UI 树
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            val uiTree = scanCurrentWindow()
            _uiTreeFlow.value = uiTree
        }
    }

    override fun onInterrupt() {
        // 服务被中断
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * 扫描当前窗口的 UI 树
     */
    private fun scanCurrentWindow(): UiTree? {
        val rootNode = rootInActiveWindow ?: return null
        val packageName = packageName?.toString() ?: ""
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val nodes = mutableListOf<UiNode>()
        traverseNode(rootNode, null, nodes)

        return UiTree(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            packageName = packageName,
            nodes = nodes
        )
    }

    /**
     * 递归遍历节点树
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        parentId: String?,
        result: MutableList<UiNode>
    ) {
        val nodeId = UUID.randomUUID().toString().take(8)

        val bounds = Bounds(
            left = node.boundsInScreen.left,
            top = node.boundsInScreen.top,
            right = node.boundsInScreen.right,
            bottom = node.boundsInScreen.bottom
        )

        val actions = node.actionList.map { it.name.toString() }

        val uiNode = UiNode(
            id = nodeId,
            className = node.className?.toString() ?: "Unknown",
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            bounds = bounds,
            actions = actions,
            parent = parentId,
            children = emptyList(), // 将在后续填充
            isVisible = node.isVisibleToUser,
            isEnabled = node.isEnabled,
            isChecked = if (node.isCheckable) node.isChecked else null,
            isSelected = node.isSelected
        )

        result.add(uiNode)

        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, nodeId, result)
            }
        }
    }

    /**
     * 根据 ID 查找节点
     */
    fun findNodeById(nodeId: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeRecursive(rootNode, nodeId)
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo, nodeId: String): AccessibilityNodeInfo? {
        // 简化实现：实际使用需要维护节点 ID 映射表
        return null
    }
}
```

- [ ] **Step 3: Run test**

Run: `./gradlew test --tests "com.agent.apk.perception.AccessibilityScannerTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
cd "E:\破解\apk"
git add app/src/main/java/com/agent/apk/perception/
git add app/src/test/java/com/agent/apk/perception/
git commit -m "feat: implement AccessibilityScanner service for screen content"
```

---

## Phase 3: Agent Core 层实现

### Task 5: 实现 TaskRouter 任务分发器

**Files:**
- Create: `app/src/main/java/com/agent/apk/agent/TaskRouter.kt`
- Create: `app/src/test/java/com/agent/apk/agent/TaskRouterTest.kt`

（代码略，按设计文档实现）

---

### Task 6: 实现 LlmClient 统一客户端

**Files:**
- Create: `app/src/main/java/com/agent/apk/agent/cloud/LlmClient.kt`
- Create: `app/src/main/java/com/agent/apk/agent/cloud/CloudAgent.kt`
- Create: `app/src/test/java/com/agent/apk/agent/cloud/LlmClientTest.kt`

（代码略，按设计文档实现）

---

### Task 7: 实现 ReActEngine

**Files:**
- Create: `app/src/main/java/com/agent/apk/agent/cloud/ReActEngine.kt`
- Create: `app/src/main/java/com/agent/apk/infra/ReActStateManager.kt`

（代码略，按设计文档实现）

---

## Phase 4: 本地模型集成

### Task 8: 实现 ModelManager

**Files:**
- Create: `app/src/main/java/com/agent/apk/agent/local/ModelManager.kt`
- Create: `app/src/main/java/com/agent/apk/agent/local/LocalAgent.kt`

（代码略，使用 MediaPipe LLM Inference）

---

## Phase 5: Action 层实现

### Task 9: 实现 AccessibilityActionExecutor

**Files:**
- Create: `app/src/main/java/com/agent/apk/action/AccessibilityActionExecutor.kt`
- Create: `app/src/main/java/com/agent/apk/action/GesturePerformer.kt`

（代码略，执行点击/滑动/输入等操作）

---

## Phase 6: UI 层实现

### Task 10: 实现设置界面

**Files:**
- Create: `app/src/main/java/com/agent/apk/ui/SettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_settings.xml`

（代码略，包含 API 配置、模型选择等界面）

---

### Task 11: 实现悬浮球服务

**Files:**
- Create: `app/src/main/java/com/agent/apk/ui/FloatingBallService.kt`
- Create: `app/src/main/res/layout/floating_ball.xml`
- Create: `app/src/main/res/drawable/ic_floating_ball.xml`

（代码略，常驻悬浮入口）

---

## Phase 7: 语音层和基础设施

### Task 12: 实现语音服务

**Files:**
- Create: `app/src/main/java/com/agent/apk/voice/SpeechToTextService.kt`
- Create: `app/src/main/java/com/agent/apk/voice/TextToSpeechService.kt`

（代码略，云端 STT/TTS）

---

### Task 13: 实现基础设施组件

**Files:**
- Create: `app/src/main/java/com/agent/apk/infra/ApiKeyManager.kt`
- Create: `app/src/main/java/com/agent/apk/infra/DeviceCapabilityDetector.kt`
- Create: `app/src/main/java/com/agent/apk/infra/TaskHistoryDao.kt`
- Create: `app/src/main/java/com/agent/apk/infra/NetworkMonitor.kt`

（代码略，API Key 加密存储、设备检测、数据库等）

---

## 执行选项

**计划已保存到：** `E:\破解\apk\docs\superpowers\plans\2026-03-26-android-agent-apk-implementation.md`

**两种执行方式：**

1. **Subagent-Driven（推荐）** - 每个任务 dispatch 一个子代理，自动进行 spec 审查 + 代码质量审查
2. **Inline Execution** - 在当前会话中按任务执行，带检查点

**选择哪种方式？**
