@echo off
chcp 65001 >nul
echo 测试 AI Agent 应用
echo.
echo 请在手机上操作：
echo 1. 确保已配置 API Key
echo 2. 点击"打开微信"按钮或发送消息
echo 3. 观察是否显示思考过程和动作
echo.
echo 按任意键开始查看日志...
pause >nul
"D:\Escrcpy\resources\extra\win\scrcpy\adb.exe" logcat -c
echo 开始监控日志（按 Ctrl+C 停止）：
echo.
"D:\Escrcpy\resources\extra\win\scrcpy\adb.exe" logcat -s "MainActivity:D" "AgentService:D" "ReActEngine:D" "CloudAgent:D" "AccessibilityScanner:D" "OpenAiCompatibleClient:D"
