(function () {
  "use strict";

  if (window.__SYS_LOADER_CONFIG__) {
    return;
  }

  window.__SYS_LOADER_CONFIG__ = {
    baseStyleText:
      "html,body{margin:0;padding:0;width:100%;height:100%}" +
      "#app{width:100%;height:100%;margin:0;padding:0}" +
      "#app-loader{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;width:100%;height:100%;padding:32px;background:radial-gradient(circle at top,rgba(64,110,235,.08),transparent 44%),#fff;z-index:9999}" +
      "html.dark #app-loader{background:radial-gradient(circle at top,rgba(64,110,235,.14),transparent 44%),#111827}",
    definitions: [
      {
        key: "none",
        name: "无动画",
        description: "不显示加载动效",
        previewScale: 1,
        html: '<div class="sys-loader-none" aria-hidden="true"></div>',
        css: ".sys-loader-none{width:1px;height:1px;opacity:0}",
      },
      {
        key: "default",
        name: "三个圆点",
        description: "经典的三点跳动，适合通用加载页",
        previewScale: 0.46,
        html: '<div class="sys-loader-default"></div>',
        css: ".sys-loader-default,.sys-loader-default::before,.sys-loader-default::after{width:2.5em;height:2.5em;border-radius:50%;animation:sys-loader-load-animation 1.8s infinite ease-in-out;animation-fill-mode:both}.sys-loader-default{position:relative;margin:0 auto;font-size:10px;color:#406eeb;text-indent:-9999em;transform:translateZ(0);animation-delay:-0.16s}.sys-loader-default::before,.sys-loader-default::after{position:absolute;top:0;content:''}.sys-loader-default::before{left:-3.5em;animation-delay:-0.32s}.sys-loader-default::after{left:3.5em}@keyframes sys-loader-load-animation{0%,80%,100%{box-shadow:0 2.5em 0 -1.3em}40%{box-shadow:0 2.5em 0 0}}",
      },
      {
        key: "rings",
        name: "彩色圆环",
        description: "多层旋转圆环，偏科技感",
        previewScale: 0.48,
        html: '<div class="sys-loader-loading-spinner"><div class="sys-loader-spinner-ring"></div><div class="sys-loader-spinner-ring"></div><div class="sys-loader-spinner-ring"></div><div class="sys-loader-spinner-ring"></div></div>',
        css: ".sys-loader-loading-spinner{position:relative;width:120px;height:120px}.sys-loader-spinner-ring{position:absolute;width:100%;height:100%;border:3px solid transparent;border-radius:50%;animation:sys-loader-spin 2s linear infinite}.sys-loader-spinner-ring:nth-child(1){border-top-color:#ff6b6b}.sys-loader-spinner-ring:nth-child(2){top:5%;left:5%;width:90%;height:90%;border-right-color:#4ecdc4;animation-delay:.5s}.sys-loader-spinner-ring:nth-child(3){top:10%;left:10%;width:80%;height:80%;border-bottom-color:#45b7d1;animation-delay:1s}.sys-loader-spinner-ring:nth-child(4){top:15%;left:15%;width:70%;height:70%;border-left-color:#f9ca24;animation-delay:1.5s}@keyframes sys-loader-spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}",
      },
      {
        key: "simple",
        name: "简约圆环",
        description: "单色旋转圆环，最干净的方案",
        previewScale: 0.66,
        html: '<div class="sys-loader-simple-spinner"></div>',
        css: ".sys-loader-simple-spinner{width:50px;height:50px;border:4px solid rgba(64,110,235,.2);border-top-color:#406eeb;border-radius:50%;animation:sys-loader-simple-spin 1s linear infinite}@keyframes sys-loader-simple-spin{to{transform:rotate(360deg)}}",
      },
      {
        key: "pulse",
        name: "脉冲圆点",
        description: "呼吸式脉冲动画，视觉最轻",
        previewScale: 0.82,
        html: '<div class="sys-loader-pulse-loader"></div>',
        css: ".sys-loader-pulse-loader{width:20px;height:20px;background:#406eeb;border-radius:50%;animation:sys-loader-pulse-animation 1.5s ease-in-out infinite}@keyframes sys-loader-pulse-animation{0%,100%{transform:scale(1);opacity:1}50%{transform:scale(1.5);opacity:.5}}",
      },
      {
        key: "blocks",
        name: "跳动方块",
        description: "节奏感更强，适合数据操作场景",
        previewScale: 0.7,
        html: '<div class="sys-loader-blocks-loader"><div class="sys-loader-block"></div><div class="sys-loader-block"></div><div class="sys-loader-block"></div></div>',
        css: ".sys-loader-blocks-loader{display:flex;gap:8px}.sys-loader-block{width:15px;height:15px;background:#406eeb;border-radius:3px;animation:sys-loader-block-jump 1.4s ease-in-out infinite}.sys-loader-block:nth-child(2){animation-delay:.2s}.sys-loader-block:nth-child(3){animation-delay:.4s}@keyframes sys-loader-block-jump{0%,80%,100%{transform:translateY(0)}40%{transform:translateY(-20px)}}",
      },
      {
        key: "minecraft",
        name: "像素挖掘",
        description: "像素镐和方块，偏游戏化",
        previewScale: 0.48,
        html: '<div class="sys-loader-minecraft-loader"><div class="sys-loader-pickaxe">⛏️</div><div class="sys-loader-mc-blocks"><div class="sys-loader-mc-block"></div><div class="sys-loader-mc-block"></div><div class="sys-loader-mc-block"></div></div></div>',
        css: ".sys-loader-minecraft-loader{display:flex;flex-direction:column;align-items:center;gap:18px}.sys-loader-pickaxe{font-size:48px;animation:sys-loader-mining .85s ease-in-out infinite}.sys-loader-mc-blocks{display:flex;gap:8px}.sys-loader-mc-block{width:20px;height:20px;background:linear-gradient(135deg,#8b5a2b 0%,#5a3820 100%);border:2px solid #1f2937;animation:sys-loader-break-block 2.4s ease-in-out infinite}.sys-loader-mc-block:nth-child(2){animation-delay:.8s}.sys-loader-mc-block:nth-child(3){animation-delay:1.6s}@keyframes sys-loader-mining{0%,100%{transform:rotate(-20deg) translateY(0)}50%{transform:rotate(20deg) translateY(-10px)}}@keyframes sys-loader-break-block{0%,80%,100%{transform:scale(1);opacity:1}90%{transform:scale(.78);opacity:.4}}",
      },
      {
        key: "pokemon",
        name: "双元素追逐",
        description: "双角色对冲，适合强调活跃感",
        previewScale: 0.45,
        html: '<div class="sys-loader-pokemon-loader"><div class="sys-loader-pokemon-ball sys-loader-pokemon-ball--fire"></div><div class="sys-loader-pokemon-ball sys-loader-pokemon-ball--water"></div></div>',
        css: ".sys-loader-pokemon-loader{position:relative;width:200px;height:60px}.sys-loader-pokemon-ball{position:absolute;top:14px;width:30px;height:30px;border-radius:999px;box-shadow:inset 0 -4px 0 rgba(255,255,255,.35),0 8px 18px rgba(15,23,42,.18);animation:sys-loader-pokemon-chase 2.8s linear infinite}.sys-loader-pokemon-ball::before{content:'';position:absolute;inset:50% 0 auto;height:2px;background:rgba(255,255,255,.8);transform:translateY(-50%)}.sys-loader-pokemon-ball::after{content:'';position:absolute;top:50%;left:50%;width:8px;height:8px;background:#fff;border-radius:50%;transform:translate(-50%,-50%)}.sys-loader-pokemon-ball--fire{background:linear-gradient(135deg,#ff7a59 0%,#ef4444 100%)}.sys-loader-pokemon-ball--water{background:linear-gradient(135deg,#38bdf8 0%,#2563eb 100%);animation-delay:1.4s}@keyframes sys-loader-pokemon-chase{0%{left:0;transform:scaleX(1)}45%{left:calc(100% - 30px);transform:scaleX(1)}50%{left:calc(100% - 30px);transform:scaleX(-1)}95%{left:0;transform:scaleX(-1)}100%{left:0;transform:scaleX(1)}}",
      },
      {
        key: "cyberpunk",
        name: "赛博故障",
        description: "故障文本和能量柱，氛围最强",
        previewScale: 0.42,
        html: '<div class="sys-loader-cyberpunk-loader"><div class="sys-loader-glitch">LOADING</div><div class="sys-loader-bars"><div class="sys-loader-bar"></div><div class="sys-loader-bar"></div><div class="sys-loader-bar"></div></div></div>',
        css: ".sys-loader-cyberpunk-loader{display:flex;flex-direction:column;align-items:center;gap:20px}.sys-loader-glitch{position:relative;font-size:32px;font-weight:800;letter-spacing:.2em;color:#00ffb2;text-shadow:2px 2px #ff00de,-2px -2px #00ffff;animation:sys-loader-glitch .9s infinite}.sys-loader-bars{display:flex;gap:8px}.sys-loader-bar{width:6px;height:40px;background:linear-gradient(180deg,#00ffb2 0%,#ff00de 100%);border-radius:999px;animation:sys-loader-cyber-pulse 1.2s ease-in-out infinite}.sys-loader-bar:nth-child(2){animation-delay:.2s}.sys-loader-bar:nth-child(3){animation-delay:.4s}@keyframes sys-loader-glitch{0%,100%{transform:translate(0)}20%{transform:translate(-2px,2px)}40%{transform:translate(2px,-2px)}60%{transform:translate(-2px,-2px)}80%{transform:translate(2px,2px)}}@keyframes sys-loader-cyber-pulse{0%,100%{height:20px;opacity:.45}50%{height:52px;opacity:1}}",
      },
      {
        key: "book",
        name: "翻书",
        description: "立体翻页效果，偏文档类体验",
        previewScale: 0.42,
        html: '<div class="sys-loader-book-loader"><div class="sys-loader-book"><div class="sys-loader-page"></div><div class="sys-loader-page"></div><div class="sys-loader-page"></div></div></div>',
        css: ".sys-loader-book-loader{display:flex;align-items:center;justify-content:center;perspective:1000px}.sys-loader-book{position:relative;width:80px;height:100px;transform-style:preserve-3d;transform:rotateY(-8deg)}.sys-loader-page{position:absolute;width:100%;height:100%;background:linear-gradient(90deg,#f0f0f0 0%,#fff 50%,#f0f0f0 100%);border:2px solid #333;border-radius:0 8px 8px 0;transform-origin:left center;box-shadow:2px 2px 8px rgba(0,0,0,.2)}.sys-loader-page:nth-child(1){animation:sys-loader-flip-page 1.8s ease-in-out infinite}.sys-loader-page:nth-child(2){animation:sys-loader-flip-page 1.8s ease-in-out infinite;animation-delay:.3s}.sys-loader-page:nth-child(3){animation:sys-loader-flip-page 1.8s ease-in-out infinite;animation-delay:.6s}@keyframes sys-loader-flip-page{0%{transform:rotateY(0deg);opacity:1}60%{transform:rotateY(-160deg);opacity:1}100%{transform:rotateY(-180deg);opacity:0}}",
      },
      {
        key: "writing",
        name: "书写进度",
        description: "书写完成后翻页，适合提交类动作",
        previewScale: 0.42,
        html: '<div class="sys-loader-writing-loader"><div class="sys-loader-pen"></div><div class="sys-loader-paper"><div class="sys-loader-line"></div><div class="sys-loader-line"></div><div class="sys-loader-line"></div><div class="sys-loader-line"></div></div></div>',
        css: ".sys-loader-writing-loader{display:flex;flex-direction:column;align-items:center;gap:10px}.sys-loader-pen{position:relative;width:42px;height:10px;background:linear-gradient(90deg,#1f2937 0%,#334155 55%,#94a3b8 100%);border-radius:999px 999px 3px 3px;transform-origin:center;animation:sys-loader-pen-move 4s ease-in-out infinite}.sys-loader-pen::after{content:'';position:absolute;top:50%;right:-8px;border-top:5px solid transparent;border-bottom:5px solid transparent;border-left:10px solid #f59e0b;transform:translateY(-50%)}.sys-loader-paper{width:140px;padding:20px;background:#fff;border:2px solid #333;border-radius:4px;box-shadow:2px 2px 8px rgba(0,0,0,.1);transform-origin:left center;animation:sys-loader-page-turn 4s ease-in-out infinite}.sys-loader-line{height:3px;margin:6px 0;background:#e5e7eb;border-radius:999px;overflow:hidden}.sys-loader-line::before{content:'';display:block;width:0;height:100%;background:#406eeb;border-radius:inherit;animation:sys-loader-write-line 4s ease-in-out infinite}.sys-loader-line:nth-child(1)::before{animation-delay:0s}.sys-loader-line:nth-child(2)::before{animation-delay:.4s}.sys-loader-line:nth-child(3)::before{animation-delay:.8s}.sys-loader-line:nth-child(4)::before{animation-delay:1.2s}@keyframes sys-loader-pen-move{0%{transform:translate(-40px,0) rotate(-38deg)}40%{transform:translate(40px,8px) rotate(-38deg)}50%{transform:translate(40px,8px) rotate(-38deg)}90%,100%{transform:translate(-40px,-6px) rotate(-38deg)}}@keyframes sys-loader-write-line{0%{width:0}40%,100%{width:100%}}@keyframes sys-loader-page-turn{0%,70%{transform:rotateY(0deg)}90%{transform:rotateY(-170deg)}100%{transform:rotateY(-180deg)}}",
      },
      {
        key: "dinoGame",
        name: "像素恐龙",
        description: "纯 HTML/CSS 的恐龙场景，不再依赖 iframe",
        previewScale: 0.42,
        html: '<div class="sys-loader-dino-scene"><div class="sys-loader-dino-ground"></div><div class="sys-loader-dino-runner"><span class="sys-loader-dino-body"></span><span class="sys-loader-dino-head"></span><span class="sys-loader-dino-tail"></span><span class="sys-loader-dino-leg sys-loader-dino-leg--front"></span><span class="sys-loader-dino-leg sys-loader-dino-leg--back"></span></div><div class="sys-loader-dino-cactus"><span class="sys-loader-dino-cactus-branch sys-loader-dino-cactus-branch--left"></span><span class="sys-loader-dino-cactus-branch sys-loader-dino-cactus-branch--right"></span></div></div>',
        css: ".sys-loader-dino-scene{position:relative;width:220px;height:88px;overflow:hidden}.sys-loader-dino-ground{position:absolute;right:0;bottom:14px;left:0;height:3px;background:repeating-linear-gradient(90deg,#52525b 0 14px,rgba(82,82,91,.15) 14px 20px)}.sys-loader-dino-runner{position:absolute;bottom:17px;left:26px;width:48px;height:40px;animation:sys-loader-dino-jump 1.15s ease-in-out infinite}.sys-loader-dino-body,.sys-loader-dino-head,.sys-loader-dino-tail,.sys-loader-dino-leg,.sys-loader-dino-cactus,.sys-loader-dino-cactus-branch{position:absolute;display:block;background:#111827}.sys-loader-dino-body{bottom:10px;left:8px;width:24px;height:16px}.sys-loader-dino-head{top:0;right:0;width:16px;height:14px;box-shadow:-6px 3px 0 0 #111827,8px 3px 0 -5px #f8fafc}.sys-loader-dino-tail{bottom:18px;left:0;width:10px;height:6px}.sys-loader-dino-leg{bottom:0;width:6px;height:14px;animation:sys-loader-dino-run .38s steps(1) infinite}.sys-loader-dino-leg--front{left:14px}.sys-loader-dino-leg--back{left:26px;animation-delay:.19s}.sys-loader-dino-cactus{right:-26px;bottom:17px;width:14px;height:30px;background:#14532d;animation:sys-loader-dino-cactus-move 1.65s linear infinite}.sys-loader-dino-cactus-branch{width:6px;height:12px;background:#14532d}.sys-loader-dino-cactus-branch--left{top:8px;left:-5px}.sys-loader-dino-cactus-branch--right{top:3px;right:-5px}@keyframes sys-loader-dino-jump{0%,22%,100%{transform:translateY(0)}48%{transform:translateY(-28px)}}@keyframes sys-loader-dino-run{0%,100%{height:14px}50%{height:9px}}@keyframes sys-loader-dino-cactus-move{from{transform:translateX(0)}to{transform:translateX(-260px)}}",
      },
    ],
  };
})();
