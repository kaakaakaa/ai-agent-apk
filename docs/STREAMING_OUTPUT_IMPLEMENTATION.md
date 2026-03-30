# 流式输出实现文档

## 概述

实现了 LLM 流式输出功能，让用户能够实时看到 AI 的思考过程，而不是等待完整响应。

## 实现架构

```
用户输入
    ↓
ReActEngine (订阅流式更新)
    ↓
CloudAgent (流式 token 回调)
    ↓
LlmClient/BailianClient (SSE 流式请求)
    ↓
LLM API (OpenAI 兼容格式)
```

## 核心组件

### 1. LlmClient.kt - 流式请求接口

```kotlin
interface LlmClient {
    suspend fun streamChatCompletion(
        request: ChatRequest,
        onToken: (String) -> Unit
    ): String
}
```

**SSE 解析逻辑:**
- 请求参数添加 `"stream": true`
- 解析响应行，过滤 `data: ` 前缀
- 提取 `choices[0].delta.content` 并回调

### 2. CloudAgent.kt - 流式输出协调器

```kotlin
enum class StreamType {
    THOUGHT,      // 思考中
    ACTION,       // 动作执行中
    FINAL_ANSWER  // 最终回答
}

var onStreamToken: ((token: String, type: StreamType) -> Unit)? = null
```

### 3. ReActEngine.kt - 流式状态转发

```kotlin
enum class StreamUpdateType {
    THOUGHT_START,    // 开始思考
    THOUGHT_TOKEN,    // 思考中的 token
    ACTION_START,     // 开始执行动作
    ACTION_TOKEN,     // 动作执行中的 token
    ANSWER_TOKEN,     // 最终回答的 token
    COMPLETE          // 完成
}

var onStreamUpdate: ((text: String, type: StreamUpdateType) -> Unit)? = null
```

### 4. MainActivity.kt - UI 实时更新

```kotlin
reActEngine.onStreamUpdate = { text, type ->
    when (type) {
        THOUGHT_TOKEN -> {
            streamingThought.append(text)
            updateMessage(thought = streamingThought.toString())
        }
        ACTION_START -> { /* 显示动作 */ }
        ANSWER_TOKEN -> { /* 显示回答 */ }
    }
}
```

## 数据流

1. **用户发送消息**
2. **ReActEngine** 开始执行任务，订阅 `onStreamUpdate`
3. **CloudAgent** 调用 LLM，订阅 `onStreamToken`
4. **LlmClient** 发送 SSE 请求，每收到一个 token 就回调
5. **回调链向上传递**: LlmClient → CloudAgent → ReActEngine → MainActivity
6. **UI 实时更新** 思考/动作/回答内容

## 优化特性

- **线程安全**: 所有回调在 `Dispatchers.Main` 上执行
- **StringBuilder 缓冲**: 避免重复创建字符串
- **自动滚动**: 新内容到达时自动滚动到底部
- **状态管理**: 清晰区分思考、动作、回答三种状态

## 测试方法

1. 启动应用
2. 输入测试消息（如"你好"）
3. 观察思考区域是否逐字显示内容
4. 验证 UI 流畅度（无卡顿）

## 相关文件

- `app/src/main/java/com/agent/apk/agent/cloud/LlmClient.kt`
- `app/src/main/java/com/agent/apk/agent/cloud/CloudAgent.kt`
- `app/src/main/java/com/agent/apk/agent/cloud/ReActEngine.kt`
- `app/src/main/java/com/agent/apk/agent/cloud/BailianClient.kt`
- `app/src/main/java/com/agent/apk/ui/MainActivity.kt`

## 日期

2026-03-28
