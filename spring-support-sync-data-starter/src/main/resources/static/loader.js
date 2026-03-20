/**
 * 统一的全屏加载动画管理器
 */
(function () {
  "use strict";

  var loaderType = localStorage.getItem("sys-loader-style") || "default";

  // 加载动画样式配置
  var loaderStyles = {
    default: {
      html: '<div class="sys-loader-default"></div>',
      css: '.sys-loader-default,.sys-loader-default::before,.sys-loader-default::after{width:2.5em;height:2.5em;border-radius:50%;animation:sys-loader-load-animation 1.8s infinite ease-in-out;animation-fill-mode:both}.sys-loader-default{position:relative;margin:0 auto;font-size:10px;color:#406eeb;text-indent:-9999em;transform:translateZ(0);animation-delay:-0.16s}.sys-loader-default::before,.sys-loader-default::after{position:absolute;top:0;content:""}.sys-loader-default::before{left:-3.5em;animation-delay:-0.32s}.sys-loader-default::after{left:3.5em}@keyframes sys-loader-load-animation{0%,80%,100%{box-shadow:0 2.5em 0 -1.3em}40%{box-shadow:0 2.5em 0 0}}',
    },
    rings: {
      html: '<div class="sys-loader-loading-spinner"><div class="sys-loader-spinner-ring"></div><div class="sys-loader-spinner-ring"></div><div class="sys-loader-spinner-ring"></div><div class="sys-loader-spinner-ring"></div></div>',
      css: ".sys-loader-loading-spinner{position:relative;width:120px;height:120px}.sys-loader-spinner-ring{position:absolute;width:100%;height:100%;border:3px solid transparent;border-radius:50%;animation:sys-loader-spin 2s linear infinite}.sys-loader-spinner-ring:nth-child(1){border-top-color:#ff6b6b;animation-delay:0s}.sys-loader-spinner-ring:nth-child(2){border-right-color:#4ecdc4;animation-delay:0.5s;width:90%;height:90%;top:5%;left:5%}.sys-loader-spinner-ring:nth-child(3){border-bottom-color:#45b7d1;animation-delay:1s;width:80%;height:80%;top:10%;left:10%}.sys-loader-spinner-ring:nth-child(4){border-left-color:#f9ca24;animation-delay:1.5s;width:70%;height:70%;top:15%;left:15%}@keyframes sys-loader-spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}",
    },
    simple: {
      html: '<div class="sys-loader-simple-spinner"></div>',
      css: ".sys-loader-simple-spinner{width:50px;height:50px;border:4px solid rgba(64,110,235,0.2);border-top-color:#406eeb;border-radius:50%;animation:sys-loader-simple-spin 1s linear infinite}@keyframes sys-loader-simple-spin{to{transform:rotate(360deg)}}",
    },
    pulse: {
      html: '<div class="sys-loader-pulse-loader"></div>',
      css: ".sys-loader-pulse-loader{width:20px;height:20px;background:#406eeb;border-radius:50%;animation:sys-loader-pulse-animation 1.5s ease-in-out infinite}@keyframes sys-loader-pulse-animation{0%,100%{transform:scale(1);opacity:1}50%{transform:scale(1.5);opacity:0.5}}",
    },
    blocks: {
      html: '<div class="sys-loader-blocks-loader"><div class="sys-loader-block"></div><div class="sys-loader-block"></div><div class="sys-loader-block"></div></div>',
      css: ".sys-loader-blocks-loader{display:flex;gap:8px}.sys-loader-block{width:15px;height:15px;background:#406eeb;border-radius:3px;animation:sys-loader-block-jump 1.4s ease-in-out infinite}.sys-loader-block:nth-child(1){animation-delay:0s}.sys-loader-block:nth-child(2){animation-delay:0.2s}.sys-loader-block:nth-child(3){animation-delay:0.4s}@keyframes sys-loader-block-jump{0%,80%,100%{transform:translateY(0)}40%{transform:translateY(-20px)}}",
    },
    minecraft: {
      html: '<div class="sys-loader-minecraft-loader"><div class="sys-loader-pickaxe">⛏️</div><div class="sys-loader-blocks"><div class="sys-loader-mc-block"></div><div class="sys-loader-mc-block"></div><div class="sys-loader-mc-block"></div></div></div>',
      css: ".sys-loader-minecraft-loader{display:flex;flex-direction:column;align-items:center;gap:20px}.sys-loader-pickaxe{font-size:48px;animation:sys-loader-mining 0.8s ease-in-out infinite}@keyframes sys-loader-mining{0%,100%{transform:rotate(-20deg) translateY(0)}50%{transform:rotate(20deg) translateY(-10px)}}.sys-loader-blocks{display:flex;gap:8px}.sys-loader-mc-block{width:20px;height:20px;background:linear-gradient(135deg,#8B4513 0%,#654321 100%);border:2px solid #000;animation:sys-loader-break-block 2.4s ease-in-out infinite}.sys-loader-mc-block:nth-child(1){animation-delay:0s}.sys-loader-mc-block:nth-child(2){animation-delay:0.8s}.sys-loader-mc-block:nth-child(3){animation-delay:1.6s}@keyframes sys-loader-break-block{0%,100%{opacity:1;transform:scale(1)}80%{opacity:1;transform:scale(1)}90%{opacity:0.5;transform:scale(0.8)}95%{opacity:0;transform:scale(0)}}",
    },
    pokemon: {
      html: '<div class="sys-loader-pokemon-loader"><div class="sys-loader-charmander">🔥</div><div class="sys-loader-squirtle">💧</div></div>',
      css: ".sys-loader-pokemon-loader{position:relative;width:200px;height:60px}.sys-loader-charmander,.sys-loader-squirtle{position:absolute;font-size:40px;animation:sys-loader-chase 3s linear infinite}.sys-loader-charmander{left:0;animation-delay:0s}.sys-loader-squirtle{left:0;animation-delay:1.5s}@keyframes sys-loader-chase{0%{left:0;transform:scaleX(1)}45%{left:calc(100% - 40px);transform:scaleX(1)}50%{left:calc(100% - 40px);transform:scaleX(-1)}95%{left:0;transform:scaleX(-1)}100%{left:0;transform:scaleX(1)}}",
    },
    cyberpunk: {
      html: '<div class="sys-loader-cyberpunk-loader"><div class="sys-loader-glitch">LOADING</div><div class="sys-loader-bars"><div class="sys-loader-bar"></div><div class="sys-loader-bar"></div><div class="sys-loader-bar"></div></div></div>',
      css: ".sys-loader-cyberpunk-loader{display:flex;flex-direction:column;align-items:center;gap:20px}.sys-loader-glitch{font-size:32px;font-weight:bold;color:#00ff41;text-shadow:2px 2px #ff00de,-2px -2px #00ffff;animation:sys-loader-glitch 1s infinite}@keyframes sys-loader-glitch{0%,100%{transform:translate(0)}20%{transform:translate(-2px,2px)}40%{transform:translate(2px,-2px)}60%{transform:translate(-2px,-2px)}80%{transform:translate(2px,2px)}}.sys-loader-bars{display:flex;gap:8px}.sys-loader-bar{width:6px;height:40px;background:linear-gradient(180deg,#00ff41 0%,#ff00de 100%);animation:sys-loader-cyber-pulse 1.2s ease-in-out infinite}.sys-loader-bar:nth-child(1){animation-delay:0s}.sys-loader-bar:nth-child(2){animation-delay:0.2s}.sys-loader-bar:nth-child(3){animation-delay:0.4s}@keyframes sys-loader-cyber-pulse{0%,100%{height:20px;opacity:0.5}50%{height:50px;opacity:1}}",
    },
    book: {
      html: '<div class="sys-loader-book-loader"><div class="sys-loader-book"><div class="sys-loader-page"></div><div class="sys-loader-page"></div><div class="sys-loader-page"></div></div></div>',
      css: ".sys-loader-book-loader{perspective:1000px}.sys-loader-book{position:relative;width:80px;height:100px;transform-style:preserve-3d;animation:sys-loader-book-open 2s ease-in-out infinite}.sys-loader-page{position:absolute;width:100%;height:100%;background:linear-gradient(90deg,#f0f0f0 0%,#fff 50%,#f0f0f0 100%);border:2px solid #333;border-radius:0 8px 8px 0;transform-origin:left center;box-shadow:2px 2px 8px rgba(0,0,0,0.2)}.sys-loader-page:nth-child(1){animation:sys-loader-flip-page 2s ease-in-out infinite;animation-delay:0s}.sys-loader-page:nth-child(2){animation:sys-loader-flip-page 2s ease-in-out infinite;animation-delay:0.3s}.sys-loader-page:nth-child(3){animation:sys-loader-flip-page 2s ease-in-out infinite;animation-delay:0.6s}@keyframes sys-loader-flip-page{0%,100%{transform:rotateY(0deg)}50%{transform:rotateY(-180deg)}}@keyframes sys-loader-book-open{0%,100%{transform:rotateY(0deg)}50%{transform:rotateY(10deg)}}",
    },
    writing: {
      html: '<div class="sys-loader-writing-loader"><div class="sys-loader-pen">✒️</div><div class="sys-loader-paper"><div class="sys-loader-line"></div><div class="sys-loader-line"></div><div class="sys-loader-line"></div></div></div>',
      css: ".sys-loader-writing-loader{display:flex;flex-direction:column;align-items:center;gap:10px}.sys-loader-pen{font-size:36px;animation:sys-loader-pen-move 2s ease-in-out infinite}@keyframes sys-loader-pen-move{0%,100%{transform:translateX(-30px) rotate(-45deg)}50%{transform:translateX(30px) rotate(-45deg)}}.sys-loader-paper{width:120px;padding:20px;background:#fff;border:2px solid #333;border-radius:4px;box-shadow:2px 2px 8px rgba(0,0,0,0.1)}.sys-loader-line{height:3px;background:#333;margin:8px 0;border-radius:2px;animation:sys-loader-write-line 2s ease-in-out infinite}.sys-loader-line:nth-child(1){animation-delay:0s}.sys-loader-line:nth-child(2){animation-delay:0.3s}.sys-loader-line:nth-child(3){animation-delay:0.6s}@keyframes sys-loader-write-line{0%,100%{width:0}50%{width:100%}}",
    },
    dinoGame: {
      html: '<iframe class="sys-loader-dino-iframe" title="dino-game" src="/dino-game/index.html"></iframe>',
      css: "#app{align-items:stretch;justify-content:stretch;padding:0}.sys-loader-dino-iframe{display:block;width:100vw;height:100vh;border:0}",
    },
  };

  var loader = loaderStyles[loaderType] || loaderStyles.default;
  var baseCSS =
    "html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden}#app{position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;background:#ffffff}html.dark #app{background:#1a1a1a}";

  document.write("<style>" + baseCSS + loader.css + "</style>");
  document.write('<body><div id="app">' + loader.html + "</div>");

  // 动态加载可选的 JS 文件（默认不启用）
  var enableOptionalScripts =
    localStorage.getItem("sys-enable-optional-scripts") === "true";
  if (enableOptionalScripts) {
    var optionalScripts = ["/adapter.min.js", "/webrtcstreamer.js"];
    optionalScripts.forEach(function (src) {
      fetch(src, { method: "HEAD" })
        .then(function (response) {
          if (response.ok) {
            var script = document.createElement("script");
            script.src = src;
            script.async = true;
            document.body.appendChild(script);
          }
        })
        .catch(function () {});
    });
  }
})();
