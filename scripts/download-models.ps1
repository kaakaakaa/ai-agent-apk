# 本地模型下载脚本
# 用于下载 Gemma 2B 和 Qwen 0.5B 模型文件到 assets 目录

$modelsDir = "E:\破解\apk\app\src\main\assets\llm\models"
$downloadDir = "E:\破解\apk\temp\models"

# 创建目录
if (-not (Test-Path $modelsDir)) {
    New-Item -ItemType Directory -Force -Path $modelsDir | Out-Null
}
if (-not (Test-Path $downloadDir)) {
    New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
}

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "     本地 LLM 模型下载工具" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "可用模型:" -ForegroundColor Yellow
Write-Host "  [1] Gemma 2B (约 1.2GB) - 适合 6GB+ RAM 设备" -ForegroundColor White
Write-Host "  [2] Qwen 0.5B (约 300MB) - 适合 3GB+ RAM 设备" -ForegroundColor White
Write-Host "  [3] 两者都下载" -ForegroundColor White
Write-Host "  [0] 退出" -ForegroundColor White
Write-Host ""
Write-Host "注意：国内用户可能需要代理才能下载" -ForegroundColor Red
Write-Host ""
Write-Host "请选择 (输入数字): " -NoNewline -ForegroundColor Yellow

$choice = Read-Host

function Download-Model {
    param(
        [string]$url,
        [string]$outputFile,
        [string]$modelName
    )

    Write-Host ""
    Write-Host "正在下载 $modelName ..." -ForegroundColor Cyan

    try {
        # 使用 Invoke-WebRequest 下载（带进度条）
        Invoke-WebRequest -Uri $url -OutFile $outputFile -UseBasicParsing

        if (Test-Path $outputFile) {
            $size = (Get-Item $outputFile).Length / 1MB
            Write-Host "下载完成！文件大小：$([math]::Round($size, 2)) MB" -ForegroundColor Green

            # 复制到 assets 目录
            $destFile = Join-Path $modelsDir (Split-Path $outputFile -Leaf)
            Copy-Item $outputFile $destFile -Force
            Write-Host "已复制到：$destFile" -ForegroundColor Green
            return $true
        }
    } catch {
        Write-Host "下载失败：$($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

switch ($choice) {
    "1" {
        # Gemma 2B 下载链接（Hugging Face）
        $url = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-int4.bin"
        $file = Join-Path $downloadDir "gemma-2b-int4.bin"
        Download-Model -url $url -outputFile $file -modelName "Gemma 2B"
    }
    "2" {
        # Qwen 0.5B 下载链接（Hugging Face）
        $url = "https://huggingface.co/Qwen/Qwen-0.5B-Chat/resolve/main/qwen-0.5b-int4.bin"
        $file = Join-Path $downloadDir "qwen-0.5b-int4.bin"
        Download-Model -url $url -outputFile $file -modelName "Qwen 0.5B"
    }
    "3" {
        $success = 0

        $url1 = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-int4.bin"
        $file1 = Join-Path $downloadDir "gemma-2b-int4.bin"
        if (Download-Model -url $url1 -outputFile $file1 -modelName "Gemma 2B") { $success++ }

        Start-Sleep -Seconds 2

        $url2 = "https://huggingface.co/Qwen/Qwen-0.5B-Chat/resolve/main/qwen-0.5b-int4.bin"
        $file2 = Join-Path $downloadDir "qwen-0.5b-int4.bin"
        if (Download-Model -url $url2 -outputFile $file2 -modelName "Qwen 0.5B") { $success++ }

        Write-Host ""
        Write-Host "完成！成功下载 $success/2 个模型" -ForegroundColor Green
    }
    default {
        Write-Host "已退出" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "下载完成后，请重新编译 APK" -ForegroundColor Cyan
Write-Host "命令：gradlew.bat assembleDebug" -ForegroundColor Gray
Write-Host ""
Read-Host "按回车键退出"
