# AI Agent APK

Control your Android phone with AI.

## Features

- Text/Voice control: Open apps, go back, open settings
- Cloud AI: Aliyun Bailian, DeepSeek, Kimi
- Local AI: Gemma 2B, Qwen 0.5B (offline)
- Chat interface
- Stable version

## Quick Start

### Step 1: Build APK

```bash
# Clone repository
git clone https://github.com/kaakaakaa/ai-agent-apk.git
cd ai-agent-apk

# Build debug APK
./gradlew assembleDebug

# APK location
app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Install APK

**Method A: USB Cable**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Method B: Transfer to Phone**
1. Copy APK to your phone
2. Open file manager
3. Tap APK to install
4. Enable "Install from unknown sources" if needed

### Step 3: Configure AI

1. Open AI Agent APK app
2. Tap Settings icon (top right)
3. Choose AI provider:
   - Aliyun Bailian
   - DeepSeek
   - Kimi
4. Enter your API Key
5. Tap "Save API Key"

### Step 4: Start Using

1. Return to main chat screen
2. Type or speak commands:
   - "Open WeChat"
   - "Go back"
   - "Open settings"
   - "Turn on WiFi"

## Local AI (Offline)

Download local models:
```powershell
cd scripts
.\download-models.ps1
```

Supported models:
- Gemma 2B (INT4)
- Qwen 0.5B (INT4)

## Project Structure

- app/ - Android app source code
- docs/ - User documentation
- scripts/ - Utility scripts

## Build Requirements

- Android Studio Arctic Fox or higher
- JDK 11+
- Android SDK 31+

## License

MIT License
