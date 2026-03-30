# Android Agent 架构优化报告

**日期**: 2026-03-28
**参考架构**: nanobot (Ultra-Lightweight Personal AI Assistant)

## 优化概述

基于 nanobot 的设计理念，对 Android Agent 项目进行了架构优化，主要改进包括：

### 1. 工具注册机制 (Tool Registry)

**新增文件**: `app/src/main/java/com/agent/apk/agent/tools/ToolRegistry.kt`

**核心功能**:
- 统一工具发现和注册系统
- 支持工具别名映射
- 工具定义自动生成（用于系统提示词）
- 工具执行结果标准化

**使用示例**:
```kotlin
val registry = ToolRegistry.getInstance()
registry.register(ClickTool(scanner), aliases = arrayOf("tap", "press"))
registry.register(TypeTool(scanner))

// 执行工具
val result = registry.execute("click", mapOf("target" to "微信"))
```

**优势**:
- 工具管理集中化
- 易于扩展新工具
- 支持工具元数据查询

---

### 2. 工具实现 (Action Tools)

**新增文件**: `app/src/main/java/com/agent/apk/agent/tools/ActionTools.kt`

**已实现工具**:
| 工具名 | 功能 | 参数 |
|--------|------|------|
| `click` | 点击屏幕元素 | target, nodeId |
| `type` | 输入文本 | text, nodeId |
| `swipe` | 滑动手势 | fromX, fromY, toX, toY, durationMs |
| `openApp` | 打开应用 | packageName |
| `navigate` | 系统导航 | action (back/home/recent) |

**设计特点**:
- 继承统一的 `AgentTool` 基类
- 参数定义自描述
- 支持 nodeId 精确匹配和文本模糊匹配
- 降级处理机制

---

### 3. 会话管理器 (Session Manager)

**新增文件**: `app/src/main/java/com/agent/apk/agent/session/SessionManager.kt`

**核心功能**:
- 会话创建/获取/删除
- Token 预算管理
- 会话持久化（数据库存储）
- 会话历史加载

**Token 预算配置**:
```kotlin
contextWindowTokens = 65_536       // 上下文窗口
maxCompletionTokens = 4_096        // 最大生成 token
SAFETY_BUFFER = 1_024              // 安全余量
```

**会话类特性**:
- 消息历史管理
- 自动 prune 旧消息（当 token 超预算时）
- 系统提示词构建
- 上下文消息构建

---

### 4. 记忆系统增强 (Memory Store Enhancement)

**优化文件**: `app/src/main/java/com/agent/apk/infra/MemoryStore.kt`

**新增功能**:
1. **LLM 智能整合**: 支持使用 LLM 自动总结对话内容
2. **失败降级机制**: LLM 失败时自动转为简化模式
3. **外部总结支持**: 避免重复调用 LLM

**双层记忆结构**:
```
memory/
├── MEMORY.md    # 长期事实记忆（累加式更新）
└── HISTORY.md   # 可搜索的对话日志（时间线记录）
```

**整合流程**:
1. 检查是否有外部总结（来自上层调用）
2. 尝试使用 LLM 智能整合
3. LLM 失败时降级到简化模式
4. 连续失败 3 次后转为原始存档

---

### 5. Agent 核心循环 (Agent Loop)

**新增文件**: `app/src/main/java/com/agent/apk/agent/AgentLoop.kt`

**核心职责**:
```
1. 接收用户输入
   ↓
2. 获取/创建会话 → Token 预检查 → 需要时整合记忆
   ↓
3. 构建上下文 (历史 + 记忆 + 当前屏幕)
   ↓
4. 调用 LLM (流式输出)
   ↓
5. 执行工具调用
   ↓
6. 更新记忆系统 (异步后台)
```

**优化特性**:
- Token 预算管理（避免上下文超限）
- 流式输出支持
- 失败重试机制
- 记忆自动整合

---

## 架构对比

### 优化前
```
AgentService
├── CloudAgent
│   └── ReActEngine
├── LocalAgent
└── ActionExecutor
```

### 优化后
```
AgentService (服务管理器)
├── AgentLoop (核心循环)
│   ├── SessionManager (会话管理)
│   ├── MemoryStore (记忆系统)
│   └── ToolRegistry (工具注册)
│       ├── ClickTool
│       ├── TypeTool
│       ├── SwipeTool
│       └── OpenAppTool
├── CloudAgent (云端 Agent)
│   └── ReActEngine
└── LocalAgent (本地 Agent)
```

---

## 使用示例

### 完整流程示例
```kotlin
// 1. 初始化
val agentService = AgentService.getInstance(context)
agentService.initialize()

// 2. 获取 Agent Loop
val agentLoop = AgentLoop.getInstance(context)

// 3. 处理用户消息
lifecycleScope.launch {
    val result = agentLoop.processMessage(
        sessionKey = "user_${userId}",
        userMessage = "打开微信",
        onProgress = { content, isToolHint ->
            if (isToolHint) {
                showToolHint(content)
            } else {
                updateThought(content)
            }
        }
    )
    showResult(result)
}

// 4. 获取会话历史
val history = agentLoop.getSessionHistory("user_${userId}", limit = 50)

// 5. 清空会话
agentLoop.clearSession("user_${userId}")
```

---

## 性能优化

### Token 预算计算
```kotlin
// 简化估算：每 3 个字符约 1 个 token（中英文混合）
fun estimateMessageTokens(message: Map<String, Any?>): Int {
    val content = message["content"] as? String ?: ""
    return content.length / 3
}

// 会话总 token 估算
fun estimateSessionTokens(session: Session): Int {
    return session.messages.sumOf { estimateMessageTokens(it) }
}
```

### 记忆整合触发条件
```kotlin
fun needsConsolidation(session: Session): Boolean {
    val budget = contextWindowTokens - maxCompletionTokens - SAFETY_BUFFER
    val target = budget / 2  // 目标：预算的一半
    val estimated = estimateSessionTokens(session)
    return estimated > budget  // 超过预算时触发
}
```

---

## 下一步计划

### 短期 (1-2 周)
- [x] 完成 Agent Loop 的 LLM 调用实现
- [x] 实现工具注册的自动发现机制
- [ ] 添加更多工具（截图、滚动、返回等）
- [ ] 完善流式输出 UI 展示

### 中期 (2-4 周)
- [ ] 添加子代理 (Subagent) 支持
- [ ] 实现命令路由系统（类似 nanobot 的 CommandRouter）
- [ ] 添加定时任务支持（类似 nanobot 的 CronService）
- [ ] 实现心跳机制（定期主动任务）

### 长期 (1-3 月)
- [ ] 多模态支持（图片、语音输入）
- [ ] 长期记忆优化（向量数据库）
- [ ] 更好的推理能力（多步规划）
- [ ] 自学习能力（从反馈中改进）

---

## 参考文档

- [nanobot README](https://github.com/HKUDS/nanobot/blob/main/README.md)
- [nanobot Agent Loop](https://github.com/HKUDS/nanobot/blob/main/nanobot/agent/loop.py)
- [nanobot Memory System](https://github.com/HKUDS/nanobot/blob/main/nanobot/agent/memory.py)

---

## 项目统计

| 指标 | 数值 |
|------|------|
| Kotlin 文件总数 | 46 |
| 新增文件 | 5 |
| 优化文件 | 1 |
| 代码行数 (新增) | ~1100 行 |
| 工具数量 | 5 |
| 支持的动作类型 | 8 |
| 支持的 LLM 厂商 | 4 |

---

**备注**: 本次优化主要聚焦于架构层面，保持了与现有代码的兼容性。优化后的架构更易于扩展、维护和测试。
