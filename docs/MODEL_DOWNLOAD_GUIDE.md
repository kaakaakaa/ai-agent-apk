# 本地模型文件下载指南

## 问题说明

当前应用支持本地 LLM 模型运行，但模型文件（Gemma 2B、Qwen 0.5B）由于体积较大（几百 MB 到几 GB），**未包含在 APK 安装包中**。

## 解决方案

### 方案一：使用云端模型（推荐）

这是最简单的方式，只需要获取对应厂商的 API Key 即可使用，无需下载任何模型文件。

**支持的云端厂商：**
- 阿里云百炼
- DeepSeek
- Kimi
- Azure OpenAI

**配置步骤：**
1. 打开应用 -> 设置
2. 选择大模型厂商
3. 输入对应的 API Key
4. 点击"保存 API Key"
5. 选择"优先使用云端模型"

### 方案二：下载本地模型文件

如果你确实需要在本地运行模型，请按以下步骤操作：

#### 1. 使用 PowerShell 脚本下载（推荐）

在项目根目录执行：

```powershell
cd E:\破解\apk
.\scripts\download-models.ps1
```

脚本会提供以下选项：
- [1] 下载 Gemma 2B (约 1.2GB)
- [2] 下载 Qwen 0.5B (约 300MB)
- [3] 两者都下载

#### 2. 手动下载

**Gemma 2B 模型：**
- 来源：Hugging Face
- URL: https://huggingface.co/google/gemma-2b-it
- 文件名：`gemma-2b-int4.bin`
- 目标位置：`E:\破解\apk\app\src\main\assets\llm\models\gemma-2b-int4.bin`

**Qwen 0.5B 模型：**
- 来源：Hugging Face
- URL: https://huggingface.co/Qwen/Qwen-0.5B-Chat
- 文件名：`qwen-0.5b-int4.bin`
- 目标位置：`E:\破解\apk\app\src\main\assets\llm\models\qwen-0.5b-int4.bin`

#### 3. 国内下载问题

由于 Hugging Face 在国内访问受限，你可以：

1. **使用镜像站：**
   - https://hf-mirror.com/
   - 将 URL 中的 `huggingface.co` 替换为 `hf-mirror.com`

2. **使用代理：**
   ```powershell
   $env:HTTPS_PROXY="http://proxy-server:port"
   .\scripts\download-models.ps1
   ```

3. **从其他来源下载：**
   - ModelScope（魔搭）：https://modelscope.cn/
   - 智谱 AI：https://open.bigmodel.cn/

#### 4. 重新编译 APK

下载完成后，重新编译 APK：

```bash
cd E:\破解\apk
gradlew.bat assembleDebug
```

然后在手机上安装新编译的 APK。

## 设备要求

| 模型 | 最低 RAM | 推荐 RAM | 存储空间 |
|------|----------|----------|----------|
| Qwen 0.5B | 3GB | 4GB+ | 1GB |
| Gemma 2B | 6GB | 8GB+ | 2GB |

## 验证安装

编译后，你可以通过以下方式验证模型文件是否正确打包：

1. 解压 APK 文件
2. 检查 `assets/llm/models/` 目录下是否有模型文件
3. 或者在应用内点击"加载本地模型"，查看是否成功

## 常见问题

**Q: 为什么本地模型加载失败？**
A: 检查模型文件是否存在于 `assets/llm/models/` 目录中。

**Q: 云端模型和本地模型有什么区别？**
A:
- 云端模型：需要 API Key，响应快，无需下载大文件
- 本地模型：无需网络，完全离线，但需要下载几百 MB 的模型文件

**Q: 我应该选择哪种方式？**
A:
- 普通用户：建议使用云端模型（阿里云百炼、DeepSeek、Kimi）
- 开发者/高级用户：可以下载本地模型体验离线运行
