@echo off
chcp 65001 >nul
cd /d "E:\破解\apk"
echo 开始监控应用日志...
echo 按 Ctrl+C 停止
echo.
"D:\Escrcpy\resources\extra\win\scrcpy\adb.exe" logcat -s "MainActivity" "ReActEngine" "CloudAgent" "AccessibilityScanner" "AgentService"
