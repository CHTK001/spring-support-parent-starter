/**
 * 首屏加载动画的可选配置（在 loader.js 之前加载）。
 *
 * <p>只声明「有哪些动画」与各自的展示名；**动画 HTML 与样式都不在这里** ——
 * 两者唯一来源是 {@code loader.js}（与 {@code @repo/ui/re-loader} 的 animations.ts 逐字一致）。</p>
 *
 * <p>为什么把 HTML 挪走：这里曾经各写一份 waveLayer，结果叠加了两次同样的 bug ——
 * 修了 animations.ts 和 loader.js，首屏仍显示旧的错误结构，因为真正的 DOM 由这份生成。
 * 一份结构化在水面路径这种"改一处忘另一处就画错"的地方是必须的，不是洁癖。</p>
 *
 * <p>删掉本文件也能正常启动：loader.js 自带完全相同的兜底定义。</p>
 */
(function () {
  "use strict";

  if (window.__SYS_LOADER_CONFIG__) {
    return;
  }

  window.__SYS_LOADER_CONFIG__ = {
    baseStyleText:
      "html,body{margin:0;padding:0;width:100%;height:100%}" +
      "#app{width:100%;height:100%;margin:0;padding:0}" +
      "#app-loader{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;width:100%;height:100%;box-sizing:border-box;padding:32px;background:radial-gradient(circle at top,rgba(64,110,235,.08),transparent 44%),#fff;z-index:9999}" +
      "html.dark #app-loader{background:radial-gradient(circle at top,rgba(64,110,235,.14),transparent 44%),#111827}",
    // 只列键与文案：HTML 由 loader.js 按 key 取 ANIMATION_HTML，避免第二份水面路径
    definitions: [
      {
        key: "wave",
        name: "海浪",
        description: "水位缓慢上涨的潮汐，深蓝向浅蓝过渡，最接近真实海面",
      },
      { key: "dots", name: "三个圆点", description: "经典三点跳动，最通用、最低调" },
      { key: "rings", name: "科技圆圈", description: "双环反向旋转 + 中心脉冲，偏科技感" },
      { key: "dino", name: "像素恐龙", description: "像素恐龙跨栏，适合想让人轻松的等待" },
      { key: "none", name: "无动画", description: "只保留纯色底，不显示任何动效" },
    ],
  };
})();
