/**
 * 统一的全屏加载动画入口
 *
 * <p>运行时优先读取 /loader-config.js 注入的全局配置，缺失时回退到最小默认样式。</p>
 *
 * <p>四个动画的样式与应用内 {@code ReLoader} 共用同一份定义
 * （{@code @repo/ui/re-loader} 的 {@code animations.ts}）；本文件是 public 静态资源，
 * 不能 import 那个模块，所以这里放的是逐字一致的副本 ——
 * 由 {@code test/vue/loader-anim/verify.mjs} 做字节比对，改动必须两处同时改。</p>
 */
(function () {
  "use strict";

  var LOADER_STYLE_ID = "app-loader-style";
  var LOADER_CONTAINER_ID = "app-loader";
  var LOADER_SHELL_CLASS = "sys-loader-shell";
  var GLOBAL_LOADER_STORAGE_KEY = "sys-loader-style";
  /* 部署侧接管时由应用启动后写入（下次刷新生效）：尺寸 px / 主色 */
  var LOADER_SIZE_KEY = "sys-loader-size";
  var LOADER_COLOR_KEY = "sys-loader-color";
  var LOADER_SIZE_DEFAULT = 96;
  var LOADER_COLOR_DEFAULT = "#406eeb";
  var LOADER_SHELL_STYLE_TEXT =
    ".sys-loader-shell{position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%}";

  /* 首屏尺寸：默认与应用内 lg 档（96px）一致，刷新时观感连贯；
     部署侧 other.loaderConfig 接管时由下面的 resolveBootSize 读配置值。 */
  function resolveBootSize() {
    var raw = localStorage.getItem(LOADER_SIZE_KEY);
    var size = parseInt(raw || "", 10);
    if (!isFinite(size) || size < 8 || size > 512) {
      return LOADER_SIZE_DEFAULT;
    }
    return size;
  }

  /* 首屏主色：只认 #hex / rgb() / hsl()，避免把任意字符串拼进 CSS（注入面） */
  function resolveBootColor() {
    var raw = (localStorage.getItem(LOADER_COLOR_KEY) || "").trim();
    var safe =
      /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(raw) ||
      /^(?:rgb|hsl)a?\(\s*[\d.%\s,/+-]+\)$/i.test(raw);
    return safe ? raw : LOADER_COLOR_DEFAULT;
  }

  function resolveBootAnimationStyleText() {
    return (
      "#app-loader .sla{--sla-s:" +
      resolveBootSize() +
      "px;--sla-c:" +
      resolveBootColor() +
      "}"
    );
  }

  /**
   * 历史取值映射。
   *
   * <p>旧版本存过 default / dinoGame / simple / pulse 等键，后端 LoadingPageStyle 也会写入
   * spinner / clock / pixel 这类值。不能让老用户刷新后看到「动画没了」，统一折到四个之一。</p>
   */
  var LEGACY_ALIASES = {
    // 现行键必须先映射到自己：漏一个就会"选了却回退默认"，而且完全无声。
    wave: "wave",
    dots: "dots",
    rings: "rings",
    dino: "dino",
    none: "none",

    default: "dots",
    pulse: "dots",
    blocks: "dots",
    book: "dots",
    writing: "dots",
    pokemon: "dots",
    dinoGame: "dino",
    pixel: "dino",
    // ReLoader 的旧键：spin 曾等价于"科技双环"
    spin: "rings",
    simple: "rings",
    spinner: "rings",
    space: "rings",
    cyberpunk: "rings",
    clock: "wave",
    minimal: "wave",
    cube: "wave",
    servererror: "wave",
  };

  /* 水面路径：viewBox 200×26，两个完整周期 —— 平移 50% 正好无缝循环 */
  var CREST =
    "M0 13 C 20 7 30 7 50 13 C 70 19 80 19 100 13 C 120 7 130 7 150 13 C 170 19 180 19 200 13";
  var CREST_BACK =
    "M0 13 C 20 8 30 8 50 13 C 70 18 80 18 100 13 C 120 8 130 8 150 13 C 170 18 180 18 200 13";

  function waveLayer(kind) {
    var crest = kind === "back" ? CREST_BACK : CREST;
    // 曲线 → 右下角 → 左下角 → 闭合：整条带成为实心水面。
    // 不闭合时浏览器会用"终点→起点"直线补齐，只剩一条细月牙，
    // 真正的水面会变成下方填充矩形的平直顶边（波谷被切掉，看着像两层水面）。
    // 不要加白色浪脊描边：水位高时会横贯整个容器，看上去又是第二道水面。
    var fill = crest + " L200 26 L0 26 Z";
    return (
      '<div class="sla-wave__layer sla-wave__layer--' +
      kind +
      '">' +
      '<div class="sla-wave__cap"><svg viewBox="0 0 200 26" preserveAspectRatio="none">' +
      '<path d="' +
      fill +
      '" fill="currentColor"/></svg></div>' +
      '<div class="sla-wave__fill"></div>' +
      "</div>"
    );
  }

  var ANIMATION_HTML = {
    wave:
      '<div class="sla sla-wave"><div class="sla-wave__sea">' +
      waveLayer("back") +
      waveLayer("front") +
      "</div></div>",
    dots: '<div class="sla sla-dots"><i></i><i></i><i></i></div>',
    rings: '<div class="sla sla-rings"><i></i><i></i><i></i></div>',
    dino:
      '<div class="sla sla-dino"><div class="sla-dino__scene">' +
      '<div class="sla-dino__ground"></div>' +
      '<div class="sla-dino__runner">' +
      '<span class="sla-dino__body"></span><span class="sla-dino__head"></span>' +
      '<span class="sla-dino__tail"></span>' +
      '<span class="sla-dino__leg sla-dino__leg--front"></span>' +
      '<span class="sla-dino__leg sla-dino__leg--back"></span>' +
      "</div>" +
      '<div class="sla-dino__cactus">' +
      '<span class="sla-dino__branch sla-dino__branch--left"></span>' +
      '<span class="sla-dino__branch sla-dino__branch--right"></span>' +
      "</div></div></div>",
    none: '<div class="sla sla-none" aria-hidden="true"></div>',
  };

  /*
   * 统一加载动画的完整样式（与 @repo/ui/re-loader 的 LOADER_ANIMATION_CSS 逐字一致）。
   */
  var ANIMATION_CSS =
    "/* ===== 统一加载动画（首屏 loader / ReLoader / 主题配置预览共用） ===== */" +
    ".sla{position:relative;display:block;flex:0 0 auto;box-sizing:border-box;line-height:0;color:var(--sla-c,#406eeb)}" +
    ".sla-dots{width:calc(var(--sla-s,64px)*1.5);height:calc(var(--sla-s,64px)*0.4);display:flex;align-items:center;justify-content:space-between}" +
    ".sla-dots i{display:block;width:calc(var(--sla-s,64px)*0.34);height:calc(var(--sla-s,64px)*0.34);border-radius:50%;background:currentColor;animation:sla-dot 1.35s ease-in-out infinite}" +
    ".sla-dots i:nth-child(2){animation-delay:.14s}" +
    ".sla-dots i:nth-child(3){animation-delay:.28s}" +
    "@keyframes sla-dot{0%,100%{transform:translateY(0) scale(.86);opacity:.5}30%{transform:translateY(-38%) scale(1.1);opacity:1}60%{transform:translateY(0) scale(.94);opacity:.78}}" +
    ".sla-rings{width:var(--sla-s,64px);height:var(--sla-s,64px);font-size:calc(var(--sla-s,64px)/4.4)}" +
    ".sla-rings::before{content:'';position:absolute;inset:-.45em;border:.1em solid color-mix(in srgb,var(--sla-c,#406eeb) 12%,transparent);border-radius:50%}" +
    ".sla-rings i{position:absolute;display:block;border-radius:50%;border:.2em solid transparent}" +
    ".sla-rings i:nth-child(1){inset:0;border-top-color:currentColor;border-right-color:color-mix(in srgb,var(--sla-c,#406eeb) 40%,transparent);animation:sla-spin 1.6s linear infinite}" +
    ".sla-rings i:nth-child(2){inset:.85em;border-bottom-color:currentColor;border-left-color:color-mix(in srgb,var(--sla-c,#406eeb) 40%,transparent);animation:sla-spin 1.15s linear infinite reverse}" +
    ".sla-rings i:nth-child(3){inset:0;width:.62em;height:.62em;margin:auto;background:currentColor;border:0;animation:sla-core 1.7s ease-in-out infinite}" +
    "@keyframes sla-spin{from{transform:rotate(0)}to{transform:rotate(360deg)}}" +
    "@keyframes sla-core{0%,100%{transform:scale(.72);opacity:.6}50%{transform:scale(1.12);opacity:1}}" +
    ".sla-wave{width:var(--sla-s,64px);height:var(--sla-s,64px);font-size:calc(var(--sla-s,64px)/6.4);border-radius:1.7em;overflow:hidden;background:color-mix(in srgb,var(--sla-c,#406eeb) 7%,transparent);box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--sla-c,#406eeb) 15%,transparent)}" +
    ".sla-wave__sea{position:absolute;left:0;right:0;bottom:0;height:30%;animation:sla-rise 8s cubic-bezier(.45,0,.55,1) infinite alternate}" +
    ".sla-wave.is-determinate .sla-wave__sea{animation:none;height:calc(12% + var(--sla-level,0)*0.54%)}" +
    ".sla-wave__layer{position:absolute;left:0;right:0;top:0;bottom:0}" +
    ".sla-wave__fill{position:absolute;left:0;right:0;top:-1px;bottom:-45%}" +
    ".sla-wave__cap{position:absolute;left:0;bottom:100%;width:200%;height:1.35em;animation:sla-drift 13s linear infinite;will-change:transform}" +
    ".sla-wave__cap svg{display:block;width:100%;height:100%}" +
    ".sla-wave__layer--back{transform:translateY(-.48em)}" +
    ".sla-wave__layer--back .sla-wave__cap{animation-duration:19s;animation-direction:reverse}" +
    ".sla-wave__layer--back .sla-wave__fill{background:color-mix(in srgb,var(--sla-c,#406eeb) 34%,white)}" +
    ".sla-wave__layer--back .sla-wave__cap{color:color-mix(in srgb,var(--sla-c,#406eeb) 34%,white)}" +
    ".sla-wave__layer--front .sla-wave__fill{background:linear-gradient(180deg,color-mix(in srgb,var(--sla-c,#406eeb) 62%,white) 0%,var(--sla-c,#406eeb) 44%,color-mix(in srgb,var(--sla-c,#406eeb) 72%,#0b1a4d) 100%)}" +
    ".sla-wave__layer--front .sla-wave__cap{color:color-mix(in srgb,var(--sla-c,#406eeb) 62%,white)}" +
    ".sla-wave__layer--front .sla-wave__fill::after{content:'';position:absolute;inset:0;pointer-events:none;opacity:.26;mix-blend-mode:screen;filter:blur(.12em);background:linear-gradient(99deg,transparent 38%,rgba(255,255,255,.55) 47%,transparent 56%),linear-gradient(99deg,transparent 64%,rgba(255,255,255,.38) 72%,transparent 80%);animation:sla-caustic 15s ease-in-out infinite alternate}" +
    "@keyframes sla-rise{0%{height:27%}100%{height:67%}}" +
    "@keyframes sla-drift{from{transform:translate3d(0,0,0)}to{transform:translate3d(-50%,0,0)}}" +
    "@keyframes sla-caustic{from{transform:translateX(-14%)}to{transform:translateX(14%)}}" +
    ".sla-dino{width:calc(var(--sla-s,96px)*2.5);height:var(--sla-s,96px);font-size:calc(var(--sla-s,96px)/8.8);overflow:hidden}" +
    ".sla-dino__scene{position:relative;width:100%;height:100%;overflow:hidden}" +
    ".sla-dino__ground{position:absolute;right:0;bottom:1.4em;left:0;height:.3em;background:repeating-linear-gradient(90deg,#52525b 0 1.4em,rgba(82,82,91,.15) 1.4em 2em);background-size:2em 100%;animation:sla-dino-ground 1.65s linear infinite}" +
    ".sla-dino__runner{position:absolute;bottom:1.7em;left:2.6em;width:4.8em;height:4em;z-index:1;animation:sla-dino-jump 1.15s ease-in-out infinite}" +
    ".sla-dino__body,.sla-dino__head,.sla-dino__tail,.sla-dino__leg,.sla-dino__cactus,.sla-dino__branch{position:absolute;display:block;background:#111827}" +
    ".sla-dino__body{bottom:1em;left:.8em;width:2.4em;height:1.6em}" +
    ".sla-dino__head{top:0;right:0;width:1.6em;height:1.4em;box-shadow:-.6em .3em 0 0 #111827,.8em .3em 0 -.5em #f8fafc}" +
    ".sla-dino__tail{bottom:1.8em;left:0;width:1em;height:.6em}" +
    ".sla-dino__leg{bottom:0;width:.6em;height:1.4em;animation:sla-dino-run .38s steps(1) infinite}" +
    ".sla-dino__leg--front{left:1.4em}" +
    ".sla-dino__leg--back{left:2.6em;animation-delay:.19s}" +
    ".sla-dino__cactus{right:-3em;bottom:1.7em;width:1.4em;height:3em;background:#14532d;z-index:0;animation:sla-dino-cactus 1.65s linear infinite}" +
    ".sla-dino__branch{width:.6em;height:1.2em;background:#14532d}" +
    ".sla-dino__branch--left{top:.8em;left:-.5em}" +
    ".sla-dino__branch--right{top:.3em;right:-.5em}" +
    "@keyframes sla-dino-jump{0%,22%,100%{transform:translateY(0)}48%{transform:translateY(-2.8em)}}" +
    "@keyframes sla-dino-run{0%,100%{height:1.4em}50%{height:.9em}}" +
    "@keyframes sla-dino-cactus{from{transform:translateX(0)}to{transform:translateX(-18em)}}" +
    "@keyframes sla-dino-ground{from{background-position:0 0}to{background-position:-2em 0}}" +
    ".sla-none{width:1px;height:1px;opacity:0}" +
    "@media (prefers-reduced-motion:reduce){" +
    ".sla-dots i{animation-duration:2.4s}" +
    ".sla-rings i:nth-child(1){animation-duration:2.8s}" +
    ".sla-rings i:nth-child(2){animation-duration:2s}" +
    ".sla-wave__sea{animation-duration:14s}" +
    ".sla-wave__cap{animation-duration:22s}" +
    ".sla-wave__layer--back .sla-wave__cap{animation-duration:30s}" +
    ".sla-dino__runner{animation-duration:1.9s}" +
    ".sla-dino__cactus{animation-duration:2.6s}" +
    ".sla-dino__ground{animation-duration:2.6s}" +
    "}";

  function normalizeKey(value) {
    return LEGACY_ALIASES[String(value || "").trim()] || "";
  }

  function createFallbackConfig() {
    return {
      baseStyleText:
        "html,body{margin:0;padding:0;width:100%;height:100%}" +
        "#app{width:100%;height:100%;margin:0;padding:0}" +
        "#app-loader{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;width:100%;height:100%;box-sizing:border-box;padding:32px;background:radial-gradient(circle at top,rgba(64,110,235,.08),transparent 44%),#fff;z-index:9999}" +
        "html.dark #app-loader{background:radial-gradient(circle at top,rgba(64,110,235,.14),transparent 44%),#111827}",
      animationStyleText: ANIMATION_CSS,
      definitions: [
        { key: "wave", name: "海浪", description: "", previewScale: 1, html: ANIMATION_HTML.wave, css: "" },
        { key: "dots", name: "三个圆点", description: "", previewScale: 1, html: ANIMATION_HTML.dots, css: "" },
        { key: "rings", name: "科技圆圈", description: "", previewScale: 1, html: ANIMATION_HTML.rings, css: "" },
        { key: "dino", name: "像素恐龙", description: "", previewScale: 1, html: ANIMATION_HTML.dino, css: "" },
        { key: "none", name: "无动画", description: "", previewScale: 1, html: ANIMATION_HTML.none, css: "" },
      ],
    };
  }

  function resolveConfig() {
    var runtimeConfig = window.__SYS_LOADER_CONFIG__;
    if (
      runtimeConfig &&
      Array.isArray(runtimeConfig.definitions) &&
      runtimeConfig.definitions.length > 0
    ) {
      return runtimeConfig;
    }
    return createFallbackConfig();
  }

  function normalizeString(value) {
    return typeof value === "string" ? value.trim() : "";
  }

  function resolveLoaderAppCode() {
    var windowAppCode = normalizeString(window.__SYS_APP_CODE__);
    if (windowAppCode) {
      return windowAppCode;
    }

    var metaAppCode = normalizeString(
      document
        .querySelector('meta[name="sys-app-code"]')
        ?.getAttribute("content"),
    );
    if (metaAppCode) {
      return metaAppCode;
    }

    return normalizeString(
      document.documentElement?.getAttribute("data-system-code"),
    );
  }

  function resolveLoaderStorageKey() {
    var appCode = resolveLoaderAppCode();
    return appCode
      ? GLOBAL_LOADER_STORAGE_KEY + ":" + appCode
      : GLOBAL_LOADER_STORAGE_KEY;
  }

  function createLoaderMap(definitions) {
    return definitions.reduce(function (map, definition) {
      if (!definition || !definition.key) {
        return map;
      }
      // 配置里可以只写 key + 文案：HTML 一律以 ANIMATION_HTML 为准
      // （避免 loader-config.js 再存一份结构，改样式时漏改就画错）
      map[definition.key] = {
        key: definition.key,
        name: definition.name || "",
        description: definition.description || "",
        html: ANIMATION_HTML[definition.key] || definition.html || "",
        css: definition.css || "",
      };
      return map;
    }, {});
  }

  function createLoaderMarkup(loader) {
    return (
      '<div class="' +
      LOADER_SHELL_CLASS +
      '" data-loader-key="' +
      String(loader.key || "none") +
      '">' +
      String(loader.html || "") +
      "</div>"
    );
  }

  function ensureStyleElement(styleText) {
    var existingStyle = document.getElementById(LOADER_STYLE_ID);
    if (existingStyle) {
      existingStyle.textContent = styleText;
      return existingStyle;
    }

    var style = document.createElement("style");
    style.id = LOADER_STYLE_ID;
    style.textContent = styleText;
    (document.head || document.documentElement).appendChild(style);
    return style;
  }

  function mountWhenBodyReady(fn) {
    if (document.body) {
      fn();
      return;
    }

    var mounted = false;
    var run = function () {
      if (mounted || !document.body) {
        return;
      }
      mounted = true;
      fn();
    };

    document.addEventListener("DOMContentLoaded", run, { once: true });

    var timer = window.setInterval(function () {
      if (!document.body) {
        return;
      }
      window.clearInterval(timer);
      run();
    }, 16);
  }

  function ensureLoaderElement(loaderMarkup) {
    mountWhenBodyReady(function () {
      if (document.getElementById(LOADER_CONTAINER_ID)) {
        return;
      }

      var loader = document.createElement("div");
      loader.id = LOADER_CONTAINER_ID;
      loader.innerHTML = loaderMarkup;
      document.body.insertBefore(loader, document.body.firstChild || null);
    });
  }

  function appendOptionalScript(src) {
    mountWhenBodyReady(function () {
      if (
        document.querySelector('script[src="' + src + '"]')
      ) {
        return;
      }

      var script = document.createElement("script");
      script.src = src;
      script.async = true;
      (document.body || document.head || document.documentElement).appendChild(
        script,
      );
    });
  }

  /* 读取用户选择：先读带 appCode 的键，再退回全局键，最后兜底默认（海浪） */
  function resolveLoaderType() {
    var scoped = normalizeKey(localStorage.getItem(resolveLoaderStorageKey()));
    if (scoped) {
      return scoped;
    }
    var globalKey = normalizeKey(localStorage.getItem(GLOBAL_LOADER_STORAGE_KEY));
    return globalKey || "wave";
  }

  var loaderType = resolveLoaderType();
  var config = resolveConfig();
  var definitions = Array.isArray(config.definitions) ? config.definitions : [];
  var fallbackConfig = createFallbackConfig();
  var fallbackLoader = fallbackConfig.definitions[0];
  var loaderMap = createLoaderMap(definitions);
  var loader =
    loaderMap[loaderType] ||
    loaderMap.none ||
    loaderMap.wave ||
    loaderMap.default ||
    definitions[0] ||
    fallbackLoader;
  var shouldMountLoader = loader && loader.key !== "none";

  ensureStyleElement(
    String(config.baseStyleText || fallbackConfig.baseStyleText) +
      LOADER_SHELL_STYLE_TEXT +
      String(config.animationStyleText || fallbackConfig.animationStyleText) +
      resolveBootAnimationStyleText() +
      String(shouldMountLoader ? loader.css : ""),
  );
  if (shouldMountLoader) {
    ensureLoaderElement(createLoaderMarkup(loader));
  }

  window.hideAppLoader = function () {
    var appLoader = document.getElementById(LOADER_CONTAINER_ID);
    if (appLoader && appLoader.parentNode) {
      appLoader.parentNode.removeChild(appLoader);
    }
  };

  var enableOptionalScripts =
    localStorage.getItem("sys-enable-optional-scripts") === "true";
  if (enableOptionalScripts) {
    var optionalScripts = ["/adapter.min.js", "/webrtcstreamer.js"];
    optionalScripts.forEach(function (src) {
      fetch(src, { method: "HEAD" })
        .then(function (response) {
          if (response.ok) {
            appendOptionalScript(src);
          }
        })
        .catch(function () {});
    });
  }
})();
