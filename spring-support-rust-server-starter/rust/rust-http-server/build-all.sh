#!/usr/bin/env bash
# 跨平台编译脚本 - 编译 Windows, Linux, macOS 动态库

set -e

echo "======================================"
echo "Rust HTTP Server - 跨平台编译"
echo "======================================"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESOURCE_DIR="$(dirname "$PROJECT_ROOT")/src/main/resources/rust-server"

# 确保资源目录存在
mkdir -p "$RESOURCE_DIR"

# 检测当前平台
OS="$(uname -s)"
case "$OS" in
    Linux*)     PLATFORM=linux;;
    Darwin*)    PLATFORM=macos;;
    *)          PLATFORM=unknown;;
esac

echo ""
echo "当前平台: $PLATFORM"
echo ""

# 1. 编译当前平台
if [ "$PLATFORM" = "linux" ]; then
    echo "[1/3] 编译 Linux (x86_64-unknown-linux-gnu)..."
    cargo build --release
    cp target/release/librust_http_server.so "$RESOURCE_DIR/librust_http_server.so"
    echo "✓ Linux SO 已复制到: $RESOURCE_DIR/librust_http_server.so"
    
elif [ "$PLATFORM" = "macos" ]; then
    echo "[1/3] 编译 macOS (x86_64-apple-darwin)..."
    cargo build --release
    cp target/release/librust_http_server.dylib "$RESOURCE_DIR/librust_http_server.dylib"
    echo "✓ macOS DYLIB 已复制到: $RESOURCE_DIR/librust_http_server.dylib"
    
    # 尝试编译 ARM64 版本 (Apple Silicon)
    if rustup target list --installed | grep -q "aarch64-apple-darwin"; then
        echo ""
        echo "编译 macOS ARM64 (aarch64-apple-darwin)..."
        cargo build --release --target aarch64-apple-darwin
        cp target/aarch64-apple-darwin/release/librust_http_server.dylib "$RESOURCE_DIR/librust_http_server_arm64.dylib"
        echo "✓ macOS ARM64 DYLIB 已复制"
    fi
else
    echo "⚠ 未知平台: $OS"
    exit 1
fi

# 2. 使用 Docker 编译 Linux (如果不在 Linux 上)
if [ "$PLATFORM" != "linux" ]; then
    echo ""
    echo "[2/3] 编译 Linux (使用 Docker)..."
    if command -v docker &> /dev/null; then
        docker run --rm \
            -v "$PROJECT_ROOT:/workspace" \
            -w /workspace \
            rust:latest \
            bash -c "cargo build --release && cp target/release/librust_http_server.so /workspace/"
        
        cp librust_http_server.so "$RESOURCE_DIR/librust_http_server.so"
        rm librust_http_server.so
        echo "✓ Linux SO 已复制"
    else
        echo "⚠ Docker 未安装，跳过 Linux 编译"
    fi
fi

# 3. 使用 cross 编译 Windows (仅在 Linux 上)
if [ "$PLATFORM" = "linux" ]; then
    echo ""
    echo "[3/3] 编译 Windows (使用 cross)..."
    
    if ! command -v cross &> /dev/null; then
        echo "正在安装 cross 工具..."
        cargo install cross --git https://github.com/cross-rs/cross
    fi
    
    cross build --release --target x86_64-pc-windows-gnu || {
        echo "⚠ Windows 编译失败，需要在 Windows 上编译"
    }
    
    if [ -f "target/x86_64-pc-windows-gnu/release/rust_http_server.dll" ]; then
        cp target/x86_64-pc-windows-gnu/release/rust_http_server.dll "$RESOURCE_DIR/rust_http_server.dll"
        echo "✓ Windows DLL 已复制"
    fi
fi

echo ""
echo "======================================"
echo "编译完成！"
echo "======================================"
echo "输出目录: $RESOURCE_DIR"
ls -lh "$RESOURCE_DIR"
