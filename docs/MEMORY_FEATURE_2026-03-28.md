# 全局记忆功能实现报告 - 2026-03-28

## 实现内容

### 1. 持久化数据库层

#### 新建文件

**1.1 `ConversationHistoryDao.kt`**
- Room DAO 接口，定义数据库操作
- 支持按会话 ID 查询、插入、删除
- 支持获取最近对话、所有会话 ID
- 返回 Flow 类型支持响应式监听

**1.2 `ConversationHistoryDatabase.kt`**
- Room 数据库主类
- 单例模式，数据库名：`conversation_history_db`
- version 1，支持破坏性迁移

**1.3 `ConversationHistoryManager.kt`**
- 高级 API 管理器
- 单例模式，通过 `getInstance(context)` 获取
- 功能：
  - `startNewSession()` - 开始新会话
  - `saveUserMessage()` - 保存用户消息
  - `saveAssistantMessage()` - 保存 AI 回复（含思考过程）
  - `saveSystemMessage()` - 保存系统消息
  - `getCurrentSessionHistory()` - 获取当前会话历史
  - `getRecentConversations()` - 获取最近对话（用于恢复）
  - `deleteAllHistory()` - 清空所有历史
  - `watchSession()` - 监听会话变化

### 2. CloudAgent 集成

**修改文件：`CloudAgent.kt`**

#### 变更内容：

1. **添加 Context 依赖**
   ```kotlin
   class CloudAgent(
       private val context: Context,  // 新增
       private val llmClient: LlmClient,
       private val model: String,
       private val supportsVision: Boolean
   )
   ```

2. **添加 HistoryManager 实例**
   ```kotlin
   private val historyManager = ConversationHistoryManager.getInstance(context)
   ```

3. **初始化时加载历史**
   ```kotlin
   init {
       conversationHistory.add(ReActMessage(role = "system", content = buildSystemPrompt()))
       loadConversationHistory()  // 新增：加载之前的对话
   }
   ```

4. **保存用户输入**
   ```kotlin
   fun startTask(goal: String): ReActSession {
       // ...
       saveUserMessage(goal)  // 新增
       return session
   }
   ```

5. **保存 AI 回复**
   ```kotlin
   if (result.isComplete) {
       session.status = SessionStatus.COMPLETED
       saveAssistantMessage(  // 新增
           content = result.finalAnswer ?: "",
           thought = result.thought,
           action = result.action?.toString(),
           completed = true
       )
   }
   ```

### 3. AgentService 适配

**修改文件：`AgentService.kt`**

传递 Context 给 CloudAgent：
```kotlin
_cloudAgent = CloudAgent(
    context = context,  // 新增
    llmClient = llmClient,
    model = config.models.firstOrNull() ?: "default",
    supportsVision = config.supportsVision
)
```

### 4. UI 线程阻塞修复

**修改文件：`MainActivity.kt`**

#### 变更内容：

1. **添加后台任务 Scope**
   ```kotlin
   private val taskScope = CoroutineScope(
       SupervisorJob() + Dispatchers.Default
   )
   ```

2. **使用后台线程池执行任务**
   ```kotlin
   // 之前：lifecycleScope.launch { ... } 会阻塞 UI
   // 现在：taskScope.launch { ... } 在后台执行
   taskScope.launch {
       // 执行任务
   }
   ```

3. **UI 更新切换回主线程**
   ```kotlin
   withContext(Dispatchers.Main) {
       messages[thinkingId] = MessageItem(...)
       messageAdapter.notifyDataSetChanged()
   }
   ```

4. **添加必要 import**
   ```kotlin
   import kotlinx.coroutines.CoroutineScope
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.SupervisorJob
   import kotlinx.coroutines.withContext
   ```

## 功能特性

### 全局记忆

1. **持久化存储**：所有对话保存到 Room 数据库
2. **会话恢复**：应用重启后自动加载最近 10 条对话
3. **会话管理**：每次启动创建新会话 ID，支持多会话
4. **完整记录**：保存用户消息、AI 回复、思考过程、执行动作

### UI 流式更新

1. **异步执行**：任务在后台线程池执行，不阻塞 UI
2. **实时反馈**：通过 StateFlow 监听 ReAct 引擎状态
3. **即时显示**：发送后立即显示"正在初始化..."，然后实时更新思考过程和动作

## 数据流程

```
用户发送消息
    ↓
1. 保存到数据库 (ConversationHistoryManager)
    ↓
2. 发送到 CloudAgent
    ↓
3. CloudAgent 加载历史 (最近 10 条)
    ↓
4. LLM 生成回复
    ↓
5. 保存到数据库 (含思考过程、动作)
    ↓
6. 下次启动时自动恢复历史
```

## 数据库结构

```sql
CREATE TABLE conversation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sessionId TEXT NOT NULL,
    role TEXT NOT NULL,       -- "user", "assistant", "system"
    content TEXT NOT NULL,
    thought TEXT,             -- AI 思考过程
    action TEXT,              -- 执行的动作
    timestamp INTEGER NOT NULL,
    taskCompleted INTEGER NOT NULL
);

CREATE INDEX idx_session_id ON conversation_history(sessionId);
```

## 修改的文件列表

1. `app/src/main/java/com/agent/apk/infra/ConversationHistoryDao.kt` (新建)
2. `app/src/main/java/com/agent/apk/infra/ConversationHistoryDatabase.kt` (新建)
3. `app/src/main/java/com/agent/apk/infra/ConversationHistoryManager.kt` (新建)
4. `app/src/main/java/com/agent/apk/agent/cloud/CloudAgent.kt` (修改)
5. `app/src/main/java/com/agent/apk/agent/AgentService.kt` (修改)
6. `app/src/main/java/com/agent/apk/ui/MainActivity.kt` (修改)

## 验证状态

- [ ] 代码编译成功
- [ ] APK 安装成功
- [ ] 应用启动正常
- [ ] 发送消息后 UI 不再卡住
- [ ] 实时显示思考过程和动作
- [ ] 重启应用后恢复之前的对话历史
- [ ] 设置中可以清空历史记录（待实现）

## 后续优化建议

1. **设置界面**：添加"清空历史"按钮
2. **历史查看**：添加查看完整历史记录的界面
3. **会话管理**：支持手动创建/删除会话
4. **搜索功能**：支持搜索历史对话内容
5. **导出功能**：支持导出对话历史为 JSON/文本
