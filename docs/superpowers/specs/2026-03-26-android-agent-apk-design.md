# Android Agent APK 设计文档

**日期：** 2026-03-26
**版本：** 1.0
**状态：** 待审查

---

## 1. 概述

### 1.1 项目目标

开发一款 Android APK，内置智能 Agent，能够：
- 理解用户语音/文本指令
- 识别屏幕内容（UI 树 + 截图）
- 自主操控手机（点击、滑动、输入）
- 支持**多厂商大模型**（阿里云百炼、DeepSeek、Kimi、通义千问、Azure OpenAI）
- 像"豆包 AI 手机"一样完成复杂任务

### 1.2 目标用户

- 普通用户：通过语音/文本与手机交互
- **盲人用户**：语音优先，无障碍支持
- **不识字的用户**：全语音交互，大图标界面

### 1.3 核心特性

| 特性 | 描述 |
|------|------|
| 语音优先 | 支持语音唤醒、语音指令、语音反馈 |
| 屏幕理解 | Accessibility UI 树 + 多模态视觉分析 |
| 自主操作 | 通过 Accessibility Service 执行点击/滑动/输入 |
| 动态降级 | 根据手机配置自动选择本地/云端模型 |
| 离线备用 | 无网络时本地模型接管简单任务 |
| **ReAct 模式** | 思考→行动→观察循环，支持多步骤任务规划 |
| **多厂商支持** | 统一接口接入阿里云百炼、DeepSeek、Kimi、通义千问、Azure OpenAI |
| **模型热切换** | 运行时可随时切换不同厂商的模型 |

---

## 2. 系统架构

### 2.1 分层架构图

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │  悬浮球     │  │  设置界面   │  │  通知栏入口     │ │
│  │  Floating   │  │  Settings   │  │  Notification   │ │
│  │  Ball       │  │  Activity   │  │  Action         │ │
│  └─────────────┘  └─────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────┤
│                 Agent Core Layer                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Task Router (任务分发器)            │   │
│  │  - 意图识别                                      │   │
│  │  - 本地/云端路由决策                             │   │
│  │  - 任务队列管理                                  │   │
│  └──────────────────────────────────────────────────┘   │
│         ┌───────────────────┬───────────────────┐       │
│         ▼                   ▼                   │       │
│  ┌─────────────┐    ┌─────────────┐             │       │
│  │ Local Agent │    │ Cloud Agent │             │       │
│  │ Gemma 2B /  │    │ Qwen API    │             │       │
│  │ Qwen 0.5B   │    │             │             │       │
│  └─────────────┘    └─────────────┘             │       │
├─────────────────────────────────────────────────────────┤
│              Perception Layer (感知层)                  │
│  ┌─────────────────────────────┐  ┌─────────────────┐  │
│  │ AccessibilityScanner        │  │ Screenshot      │  │
│  │ - UI 树遍历                  │  │ - 屏幕捕获      │  │
│  │ - 元素信息提取              │  │ - 压缩上传      │  │
│  │ - 层级关系分析              │  │ - 多模态分析    │  │
│  └─────────────────────────────┘  └─────────────────┘  │
├─────────────────────────────────────────────────────────┤
│               Action Layer (执行层)                     │
│  ┌─────────────────────────────────────────────────────┐│
│  │ AccessibilityActionExecutor                         ││
│  │ - click(), longClick(), scroll()                    ││
│  │ - setText(), getText()                              ││
│  │ - navigateBack(), goHome()                          ││
│  │ - 手势模拟 (滑动手势)                                ││
│  └─────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────┤
│                Voice Layer (语音层)                     │
│  ┌─────────────────────┐  ┌─────────────────────────┐  │
│  │ Cloud STT Service   │  │ Cloud TTS Service       │  │
│  │ - Azure Speech      │  │ - Azure TTS             │  │
│  │ - 通义听悟           │  │ - 通义听悟               │  │
│  └─────────────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│              Infrastructure Layer                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ API Key     │  │ 设备检测    │  │ 日志/历史       │ │
│  │ 加密存储    │  │ 降级决策    │  │ 任务记录        │ │
│  └─────────────┘  └─────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 模块设计

### 3.1 UI Layer

#### 3.1.1 悬浮球 (FloatingBallService)

**职责：** 常驻悬浮入口，用户点击后唤起交互界面

**功能：**
- 可拖动悬浮球（类似 Assistive Touch）
- 单击：弹出快捷菜单（语音输入/文本输入/历史记录）
- 长按：直接开始语音输入
- 双击：唤醒最近任务

**技术实现：**
- `Service` + `WindowManager` 实现悬浮窗
- 前景服务保活
- 支持透明度调节

#### 3.1.2 设置界面 (SettingsActivity)

**职责：** 配置管理、API Key 设置、模型选择

**界面结构：**
```
设置主界面
├── API 配置
│   ├── 预设模板选择
│   │   ├── 阿里云百炼 (Qwen-Max + 通义听悟 STT/TTS)
│   │   ├── DeepSeek (deepseek-chat)
│   │   ├── Kimi 月之暗面 (moonshot-v1-128k)
│   │   ├── 通义千问 DashScope (qwen-max)
│   │   ├── Azure OpenAI (GPT-4o)
│   │   └── 自定义混合 (LLM / STT / TTS 分别配置)
│   ├── LLM 配置
│   │   ├── 厂商选择
│   │   ├── API Key 输入
│   │   ├── Base URL (自定义厂商时使用)
│   │   └── 模型选择 (根据厂商动态加载)
│   ├── STT 配置
│   │   ├── 厂商选择 ( Azure Speech / 通义听悟 / 本地)
│   │   └── API Key 输入
│   └── TTS 配置
│       ├── 厂商选择 (Azure TTS / 通义听悟 / 系统 TTS)
│       └── API Key 输入
├── 模型设置
│   ├── 本地模型开关
│   ├── 云端优先/本地优先切换
│   ├── 默认使用的云端模型
│   └── 降级阈值设置
├── 语音设置
│   ├── 语音唤醒开关
│   ├── TTS 音色选择
│   └── 语速调节
├── 无障碍设置
│   ├── 权限引导
│   └── 功能测试
├── 高级设置
│   ├── ReAct 模式最大步数 (默认 10 步)
│   ├── 单步超时时间 (默认 30 秒)
│   ├── 是否允许自动截图
│   └── 调试模式 (显示 Thought/Action/Observation)
└── 关于
    ├── 版本信息
    └── 帮助文档
```

**技术实现：**
- `AppCompatActivity` + `PreferenceFragmentCompat`
- API Key 使用 `AndroidKeystore` 加密存储
- 厂商配置支持导入/导出（JSON 格式）

#### 3.1.3 通知栏入口 (NotificationManager)

**职责：** 系统通知栏快捷入口

**功能：**
- 常驻通知（Agent 运行中）
- 快捷操作按钮：语音输入 / 停止任务
- 点击通知：打开快捷菜单

---

### 3.2 Agent Core Layer

#### 3.2.1 Task Router (任务分发器)

**职责：** 接收用户请求，分发给本地或云端 Agent；支持 ReAct 模式的任务规划

**路由决策逻辑：**
```kotlin
fun route(task: Task): AgentTarget {
    return when {
        // 无网络 → 本地
        networkStatus.isOffline() -> AgentTarget.LOCAL

        // 用户强制云端 → 云端
        settings.cloudFirst -> AgentTarget.CLOUD

        // 简单指令 → 本地
        task.isSimpleCommand() -> {
            if (deviceCapability.canRunGemma2B()) {
                AgentTarget.LOCAL_GEMMA_2B
            } else if (deviceCapability.canRunQwen0_5B()) {
                AgentTarget.LOCAL_QWEN_0_5B
            } else {
                AgentTarget.CLOUD
            }
        }

        // 复杂任务 → 云端 (ReAct 模式)
        else -> AgentTarget.CLOUD
    }
}
```

**简单指令定义：**
- 打开应用："打开微信"
- 基础导航："返回"、"主页"、"最近任务"
- 简单点击："点击确定按钮"
- 系统操作："调高音量"、"打开蓝牙"

**复杂任务定义（需要 ReAct 模式）：**
- 多步骤任务："帮我订一杯咖啡"
- 语义理解："找出未接来电并回复"
- 跨应用操作："把这张照片发给张三"
- 需要推理的任务

#### 3.2.2 Local Agent (本地代理)

**职责：** 离线/快速响应场景

**支持的模型：**
| 模型 | 适用设备 | 内存占用 | 推理速度 |
|------|----------|----------|----------|
| Gemma 2B | RAM ≥ 6GB, SD 8+ Gen1+ | ~3GB | 8-15 tok/s |
| Qwen1.5-0.5B | RAM ≥ 4GB, SD 778G+ | ~1GB | 15-25 tok/s |

**技术栈：**
- Gemma 2B: MediaPipe LLM Inference
- Qwen 0.5B: MLC LLM 或 ExecuTorch

**Prompt 模板：**
```
你是一个 Android 手机助手，可以控制手机完成各种任务。

当前屏幕内容：
{ui_tree_json}

用户指令：{user_input}

请输出要执行的操作，格式为 JSON：
{
  "action": "click" | "swipe" | "type" | "back" | "home" | ...,
  "target": "元素描述或坐标",
  "reason": "选择这个操作的原因"
}
```

#### 3.2.3 Cloud Agent (云端代理)

**职责：** 复杂任务处理、多模态理解、ReAct 模式任务规划

**支持的多厂商 API：**

| 厂商 | 配置名称 | Base URL | 模型列表 | 多模态 |
|------|----------|----------|----------|--------|
| 阿里云百炼 | `aliyun-bailian` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-max, qwen-plus, qwen-turbo | ✅ |
| DeepSeek | `deepseek` | `https://api.deepseek.com` | deepseek-chat, deepseek-coder | ❌ |
| Kimi (月之暗面) | `kimi` | `https://api.moonshot.cn/v1` | moonshot-v1-8k, moonshot-v1-32k, moonshot-v1-128k | ✅ |
| 通义千问 (DashScope) | `dashscope` | `https://dashscope.aliyuncs.com/compatible-mode/v1` | qwen-max, qwen-plus | ✅ |
| Azure OpenAI | `azure-openai` | `https://{resource}.openai.azure.com` | gpt-4o, gpt-4-turbo | ✅ |
| 自定义 (OpenAI 兼容) | `custom` | 用户自定义 | 用户自定义 | 可选 |

**统一 API 客户端设计：**
```kotlin
// 所有厂商统一使用 OpenAI 兼容格式
interface LlmClient {
    suspend fun chatCompletion(request: ChatRequest): ChatResponse
    suspend fun chatWithVision(request: VisionChatRequest): VisionChatResponse
}

// 统一请求格式
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<Tool>? = null,  // 工具调用（函数调用）
    val temperature: Float = 0.7f
)

data class Message(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

data class Tool(
    val type: String,  // "function"
    val function: FunctionDefinition
)
```

**ReAct 模式 Prompt 模板：**
```
你是一个智能 Android 手机助手，可以通过 ReAct 模式完成复杂任务。

ReAct 模式工作流程：
1. Thought (思考)：分析当前情况，决定下一步行动
2. Action (行动)：执行一个操作
3. Observation (观察)：获取操作结果
4. 重复 1-3 直到任务完成

可用工具：
- click(element): 点击元素
- swipe(direction): 滑动屏幕
- type(text): 输入文本
- openApp(packageName): 打开应用
- goBack(): 返回
- goHome(): 回到主页
- screenshot(): 获取屏幕截图

当前时间：{timestamp}
用户指令：{user_input}

当前屏幕内容（UI 树）：
{ui_tree_json}

{如果有截图}
当前屏幕截图已附加。

请按照 ReAct 模式输出：

Thought: [分析用户意图和当前屏幕状态]
Action: [要执行的操作]
Observation: [等待执行结果后填入]
Thought: [根据观察结果继续思考]
...
Final Answer: [任务完成后的总结]
```

**多轮对话管理：**
```kotlin
data class ReActSession(
    val taskId: String,
    val userGoal: String,
    val conversationHistory: List<Message>,
    val executedActions: List<ActionRecord>,
    val startTime: Long
)

// Agent 需要维护会话状态，支持多轮对话
interface CloudAgent {
    fun startTask(goal: String): ReActSession
    suspend fun step(session: ReActSession, observation: String): ReActResult
    fun endTask(session: ReActSession)
}

data class ReActResult(
    val thought: String,
    val action: Action?,  // null 表示任务完成
    val finalAnswer: String?,  // 任务完成时返回
    val isComplete: Boolean
)
```

---

### 3.3 Perception Layer (感知层)

#### 3.3.1 AccessibilityScanner (无障碍扫描器)

**职责：** 获取当前屏幕 UI 树

**功能：**
- 监听 `AccessibilityEvent`
- 获取 `AccessibilityNodeInfo` 树
- 提取关键信息：
  - 文本内容
  - 元素类型（按钮、输入框、图片等）
  - 边界坐标
  - 可执行操作
  - 层级关系

**输出格式：**
```json
{
  "screen_width": 1080,
  "screen_height": 2400,
  "nodes": [
    {
      "id": "node_1",
      "class": "android.widget.Button",
      "text": "确定",
      "bounds": {"left": 100, "top": 500, "right": 300, "bottom": 600},
      "actions": ["click"],
      "parent": null,
      "children": []
    }
  ]
}
```

#### 3.3.2 ScreenshotManager (截图管理器)

**职责：** 屏幕截图捕获与处理

**功能：**
- 定时截图（可配置间隔）
- 事件触发截图（检测到新界面）
- 图像压缩（WebP，质量 80%）
- 本地缓存管理

**技术实现：**
- `MediaProjection` API 获取屏幕内容
- 后台服务持续运行

---

### 3.4 Action Layer (执行层)

#### 3.4.1 AccessibilityActionExecutor

**职责：** 执行具体操作

**支持的操作：**
```kotlin
interface ActionExecutor {
    fun click(nodeInfo: AccessibilityNodeInfo): Boolean
    fun longClick(nodeInfo: AccessibilityNodeInfo): Boolean
    fun scroll(nodeInfo: AccessibilityNodeInfo, direction: Int): Boolean
    fun setText(nodeInfo: AccessibilityNodeInfo, text: String): Boolean
    fun getText(nodeInfo: AccessibilityNodeInfo): String?
    fun navigateBack(): Boolean
    fun goHome(): Boolean
    fun openApp(packageName: String): Boolean
    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long): Boolean
    fun pressKeyCode(keyCode: Int): Boolean
}
```

**手势模拟：** 使用 `GestureDescription` 实现复杂手势

---

### 3.5 Voice Layer (语音层)

#### 3.5.1 Cloud STT Service

**职责：** 语音转文字

**支持的 API：**
- Azure Speech Service
- 阿里云通义听悟

**功能：**
- 实时语音识别
- 支持中文普通话及方言
- 降噪处理

#### 3.5.2 Cloud TTS Service

**职责：** 文字转语音

**支持的 API：**
- Azure Neural TTS
- 阿里云通义听悟

**功能：**
- 多音色选择
- 语速调节
- 情感化语音

---

### 3.6 Infrastructure Layer

#### 3.6.1 APIKeyManager (API Key 管理)

**职责：** 安全存储多厂商 API Key

**支持的厂商配置：**
```kotlin
data class VendorConfig(
    val id: String,  // "aliyun-bailian", "deepseek", "kimi", etc.
    val name: String,  // 显示名称
    val baseUrl: String,  // API Base URL
    val apiKey: String,  // 加密存储
    val models: List<String>,  // 支持的模型列表
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val isActive: Boolean  // 是否当前启用
)

data class ApiConfig(
    val llmVendor: String,  // 当前使用的 LLM 厂商
    val sttVendor: String,  // 当前使用的 STT 厂商
    val ttsVendor: String,  // 当前使用的 TTS 厂商
    val vendorConfigs: Map<String, VendorConfig>  // 所有厂商配置
)
```

**技术实现：**
- Android Keystore 系统
- AES-256 加密
- Key 存储在 `EncryptedSharedPreferences`
- 支持配置导入/导出（JSON 格式，加密）

#### 3.6.2 DeviceCapabilityDetector (设备检测)

**职责：** 检测设备配置，决策降级策略

**检测项：**
- RAM 大小
- SoC 型号
- 可用存储空间
- Android 版本

**决策逻辑：**
```kotlin
data class DeviceCapability(
    val ramGb: Float,
    val socModel: String,
    val availableStorageGb: Long,
    val androidVersion: Int
)

fun getLocalModelLevel(capability: DeviceCapability): LocalModelLevel {
    return when {
        capability.ramGb >= 6 && capability.socModel.isHighEnd() ->
            LocalModelLevel.GEMMA_2B
        capability.ramGb >= 4 && capability.availableStorageGb >= 3 ->
            LocalModelLevel.QWEN_0_5B
        else ->
            LocalModelLevel.CLOUD_ONLY
    }
}
```

#### 3.6.3 TaskHistoryManager (任务历史)

**职责：** 记录任务执行历史

**存储内容：**
- 用户指令
- 执行的操作序列
- 执行结果
- 时间戳
- 使用的模型（用于分析哪个模型表现更好）

**技术实现：**
- Room Database 本地存储

#### 3.6.4 ReActStateManager (ReAct 状态管理)

**职责：** 管理 ReAct 模式的会话状态

**功能：**
- 维护当前任务的对话历史
- 记录已执行的操作
- 检测无限循环（同一操作重复执行）
- 超时处理
- 支持暂停/恢复任务

**技术实现：**
- 内存缓存 + Room 持久化
- 支持后台继续执行

---

## 4. 数据流

### 4.1 语音任务流程

```
用户说话
    │
    ▼
悬浮球长按 / 通知栏入口
    │
    ▼
Cloud STT (语音→文本)
    │
    ▼
Task Router (路由决策)
    ├─→ Local Agent → 输出操作
    │                        │
    └─→ Cloud Agent → 输出操作
                             │
                             ▼
                    AccessibilityActionExecutor
                             │
                             ▼
                    执行操作并语音反馈 (TTS)
```

### 4.2 文本任务流程

```
用户输入文本
    │
    ▼
悬浮球菜单 → 文本输入框
    │
    ▼
Task Router (路由决策)
    ├─→ Local Agent → 输出操作
    │                        │
    └─→ Cloud Agent → 输出操作
                             │
                             ▼
                    AccessibilityActionExecutor
                             │
                             ▼
                    执行操作并显示结果
```

### 4.3 屏幕理解流程

```
定时触发 / 事件触发
    │
    ▼
AccessibilityScanner 获取 UI 树
    │
    ▼
是否需要视觉分析？
    ├─ No → 仅使用 UI 树
    │
    └─ Yes → ScreenshotManager 截图
                │
                ▼
            上传云端 (Cloud Agent)
                │
                ▼
            多模态分析 (UI 树 + 图像)
```

---

## 5. 权限设计

### 5.1 必需权限

```xml
<!-- 无障碍服务 -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- 前台服务 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- 悬浮窗 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- 麦克风（语音输入） -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 通知 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 网络 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 存储（可选，保存截图/历史） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    android:maxSdkVersion="28" />
```

### 5.2 权限请求流程

```
首次启动
    │
    ▼
1. 请求通知权限
    │
    ▼
2. 请求麦克风权限
    │
    ▼
3. 引导开启无障碍服务 (跳转设置页)
    │
    ▼
4. 请求悬浮窗权限
    │
    ▼
5. (可选) 请求存储权限
```

---

## 6. 安全设计

### 6.1 API Key 加密存储

```kotlin
// 使用 EncryptedSharedPreferences
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "api_keys",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### 6.2 数据本地化

- 截图默认仅本地处理
- 仅复杂任务上传云端
- 用户可配置"是否上传截图"

### 6.3 权限最小化

- 仅请求必要权限
- 首次启动明确告知用途
- 设置中提供权限说明

---

## 7. 错误处理

### 7.1 网络错误

```kotlin
when (exception) {
    is NoNetworkException → 降级到本地模型
    is ApiKeyInvalidException → 提示用户重新配置
    is RateLimitException → 等待后重试 / 降级本地
    is TimeoutException → 重试 1 次后降级
}
```

### 7.2 无障碍服务错误

- 服务未开启 → 引导用户开启
- 元素找不到 → 重新扫描屏幕
- 操作失败 → 语音反馈 + 记录日志

### 7.3 本地模型错误

- 加载失败 → 降级到云端
- 推理超时 → 本次任务转云端
- OOM → 释放内存，降级到云端

---

## 8. 性能指标

| 指标 | 目标值 |
|------|--------|
| 语音唤醒延迟 | < 500ms |
| 云端响应时间 | < 3s (P95) |
| 本地推理时间 | < 5s (Gemma 2B) |
| 操作执行准确率 | > 95% |
| 内存占用 | < 500MB (不含模型) |
| 电池消耗 | < 5%/小时 (后台运行) |

---

## 9. 项目结构

```
app/
├── src/main/
│   ├── java/com/agent/apk/
│   │   ├── AgentApplication.kt          # Application 入口
│   │   │
│   │   ├── ui/
│   │   │   ├── SettingsActivity.kt      # 设置界面
│   │   │   ├── FloatingBallService.kt   # 悬浮球服务
│   │   │   └── components/              # UI 组件
│   │   │
│   │   ├── agent/
│   │   │   ├── TaskRouter.kt            # 任务分发器
│   │   │   ├── local/
│   │   │   │   ├── LocalAgent.kt        # 本地 Agent
│   │   │   │   └── ModelManager.kt      # 本地模型管理
│   │   │   └── cloud/
│   │   │       ├── CloudAgent.kt        # 云端 Agent
│   │   │       └── ApiClient.kt         # API 客户端
│   │   │
│   │   ├── perception/
│   │   │   ├── AccessibilityScanner.kt  # UI 树扫描
│   │   │   ├── ScreenshotManager.kt     # 截图管理
│   │   │   └── ScreenAnalyzer.kt        # 屏幕分析
│   │   │
│   │   ├── action/
│   │   │   ├── AccessibilityActionExecutor.kt
│   │   │   └── GesturePerformer.kt
│   │   │
│   │   ├── voice/
│   │   │   ├── SpeechToTextService.kt
│   │   │   └── TextToSpeechService.kt
│   │   │
│   │   ├── infra/
│   │   │   ├── ApiKeyManager.kt         # API Key 管理
│   │   │   ├── DeviceCapabilityDetector.kt
│   │   │   ├── TaskHistoryDao.kt        # 数据库
│   │   │   └── NetworkMonitor.kt        # 网络监控
│   │   │
│   │   └── model/
│   │       ├── Task.kt
│   │       ├── Action.kt
│   │       └── UiNode.kt
│   │
│   ├── res/
│   │   ├── layout/                      # 布局文件
│   │   ├── values/                      # 字符串、样式
│   │   ├── drawable/                    # 图标
│   │   └── xml/
│   │       └── accessibility_service_config.xml
│   │
│   └── AndroidManifest.xml
│
├── build.gradle.kts
└── docs/
    └── superpowers/specs/
        └── 2026-03-26-android-agent-apk-design.md
```

---

## 10. 开发依赖

### 10.1 核心依赖

```kotlin
// AndroidX Core
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.11.0")

// MediaPipe (Gemma 2B)
implementation("com.google.ai.edge.litert:litert:1.0.0")
implementation("com.google.mediapipe:llm-inference:1.0.0")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// Encrypted SharedPreferences
implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

// OkHttp + Retrofit (网络请求)
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.retrofit2:retrofit:2.9.0")

// Kotlin Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

// JSON 解析
implementation("com.google.code.gson:gson:2.10.1")
```

### 10.2 开发环境

- Android Studio Hedgehog (2023.1.1) 或更新
- Gradle 8.2+
- Kotlin 1.9+
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)

---

## 11. 测试策略

### 11.1 单元测试

- TaskRouter 路由逻辑
- API Key 加密/解密
- 设备检测逻辑

### 11.2 集成测试

- Accessibility Service 操作执行
- 本地模型加载与推理
- 云端 API 调用

### 11.3 UI 测试

- 设置界面交互
- 悬浮球功能
- 通知栏操作

### 11.4 端到端测试

- 完整语音任务流程
- 文本任务流程
- 离线降级场景

---

## 12. 里程碑

| 阶段 | 内容 | 预计时间 |
|------|------|----------|
| Phase 1 | 项目脚手架 + 基础 UI | 1 周 |
| Phase 2 | Accessibility Service 集成 | 1 周 |
| Phase 3 | 云端 Agent 集成 | 1 周 |
| Phase 4 | 本地模型集成 (Gemma 2B) | 2 周 |
| Phase 5 | 语音 STT/TTS 集成 | 1 周 |
| Phase 6 | 任务路由 + 降级逻辑 | 1 周 |
| Phase 7 | 测试 + 优化 | 2 周 |
| **总计** | | **9 周** |

---

## 13. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Gemma 2B 兼容性问题 | 高 | 准备 Qwen 0.5B 备选 |
| 云端 API 延迟高 | 中 | 本地降级 + 超时处理 |
| 无障碍服务被系统杀死 | 中 | 前台服务保活 + 白名单引导 |
| 电池消耗过大 | 中 | 智能休眠 + 用户可配置 |
| 模型文件过大 | 低 | 可选下载 + 增量更新 |

---

## 14. 审查清单

- [ ] 架构设计是否清晰？
- [ ] 模块划分是否合理？
- [ ] 权限设计是否最小化？
- [ ] 降级策略是否完善？
- [ ] 是否有遗漏的功能？

---

**下一步：** 用户审查本设计文档 → 审查通过后进入 `writing-plans` 阶段
