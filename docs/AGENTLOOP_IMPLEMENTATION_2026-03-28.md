# AgentLoop LLM 调用实现报告

**日期**: 2026-03-28
**状态**: 已完成

## 实现概述

完成了 AgentLoop 核心循环的 LLM 调用和工具执行功能，使 Android Agent 项目具备了完整的对话和操作能力。

## 实现内容

### 1. LLM 调用功能 (callLLM 方法)

**文件**: `app/src/main/java/com/agent/apk/agent/AgentLoop.kt`

**核心功能**:
- 流式调用 LLM 客户端
- 解析 Thought/Action/Final Answer 三种响应类型
- 自动判断聊天响应 vs 操作响应
- Token 预算管理（65,536 tokens 上下文窗口）

**实现细节**:
```kotlin
private suspend fun callLLM(
    messages: List<Map<String, String>>,
    onProgress: suspend (content: String, isToolHint: Boolean) -> Unit
): Triple<String?, String?, String?>
```

**响应解析**:
- `extractThought()`: 提取 "Thought:" 后的思考内容
- `extractAction()`: 提取 "Action:" 后的动作字符串
- `extractFinalAnswer()`: 提取 "Final Answer:" 后的最终回答
- `isChatResponse()`: 判断是否为纯聊天响应（不需要执行动作）

### 2. LLM 客户端管理

**初始化逻辑**:
```kotlin
private suspend fun getOrCreateLlmClient(): LlmClient?
```

**支持的厂商**:
- 阿里云百炼 (dashscope)
- DeepSeek
- Kimi (月之暗面)
- Azure OpenAI

**API Key 管理**:
- 从 `ApiKeyManager` 按优先级获取
- 使用 EncryptedSharedPreferences 加密存储
- 支持多厂商配置

### 3. 工具执行功能 (executeAction 方法)

**核心功能**:
- 解析动作字符串（如 `click(target="微信")`）
- 调用 ToolRegistry 执行工具
- 返回执行结果

**解析逻辑**:
```kotlin
private fun parseActionString(action: String): Pair<String, Map<String, Any?>>?
```

**支持的格式**:
- 具名参数：`click(target="微信")`
- 位置参数：`click(微信)`

### 4. 工具注册表初始化

**初始化的工具**:
| 工具名 | 功能 | 别名 |
|--------|------|------|
| ClickTool | 点击屏幕元素 | tap, press |
| TypeTool | 输入文本 | - |
| SwipeTool | 滑动手势 | - |
| OpenAppTool | 打开应用 | - |
| NavigateTool | 系统导航 | - |

**初始化时机**:
- AgentLoop 构造函数中的 `init` 块
- 依赖 AccessibilityScanner 实例

### 5. 消息构建

**buildMessages 方法构建的消息结构**:
1. 系统提示词（包含工具定义）
2. 长期记忆（从 MemoryStore 加载）
3. 历史对话（从 Session 加载）
4. 当前屏幕上下文（从 AccessibilityScanner 获取）
5. 当前用户消息

## 工作流程

```
用户输入
    ↓
1. 获取/创建会话 → Token 预检查 → 需要时剪枝旧消息
    ↓
2. 构建上下文（历史 + 记忆 + 屏幕内容）
    ↓
3. 调用 LLM（流式输出）
    ├─→ 流式回调 onProgress
    ├─→ 解析 Thought/Action/FinalAnswer
    ↓
4. 执行动作（如果有）
    ├─→ 解析动作字符串
    ├─→ 调用 ToolRegistry.execute()
    ├─→ 返回观察结果
    ↓
5. 保存会话到数据库
    ↓
6. 后台记忆整合（保存到 MEMORY.md 和 HISTORY.md）
    ↓
返回最终回答
```

## 优化特性

### Token 预算管理
```kotlin
CONTEXT_WINDOW_TOKENS = 65_536   // 总窗口
MAX_COMPLETION_TOKENS = 4_096    // 最大生成
SAFETY_BUFFER = 1_024            // 安全余量

// 当超过预算时自动剪枝
if (sessionManager.needsConsolidation(session)) {
    session.pruneOldMessages(budget / 2)
}
```

### 流式输出支持
- 实时回调每个 token
- 支持 Thought/Action/FinalAnswer 三种类型
- 通过 `onStreamToken` 回调通知 UI

### 失败处理
- LLM 调用失败时返回错误消息
- 工具执行失败时返回失败原因
- 异常捕获并记录日志

## 使用示例

```kotlin
// 1. 获取 AgentLoop 实例
val agentLoop = AgentLoop.getInstance(context)

// 2. 处理用户消息
val result = agentLoop.processMessage(
    sessionKey = "user_${userId}",
    userMessage = "打开微信",
    onProgress = { content, isToolHint ->
        if (isToolHint) {
            // 显示工具执行提示
            showActionHint(content)
        } else {
            // 显示思考内容
            updateThought(content)
        }
    }
)

// 3. 流式输出回调
agentLoop.onStreamToken = { token, type ->
    when (type) {
        AgentLoop.StreamType.THOUGHT -> showThought(token)
        AgentLoop.StreamType.ACTION -> showAction(token)
        AgentLoop.StreamType.FINAL_ANSWER -> showAnswer(token)
    }
}
```

## 编译状态

```
BUILD SUCCESSFUL in 4s
37 actionable tasks: 6 executed, 31 up-to-date
```

## 下一步计划

### 已完成
- [x] Agent Loop 的 LLM 调用实现
- [x] 工具注册表初始化和执行
- [x] 流式输出支持
- [x] Token 预算管理
- [x] 记忆整合集成

### 待完成
- [ ] 流式输出 UI 展示完善
- [ ] 更多工具（截图、滚动、返回等）
- [ ] 子代理 (Subagent) 支持
- [ ] 命令路由系统

## 项目统计更新

| 指标 | 数值 | 变化 |
|------|------|------|
| Kotlin 文件总数 | 46 | - |
| 新增文件 | 5 | +1 (AgentLoop.kt) |
| 代码行数 (新增) | ~1100 行 | +300 行 |
| 工具数量 | 5 | - |
| 支持的 LLM 厂商 | 4 | - |

## 参考文档

- [ARCHITECTURE_OPTIMIZATION_2026-03-28.md](ARCHITECTURE_OPTIMIZATION_2026-03-28.md)
- [LlmClient.kt](../app/src/main/java/com/agent/apk/agent/cloud/LlmClient.kt)
- [ToolRegistry.kt](../app/src/main/java/com/agent/apk/agent/tools/ToolRegistry.kt)
- [ActionTools.kt](../app/src/main/java/com/agent/apk/agent/tools/ActionTools.kt)
