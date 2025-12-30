#!/usr/bin/env pwsh
# 跨平台编译脚本 - 编译 Windows, Linux, macOS 动态库

$ErrorActionPreference = "Stop"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Rust HTTP Server - 跨平台编译" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

$ProjectRoot = $PSScriptRoot
$ResourceDir = Join-Path (Split-Path $ProjectRoot -Parent) "src\main\resources\rust-server"

# 确保资源目录存在
New-Item -ItemType Directory -Force -Path $ResourceDir | Out-Null

# 1. 编译 Windows (本地)
Write-Host "`n[1/3] 编译 Windows..." -ForegroundColor Green
$env:LIB = 'C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0\um\x64;C:\Program Files (x86)\Windows Kits\10\Lib\10.0.19041.0\ucrt\x64;C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Tools\MSVC\14.29.30133\lib\x64'
cargo build --release
if ($LASTEXITCODE -ne 0) {
    Write-Host "Windows 编译失败" -ForegroundColor Red
    exit 1
}
Copy-Item "target\release\rust_http_server.dll" "$ResourceDir\rust_http_server.dll" -Force
Write-Host "✓ Windows DLL 已复制到: $ResourceDir\rust_http_server.dll" -ForegroundColor Green

# 2. 编译 Linux (使用 Docker)
Write-Host "`n[2/3] 编译 Linux..." -ForegroundColor Green
$dockerCmd = 'cargo build --release && cp target/release/librust_http_server.so /workspace/librust_http_server.so'
docker run --rm `
    -v "${ProjectRoot}:/workspace" `
    -w /workspace `
    rust:latest `
    bash -c $dockerCmd

if ($LASTEXITCODE -ne 0) {
    Write-Host "Linux 编译失败" -ForegroundColor Red
    exit 1
}
Copy-Item "librust_http_server.so" "$ResourceDir\librust_http_server.so" -Force
Remove-Item "librust_http_server.so" -Force
Write-Host "✓ Linux SO 已复制到: $ResourceDir\librust_http_server.so" -ForegroundColor Green

# 3. 编译 macOS (使用 cross-rs)
Write-Host "`n[3/3] 编译 macOS..." -ForegroundColor Green

# 检查是否安装 cross
if (-not (Get-Command cross -ErrorAction SilentlyContinue)) {
    Write-Host "正在安装 cross 工具..." -ForegroundColor Yellow
    cargo install cross --git https://github.com/cross-rs/cross
}

# 使用 cross 编译 macOS (注意: 需要 macOS SDK, 可能失败)
try {
    cross build --release --target x86_64-apple-darwin
    if ($LASTEXITCODE -eq 0) {
        Copy-Item "target\x86_64-apple-darwin\release\librust_http_server.dylib" "$ResourceDir\librust_http_server.dylib" -Force
        Write-Host "✓ macOS DYLIB 已复制到: $ResourceDir\librust_http_server.dylib" -ForegroundColor Green
    } else {
        throw "macOS 编译失败"
    }
} catch {
    Write-Host "⚠ macOS 编译跳过（需要在 macOS 系统上编译）" -ForegroundColor Yellow
    Write-Host "  请在 macOS 上运行: cargo build --release" -ForegroundColor Yellow
}

Write-Host "`n======================================" -ForegroundColor Cyan
Write-Host "编译完成！" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "输出目录: $ResourceDir" -ForegroundColor White
Get-ChildItem $ResourceDir | Format-Table Name, Length -AutoSize
