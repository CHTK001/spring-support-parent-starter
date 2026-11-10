/**
 * 首屏加载动画的可选配置（在 loader.js 之前加载）。
 *
 * <p>只声明「有哪些动画」与各自的展示名；**动画 HTML 与样式都不在这里** ——
 * 两者唯一来源是 {@code loader.js}（与 {@code @repo/ui/re-loader} 的 animations.ts 逐字一致）。</p>
 *
 * <p>为什么把 HTML 挪走：这里曾经各写一份动画结构，结果叠加了两次同样的 bug ——
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
      { key: "dots", name: "三个圆点", description: "经典三点跳动，最通用、最低调" },
      { key: "rings", name: "科技圆圈", description: "双环反向旋转 + 中心脉冲，偏科技感" },
      { key: "dino", name: "像素恐龙", description: "像素恐龙跨栏：应用内可玩，方向键 / 空格起跳、有积分、撞到就结束" },
      { key: "ripple", name: "水波涟漪", description: "三圈同心涟漪向外扩散，安静、节奏均匀" },
      { key: "orbit", name: "双轨环绕", description: "一颗卫星绕环运行，内外双轨反向，偏太空感" },
      { key: "bars", name: "音量柱", description: "四根竖向音柱各自起伏，像在听一段节奏" },
      { key: "matrix", name: "点阵", description: "七乘七点阵被一道扫描带掠过，像信号正在传输" },
      { key: "conveyor", name: "传送带", description: "板条箱沿传送带穿过闸门，适合表达流程在推进" },
      { key: "pillars", name: "柱阵", description: "柱阵逐排起伏，像数据正在铺开" },
      { key: "cards", name: "卡片翻动", description: "三张卡片依次翻面，节奏轻快不抢眼" },
      { key: "none", name: "无动画", description: "只保留纯色底，不显示任何动效" },
    ],
  };
})();
