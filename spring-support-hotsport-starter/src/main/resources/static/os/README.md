# OS 平台图标（图片版） — 共享 public 目录

这里集中存放各前端应用共享的静态资源（Vite `publicDir` 指向 monorepo 根 `vue-support-parent-starter/public/`，所有 app 共用）。

主机资产列表的名称列会优先显示这里的平台 logo，图片缺失时自动回退为内置 SVG。

## 约定文件名（本 `public/os/` 目录）

| 文件名 | 平台 |
|---|---|
| `os-linux.png` | Linux |
| `os-windows.png` | Windows |
| `os-macos.png` | macOS |
| `os-android.png` | Android（可选） |

- 建议 32×32 透明底 PNG
- 引用方式：`AssetsView.vue` 中 `osImgSrc()` 返回 `/<base>/os/os-<平台>.png`，dev 与 build 均命中本目录
- `<img>` 加载失败时 `@error` 隐藏图片、显示 SVG 兜底