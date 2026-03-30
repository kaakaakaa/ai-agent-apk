# AI Agent APK

> 通过 AI 大模型智能控制 Android 手机

[English](README_EN.md) | **简体中文**

---

## 📱 简介

AI Agent APK 是一个基于 AI Agent 的 Android 应用，可以通过自然语言指令控制手机操作。

**核心功能：**
- ✅ 语音/文字控制：打开微信 、 返回 、 打开设置
- ✅ 云端模型支持：阿里云百炼、DeepSeek、Kimi 等
- ✅ 本地模型运行：Gemma 2B、Qwen 0.5B（离线可用）
- ✅ 聊天交互界面：简单易用
- ✅ 稳定版本：已修复闪退和 API Key 保存问题

---

## 🚀 快速开始

### 方式一：云端模型（推荐）

1. **安装 APK**
   `ash
   # 编译 Debug 版本
   ./gradlew assembleDebug
   
   # 安装到手机
   adb install app/build/outputs/apk/debug/app-debug.apk
   `

2. **配置 API Key**
   - 打开应用（主界面是聊天窗口）
   - 点击右上角设置图标
   - 选择大模型厂商（阿里云百炼、DeepSeek、Kimi 等）
   - 输入对应 API Key
   - 点击"保存 API Key"

3. **开始使用**
   - 返回主界面
   - 在聊天框输入指令，例如：
     - "打开微信"
     - "返回"
     - "打开设置"

**国内可用厂商：**

| 厂商 | 官网 | 文档 |
|------|------|------|
| 阿里云百炼 | https://bailian.console.aliyun.com/ | [文档](https://help.aliyun.com/product/42154.html) |
| DeepSeek | https://www.deepseek.com/ | [文档](https://platform.deepseek.com/api-docs/) |
| Kimi | https://kimi.moonshot.cn/ | [文档](https://platform.moonshot.cn/docs/) |

### 方式二：本地模型（离线）

下载本地模型文件后可离线运行：

`powershell
cd scripts
.\download-models.ps1
`

**支持模型：**
- Gemma 2B (INT4)
- Qwen 0.5B (INT4)

---

## 📦 项目结构

`
ai-agent-apk/
├── app/                          # Android 应用源码
│   ├── src/main/
│   │   ├── java/com/agent/apk/  # Kotlin 源码
│   │   │   ├── agent/           # AI Agent 核心逻辑
│   │   │   ├── ui/              # 界面组件
│   │   │   ├── action/          # 动作执行
│   │   │   ├── perception/      # 屏幕感知
│   │   │   ├── infra/           # 基础设施
│   │   │   └── model/           # 数据模型
│   │   ├── res/                 # 资源文件
│   │   └── assets/llm/          # 本地模型文件
│   └── build.gradle.kts         # 应用构建配置
├── docs/                         # 文档
│   ├── USAGE_CN.md              # 使用指南
│   ├── API_CONFIG_GUIDE.md      # API 配置指南
│   └── MODEL_DOWNLOAD_GUIDE.md  # 模型下载指南
├── scripts/                      # 脚本工具
│   ├── download-models.ps1      # 模型下载脚本
│   └── test-agent.bat           # 测试脚本
├── gradle/                       # Gradle 配置
├── build.gradle.kts              # 项目构建配置
├── settings.gradle.kts           # Gradle 设置
├── gradle.properties             # Gradle 属性
├── gradlew / gradlew.bat         # Gradle 包装器
├── README.md                     # 项目说明
└── LICENSE                       # MIT 许可证
`

---

## 🛠️ 编译与运行

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 11+
- Android SDK 31+

### 编译步骤

`ash
# 1. 克隆项目
git clone https://github.com/kaakaakaa/ai-agent-apk.git

# 2. 进入项目目录
cd ai-agent-apk

# 3. 编译 Debug 版本
./gradlew assembleDebug

# 4. 编译 Release 版本
./gradlew assembleRelease
`

**编译产物位置：**
- Debug: pp/build/outputs/apk/debug/app-debug.apk
- Release: pp/build/outputs/apk/release/app-release.apk

---

## 🏗️ 架构设计

`
┌─────────────────────────────────────────┐
│           UI Layer (界面层)              │
│  ┌─────────────┐  ┌─────────────────┐  │
│  │ MainActivity│  │ AgentOverlay    │  │
│  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│        Agent Layer (Agent 层)            │
│  ┌─────────────┐  ┌─────────────────┐  │
│  │ AgentLoop   │  │ TaskRouter      │  │
│  └─────────────┘  └─────────────────┘  │
│  ┌─────────────┐  ┌─────────────────┐  │
│  │ CloudAgent  │  │ LocalAgent      │  │
│  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│      Perception Layer (感知层)           │
│  ┌─────────────┐  ┌─────────────────┐  │
│  │ Accessibility│  │ ScreenAnalyzer  │  │
│  │ Scanner     │  │                 │  │
│  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│        Action Layer (执行层)             │
│  ┌─────────────┐  ┌─────────────────┐  │
│  │ Gesture     │  │ Accessibility   │  │
│  │ Performer   │  │ ActionExecutor  │  │
│  └─────────────┘  └─────────────────┘  │
└─────────────────────────────────────────┘
`

---

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

- **GitHub Issues**: 提问和讨论
- **项目仓库**: https://github.com/kaakaakaa/ai-agent-apk

---

**⚠️ 免责声明**：本项目仅供学习研究使用，请勿用于非法用途。
