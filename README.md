# AI Agent APK - Android 手机 AI 助手

> **🎯 项目用途**：通过 AI 大模型（阿里云百炼、DeepSeek、Kimi 等）智能控制 Android 手机  
> 支持云端 API 和本地模型，让手机操作更智能！

## 📱 项目简介

这是一个基于 AI Agent 的 Android 应用，可以通过自然语言指令控制手机操作：

- ✅ **语音/文字控制**：打开微信 、 返回 、 打开设置
- ✅ **云端模型支持**：阿里云百炼、DeepSeek、Kimi 等
- ✅ **本地模型运行**：Gemma 2B、Qwen 0.5B（离线可用）
- ✅ **智能界面**：聊天交互界面，简单易用
- ✅ **已修复闪退**：稳定版本，可正常使用

## 🚀 快速开始

### 方式一：云端模型（推荐）⭐

1. 安装 APK：pp/build/outputs/apk/debug/app-debug.apk
2. 打开应用（主界面是聊天窗口）
3. 点击右上角设置图标或通过悬浮球进入设置
4. 选择大模型厂商（阿里云百炼、DeepSeek、Kimi 等）
5. 输入对应 API Key
6. 点击保存 API Key
7. 返回主界面，在聊天框输入指令

**国内可用厂商：**
| 厂商 | 官网 | 文档 |
|------|------|------|
| 阿里云百炼 | https://bailian.console.aliyun.com/ | [文档](https://help.aliyun.com/product/42154.html) |
| DeepSeek | https://www.deepseek.com/ | [文档](https://platform.deepseek.com/api-docs/) |
| Kimi | https://kimi.moonshot.cn/ | [文档](https://platform.moonshot.cn/docs/) |

### 方式二：本地模型（离线）

`powershell
cd scripts
.\download-models.ps1
`

## 📦 项目结构

`
├── app/                    # 应用源码
│   ├── src/main/          # 主源码
│   │   ├── java/          # Java/Kotlin 代码
│   │   ├── res/           # 资源文件
│   │   └── assets/llm/    # 本地模型文件
│   └── build.gradle.kts   # 应用构建配置
├── docs/                   # 文档
├── scripts/                # 脚本工具
├── nanobot-main/           # 可选：Agent 框架
├── build.gradle.kts        # 项目构建配置
└── settings.gradle.kts     # Gradle 设置
`

## 🛠️ 编译与运行

### 环境要求
- Android Studio Arctic Fox 或更高版本
- JDK 11+
- Android SDK 31+

### 编译步骤
`ash
# 克隆项目
git clone https://github.com/kaakaakaa/ai-agent-apk.git

# 进入项目目录
cd ai-agent-apk

# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本
./gradlew assembleRelease
`

编译产物位置：pp/build/outputs/apk/

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📞 联系方式

- GitHub Issues: 提问和讨论
- 项目仓库：https://github.com/kaakaakaa/ai-agent-apk

---

**⚠️ 免责声明**：本项目仅供学习研究使用，请勿用于非法用途。
