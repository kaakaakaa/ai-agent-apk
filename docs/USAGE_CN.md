# AI Agent APK - 使用说明

## 快速开始

### 1. 安装应用
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. 开启无障碍权限
1. 打开 设置 → 无障碍 → 已下载的应用
2. 找到 **AI Agent** 并开启权限

### 3. 配置 API Key
1. 打开应用
2. 点击底部 **⚙️ 设置 - 配置 API Key 和模型** 按钮
3. 选择大模型厂商：
   - **阿里云百炼**（推荐）
   - **DeepSeek**
   - **Kimi**
   - **Azure OpenAI**
4. 输入 API Key
5. 点击 **保存 API Key**

### 4. 开始使用
在主界面：
- **输入框**：输入你想让 AI 帮你做的事
- **发送按钮**：发送指令
- **快捷按钮**：快速执行常用操作

#### 示例指令：
- "打开微信"
- "返回"
- "打开设置"
- "帮我发一条微信消息"

## 功能说明

### 主界面
- **聊天窗口**：与 AI 助手对话
- **状态栏**：显示当前 Agent 状态（就绪/未就绪）
- **快捷指令**：一键执行常用操作
- **设置按钮**：配置 API Key 和模型

### 设置界面
- **API 配置**：选择厂商并保存 API Key
- **模型选择**：
  - 自动选择（根据设备能力）
  - 优先使用云端模型
  - 仅使用本地模型
- **本地模型**：加载本地 LLM 模型（需要先下载模型文件）
- **语音配置**：启用/禁用语音输入输出

## 国内可用厂商

| 厂商 | 官网 | API 申请 |
|------|------|----------|
| 阿里云百炼 | https://bailian.console.aliyun.com/ | 控制台 → API Key 管理 |
| DeepSeek | https://www.deepseek.com/ | 个人中心 → API Keys |
| Kimi | https://kimi.moonshot.cn/ | 控制台 → API Key |

## 常见问题

### Q: 应用闪退？
A: 检查是否授予了无障碍权限。

### Q: 提示"未就绪"？
A: 需要先在设置中配置 API Key。

### Q: 本地模型怎么用？
A: 本地模型需要单独下载模型文件（几百 MB），建议使用云端模型。

### Q: 悬浮球怎么开启？
A: 需要授予悬浮窗权限，然后在设置中启动悬浮球服务。

## 技术细节

### 项目结构
```
app/src/main/java/com/agent/apk/
├── agent/          # Agent 核心逻辑
├── infra/          # 基础设施（API、网络）
├── model/          # 数据模型
├── perception/     # 感知层（无障碍服务）
├── service/        # 后台服务
├── ui/             # 界面 Activity
└── voice/          # 语音服务
```

### 构建命令
```bash
./gradlew assembleDebug
```

### 查看日志
```bash
adb logcat -s "MainActivity" "SettingsActivity" "AgentService"
```
