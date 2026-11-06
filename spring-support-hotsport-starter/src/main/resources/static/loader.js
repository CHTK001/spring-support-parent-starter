/**
 * 统一的全屏加载动画入口
 *
 * <p>运行时优先读取 /loader-config.js 注入的全局配置，缺失时回退到最小默认样式。</p>
 *
 * <p>十个动画的样式与应用内 {@code ReLoader} 共用同一份定义
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
  /*
   * 部署侧接管（app.yaml other.loaderConfig）的键：由应用启动后写入，下次刷新生效。
   *
   * 动画刻意**不复用** sys-loader-style：那个键是用户在主题配置里的选择，
   * 复用会让「打开锁定」覆盖掉用户的选择，「关掉锁定」后也回不去。
   * 所有键都按「带 appCode 的键优先、全局键兜底」读（与 sys-loader-style 同一口径）。
   */
  var LOADER_DEPLOY_ANIMATION_KEY = "sys-loader-animation";
  var LOADER_SIZE_KEY = "sys-loader-size";
  var LOADER_SIZE_WIDE_KEY = "sys-loader-size-wide";
  var LOADER_COLOR_KEY = "sys-loader-color";
  var LOADER_SPEED_KEY = "sys-loader-speed";
  var LOADER_ADAPT_COLOR_KEY = "sys-loader-adapt-color";
  var LOADER_SIZE_DEFAULT = 96;
  var LOADER_SIZE_WIDE_DEFAULT = 96;
  var LOADER_COLOR_DEFAULT = "#406eeb";
  var LOADER_SPEED_DEFAULT = 1;
  var LOADER_ADAPT_COLOR_VALUE = "var(--el-color-primary," + LOADER_COLOR_DEFAULT + ")";
  /* 宽窄屏断点：与 @repo/ui 的 LOADER_WIDTH_BREAKPOINT、config 的 BOOT_LOADER_WIDTH_BREAKPOINT 一致 */
  var LOADER_WIDTH_BREAKPOINT = 768;
  /* 用户偏好（主题配置里可调）：尺寸两档 / 速度 / 主色跟随，部署侧键优先 */
  var LOADER_PREFS_KEY = "sys-loader-prefs";
  var LOADER_SHELL_STYLE_TEXT =
    ".sys-loader-shell{position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%}";

  /* 读部署侧键：先带 appCode 的，再全局（只在应用起来之前可能拿不到 appCode） */
  function readDeployKey(baseKey) {
    var appCode = resolveLoaderAppCode();
    if (appCode) {
      var scoped = localStorage.getItem(baseKey + ":" + appCode);
      if (scoped) {
        return scoped;
      }
    }
    return localStorage.getItem(baseKey);
  }

  /* 尺寸：非法值 / 缺省用兜底；8~512 之外一律夹住 */
  function parseBootSize(raw, fallback) {
    var size = parseInt(raw, 10);
    if (!isFinite(size) || size < 8 || size > 512) {
      return fallback;
    }
    return size;
  }

  /* 用户偏好：sys-loader-prefs 存 JSON（{ size: {narrow,wide}, speed, adaptColor }），
     JSON 损坏 / 被禁用时整片回落默认，绝不让首屏因为一个坏字符串而白屏。 */
  function readLoaderPrefs() {
    var prefs = {
      size: { narrow: LOADER_SIZE_DEFAULT, wide: LOADER_SIZE_WIDE_DEFAULT },
      speed: LOADER_SPEED_DEFAULT,
      adaptColor: false,
    };
    try {
      var raw = readDeployKey(LOADER_PREFS_KEY);
      if (!raw) {
        return prefs;
      }
      var parsed = JSON.parse(raw);
      var size = (parsed && parsed.size) || {};
      prefs.size.narrow = parseBootSize(size.narrow, LOADER_SIZE_DEFAULT);
      prefs.size.wide = parseBootSize(size.wide, prefs.size.narrow);
      var speed = parseFloat(parsed && parsed.speed);
      prefs.speed = isFinite(speed) && speed > 0 ? Math.min(3, Math.max(0.5, speed)) : LOADER_SPEED_DEFAULT;
      prefs.adaptColor = parsed.adaptColor === true;
    } catch (error) {
      /* 保持默认 */
    }
    return prefs;
  }

  /* 首屏尺寸：部署侧键 > 用户偏好 > 默认（窄屏 96 / 宽屏同窄屏）。
     部署侧写单个数字时宽屏与窄屏同值（历史写法），写 size-wide 才分档。 */
  function resolveBootSize() {
    var prefs = readLoaderPrefs();
    var narrow = parseBootSize(readDeployKey(LOADER_SIZE_KEY), prefs.size.narrow);
    var wide = parseBootSize(readDeployKey(LOADER_SIZE_WIDE_KEY), narrow);
    return { narrow: narrow, wide: wide };
  }

  /* 速度倍率：部署侧 > 用户偏好 > 1；CSS 里所有时长都是 calc(Ns / var(--sla-rate,1)) */
  function resolveBootSpeed() {
    var raw = parseFloat(readDeployKey(LOADER_SPEED_KEY));
    if (isFinite(raw) && raw > 0) {
      return Math.min(3, Math.max(0.5, raw));
    }
    return readLoaderPrefs().speed;
  }

  /* 主色是否跟随主题主色：部署侧 true/false 优先，其次用户偏好 */
  function resolveBootAdaptColor() {
    var raw = readDeployKey(LOADER_ADAPT_COLOR_KEY);
    if (raw === "true") {
      return true;
    }
    if (raw === "false") {
      return false;
    }
    return readLoaderPrefs().adaptColor;
  }

  /* 首屏主色：跟随主题主色时用 var(--el-color-primary)（Element Plus 未加载时退回默认蓝）；
     否则只认 #hex / rgb() / hsl()，避免把任意字符串拼进 CSS（注入面） */
  function resolveBootColor() {
    if (resolveBootAdaptColor()) {
      return LOADER_ADAPT_COLOR_VALUE;
    }
    var raw = (readDeployKey(LOADER_COLOR_KEY) || "").trim();
    var safe =
      /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(raw) ||
      /^(?:rgb|hsl)a?\(\s*[\d.%\s,/+-]+\)$/i.test(raw);
    return safe ? raw : LOADER_COLOR_DEFAULT;
  }

  function resolveBootAnimationStyleText() {
    var size = resolveBootSize();
    var declarations = "--sla-s:" + size.narrow + "px;--sla-c:" + resolveBootColor();
    var speed = resolveBootSpeed();
    if (speed !== LOADER_SPEED_DEFAULT) {
      declarations += ";--sla-rate:" + speed;
    }
    var style = "#app-loader .sla{" + declarations + "}";
    // 两档尺寸只在真的不同时才写 media query —— 默认 96/96 时输出与改造前的字符串逐字一致
    if (size.wide !== size.narrow) {
      style +=
        "@media (min-width:" +
        LOADER_WIDTH_BREAKPOINT +
        "px){#app-loader .sla{--sla-s:" +
        size.wide +
        "px}}";
    }
    return style;
  }

  /**
   * 历史取值映射。
   *
   * <p>旧版本存过 default / dinoGame / simple / pulse 等键，后端 LoadingPageStyle 也会写入
   * spinner / clock / pixel 这类值。不能让老用户刷新后看到「动画没了」，统一折到现行动画之一。</p>
   */
  var LEGACY_ALIASES = {
    // 现行键必须先映射到自己：漏一个就会"选了却回退默认"，而且完全无声。
    dots: "dots",
    rings: "rings",
    dino: "dino",
    ripple: "ripple",
    orbit: "orbit",
    bars: "bars",
    matrix: "matrix",
    conveyor: "conveyor",
    pillars: "pillars",
    cards: "cards",
    none: "none",

    // 线稿动画（hairline-*）已下线，改为纯 CSS 的四款：存量选择折到最接近的一款
    "hairline-phosphor": "matrix",
    "hairline-slow": "conveyor",
    "hairline-terrain": "pillars",
    "hairline-turntable": "cards",

    // 已下线的动画：存量 localStorage / 后端配置里可能还有，折到现存的一款
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
    // 海浪已下线：存量值折到默认的三点，不留空白
    wave: "dots",
    hourglass: "dots",
    clock: "dots",
    minimal: "dots",
    cube: "dots",
    servererror: "dots",
  };

  var ANIMATION_HTML = {
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
    ripple: '<div class="sla sla-ripple"><i></i><i></i><i></i></div>',
    orbit: '<div class="sla sla-orbit"><i></i><i></i></div>',
    bars: '<div class="sla sla-bars"><i></i><i></i><i></i><i></i></div>',
    matrix: '<div class="sla sla-matrix"><i></i></div>',
    conveyor:
      '<div class="sla sla-conveyor">' +
      '<div class="sla-conveyor__belt sla-conveyor__belt--top"></div>' +
      '<div class="sla-conveyor__belt sla-conveyor__belt--bottom"></div>' +
      '<div class="sla-conveyor__crate"></div>' +
      "</div>",
    pillars:
      '<div class="sla sla-pillars">' +
      "<i></i><i></i><i></i><i></i><i></i>" +
      "<i></i><i></i><i></i><i></i><i></i>" +
      "<i></i><i></i><i></i><i></i><i></i>" +
      "</div>",
    cards: '<div class="sla sla-cards"><i></i><i></i><i></i></div>',
  };

  /*
   * 统一加载动画的完整样式（与 @repo/ui/re-loader 的 LOADER_ANIMATION_CSS 逐字一致）。
   */
  var ANIMATION_CSS =
    "/* ===== 统一加载动画（首屏 loader / ReLoader / 主题配置预览共用） ===== */\n.sla{position:relative;display:block;flex:0 0 auto;box-sizing:border-box;line-height:0;color:var(--sla-c,#406eeb)}" +
    "/* ── 三个圆点 ──\n点的直径只取 --sla-s 的 0.16 倍（旧值 0.34）：首屏默认 96px 时单个点约 15px，" +
    "旧值会到 33px —— 三个连起来像三个大圆球，这是「默认太大了」的直接原因。" +
    "动效是每个点各自「从无到有」：scale(0)/opacity:0 起，依次错峰 0.16s，" +
    "于是看到的是「第一个冒出来 → 第二个 → 第三个 → 收回去」的呼吸，而不是整排一起跳。 */" +
    ".sla-dots{width:calc(var(--sla-s,64px)*1.1);height:calc(var(--sla-s,64px)*0.34);display:flex;align-items:center;justify-content:space-between}" +
    ".sla-dots i{display:block;width:calc(var(--sla-s,64px)*0.16);height:calc(var(--sla-s,64px)*0.16);border-radius:50%;background:currentColor;transform:scale(0);opacity:0;animation:sla-dot calc(1.5s / var(--sla-rate,1)) cubic-bezier(.4,0,.6,1) infinite both}" +
    ".sla-dots i:nth-child(2){animation-delay:calc(.16s / var(--sla-rate,1))}" +
    ".sla-dots i:nth-child(3){animation-delay:calc(.32s / var(--sla-rate,1))}" +
    "@keyframes sla-dot{0%,6%{transform:scale(0);opacity:0}42%{transform:scale(1);opacity:1}70%{transform:scale(1);opacity:1}100%{transform:scale(0);opacity:0}}" +
    "/* ── 科技圆圈 ── */\n.sla-rings{width:var(--sla-s,64px);height:var(--sla-s,64px);font-size:calc(var(--sla-s,64px)/4.4)}" +
    ".sla-rings::before{content:'';position:absolute;inset:-.45em;border:.1em solid color-mix(in srgb,var(--sla-c,#406eeb) 12%,transparent);border-radius:50%}" +
    ".sla-rings i{position:absolute;display:block;border-radius:50%;border:.2em solid transparent}" +
    ".sla-rings i:nth-child(1){inset:0;border-top-color:currentColor;border-right-color:color-mix(in srgb,var(--sla-c,#406eeb) 40%,transparent);animation:sla-spin calc(1.6s / var(--sla-rate,1)) linear infinite}" +
    ".sla-rings i:nth-child(2){inset:.85em;border-bottom-color:currentColor;border-left-color:color-mix(in srgb,var(--sla-c,#406eeb) 40%,transparent);animation:sla-spin calc(1.15s / var(--sla-rate,1)) linear infinite reverse}" +
    ".sla-rings i:nth-child(3){inset:0;width:.62em;height:.62em;margin:auto;background:currentColor;border:0;animation:sla-core calc(1.7s / var(--sla-rate,1)) ease-in-out infinite}" +
    "@keyframes sla-spin{from{transform:rotate(0)}to{transform:rotate(360deg)}}" +
    "@keyframes sla-core{0%,100%{transform:scale(.72);opacity:.6}50%{transform:scale(1.12);opacity:1}}" +
    "/* ── 像素恐龙 ──\n坐标全部用 em：--sla-s 表示**高度**（与圆圈同口径），" +
    "横向按原始 220×88 的比例放大到 2.5 倍高度。" +
    "为什么不写 calc(220px * calc(var(--sla-s)/220)) —— 单位相乘在 CSS 里非法，" +
    "整条声明会被丢弃，表现是\"永远 220px、永不缩放\"（实测踩过）。" +
    "用 em 一次解决：1em = 高度/8.8，所有原坐标除以 10 就是 em 值。 */" +
    ".sla-dino{width:calc(var(--sla-s,96px)*2.5);height:var(--sla-s,96px);font-size:calc(var(--sla-s,96px)/8.8);overflow:hidden}" +
    ".sla-dino__scene{position:relative;width:100%;height:100%;overflow:hidden}" +
    ".sla-dino__ground{position:absolute;right:0;bottom:1.4em;left:0;height:.3em;background:repeating-linear-gradient(90deg,#52525b 0 1.4em,rgba(82,82,91,.15) 1.4em 2em);background-size:2em 100%;animation:sla-dino-ground calc(1.65s / var(--sla-rate,1)) linear infinite}" +
    ".sla-dino__runner{position:absolute;bottom:1.7em;left:2.6em;width:4.8em;height:4em;z-index:1;animation:sla-dino-jump calc(1.15s / var(--sla-rate,1)) ease-in-out infinite}" +
    ".sla-dino__body,.sla-dino__head,.sla-dino__tail,.sla-dino__leg,.sla-dino__cactus,.sla-dino__branch{position:absolute;display:block;background:#111827}" +
    ".sla-dino__body{bottom:1em;left:.8em;width:2.4em;height:1.6em}" +
    ".sla-dino__head{top:0;right:0;width:1.6em;height:1.4em;box-shadow:-.6em .3em 0 0 #111827,.8em .3em 0 -.5em #f8fafc}" +
    ".sla-dino__tail{bottom:1.8em;left:0;width:1em;height:.6em}" +
    ".sla-dino__leg{bottom:0;width:.6em;height:1.4em;animation:sla-dino-run calc(.38s / var(--sla-rate,1)) steps(1) infinite}" +
    ".sla-dino__leg--front{left:1.4em}" +
    ".sla-dino__leg--back{left:2.6em;animation-delay:calc(.19s / var(--sla-rate,1))}" +
    "/* 仙人掌从左前方远处进入：起点在容器右侧之外，落到 ground 之上，\nz-index 低于 runner，于是\"从恐龙身后穿过\"而不是糊在它身上 */" +
    ".sla-dino__cactus{right:-3em;bottom:1.7em;width:1.4em;height:3em;background:#14532d;z-index:0;animation:sla-dino-cactus calc(1.65s / var(--sla-rate,1)) linear infinite}" +
    ".sla-dino__branch{width:.6em;height:1.2em;background:#14532d}" +
    ".sla-dino__branch--left{top:.8em;left:-.5em}" +
    ".sla-dino__branch--right{top:.3em;right:-.5em}" +
    "@keyframes sla-dino-jump{0%,22%,100%{transform:translateY(0)}48%{transform:translateY(-2.8em)}}" +
    "@keyframes sla-dino-run{0%,100%{height:1.4em}50%{height:.9em}}" +
    "/* 仙人掌横穿整个场景（宽度约 10em 的容器 + 起点在右侧外），到左侧外再回环 */\n@keyframes sla-dino-cactus{from{transform:translateX(0)}to{transform:translateX(-18em)}}" +
    "@keyframes sla-dino-ground{from{background-position:0 0}to{background-position:-2em 0}}" +
    "/* ── 水波涟漪 ──\n三圈同心环从中心向外扩散，错峰 1/3 周期。环本身不移动，只做 scale + 透明度，" +
    "所以不会像旋转类动画那样抢注意力 —— 适合\"等一个后台任务\"这种长等待。 */" +
    ".sla-ripple{width:var(--sla-s,64px);height:var(--sla-s,64px)}" +
    ".sla-ripple i{position:absolute;inset:0;margin:auto;width:100%;height:100%;border:calc(var(--sla-s,64px)*0.045) solid currentColor;border-radius:50%;opacity:0;transform:scale(.18);animation:sla-ripple calc(2.4s / var(--sla-rate,1)) cubic-bezier(.2,.6,.3,1) infinite}" +
    ".sla-ripple i:nth-child(2){animation-delay:calc(.8s / var(--sla-rate,1))}" +
    ".sla-ripple i:nth-child(3){animation-delay:calc(1.6s / var(--sla-rate,1))}" +
    "@keyframes sla-ripple{0%{transform:scale(.18);opacity:0}12%{opacity:.9}70%{opacity:.24}100%{transform:scale(1);opacity:0}}" +
    "/* ── 双轨环绕 ──\n一颗卫星沿外圈顺时针、一颗沿内圈逆时针；轨道本身只画一圈淡色参考线。" +
    "卫星用 ::before 挂在轨道顶端，rotate 整条轨道即等于卫星绕圈。 */" +
    ".sla-orbit{width:var(--sla-s,64px);height:var(--sla-s,64px)}" +
    ".sla-orbit::before{content:'';position:absolute;inset:0;border:1px solid color-mix(in srgb,var(--sla-c,#406eeb) 18%,transparent);border-radius:50%}" +
    ".sla-orbit i{position:absolute;inset:0;display:block;border-radius:50%;animation:sla-orbit calc(2.6s / var(--sla-rate,1)) linear infinite}" +
    ".sla-orbit i::before{content:'';position:absolute;top:0;left:50%;width:calc(var(--sla-s,64px)*0.16);height:calc(var(--sla-s,64px)*0.16);margin-left:calc(var(--sla-s,64px)*-0.08);margin-top:calc(var(--sla-s,64px)*-0.08);border-radius:50%;background:currentColor}" +
    ".sla-orbit i:nth-child(2){inset:18%;border:1px solid color-mix(in srgb,var(--sla-c,#406eeb) 12%,transparent);animation-direction:reverse;animation-duration:calc(1.8s / var(--sla-rate,1))}" +
    ".sla-orbit i:nth-child(2)::before{width:calc(var(--sla-s,64px)*0.11);height:calc(var(--sla-s,64px)*0.11);margin-left:calc(var(--sla-s,64px)*-0.055);margin-top:calc(var(--sla-s,64px)*-0.055);background:color-mix(in srgb,var(--sla-c,#406eeb) 55%,white)}" +
    "@keyframes sla-orbit{from{transform:rotate(0)}to{transform:rotate(360deg)}}" +
    "/* ── 音量柱 ──\n四根竖柱各自伸缩，错峰 0.14~0.42s；错峰刻意不等距，" +
    "等距会变成整齐的\"波浪\"，反而不像音量。 */" +
    ".sla-bars{width:calc(var(--sla-s,64px)*1.2);height:var(--sla-s,64px);display:flex;align-items:flex-end;justify-content:space-between;padding:0 calc(var(--sla-s,64px)*0.06);box-sizing:border-box}" +
    ".sla-bars i{display:block;width:14%;height:100%;border-radius:999px;background:currentColor;transform-origin:50% 100%;transform:scaleY(.25);animation:sla-bars calc(1.1s / var(--sla-rate,1)) ease-in-out infinite alternate}" +
    ".sla-bars i:nth-child(2){animation-delay:calc(.28s / var(--sla-rate,1))}" +
    ".sla-bars i:nth-child(3){animation-delay:calc(.14s / var(--sla-rate,1))}" +
    ".sla-bars i:nth-child(4){animation-delay:calc(.42s / var(--sla-rate,1))}" +
    "@keyframes sla-bars{from{transform:scaleY(.25)}to{transform:scaleY(1)}}" +
    "/* ── hairline 矢量层 ──\n四款（点阵 / 传送带 / 柱阵 / 卡片翻动）在应用内由 at lucasmarkes/hairline 真画：" +
    "上面那几段 CSS 只作为首屏 / 库未就位时的近似图。.has-hl 时隐藏近似层，只留图形本身；" +
    "颜色与尺寸仍走 --sla-c / --sla-s，与其它动画同一口径。 */" +
    ".sla.has-hl{display:flex;align-items:center;justify-content:center;color:var(--sla-c,#406eeb)}" +
    ".sla.has-hl > *:not(.sla-hl){display:none!important}" +
    ".sla .sla-hl{position:relative;display:flex;align-items:center;justify-content:center;width:var(--sla-s,64px);height:var(--sla-s,64px);line-height:0}" +
    "/* ── 点阵 ──\n格子由 repeating-conic-gradient 画出（每格中心一个小方块），一条宽的渐变扫描带" +
    "自上而下掠过；扫描带用 mask 复用同一张格子图，于是只有点上会亮。 */" +
    ".sla-matrix{width:var(--sla-s,64px);height:var(--sla-s,64px);overflow:hidden}" +
    ".sla-matrix::before{content:'';position:absolute;inset:0;background-image:repeating-conic-gradient(currentColor 0 25%,transparent 0 100%);background-size:calc(100% / 7) calc(100% / 7);opacity:.28}" +
    ".sla-matrix i{position:absolute;inset:-20% 0;display:block;background:linear-gradient(to bottom,transparent 0,currentColor 38%,currentColor 62%,transparent 100%);-webkit-mask-image:repeating-conic-gradient(#000 0 25%,transparent 0 100%);mask-image:repeating-conic-gradient(#000 0 25%,transparent 0 100%);-webkit-mask-size:calc(100% / 7) calc(100% / 7);mask-size:calc(100% / 7) calc(100% / 7);animation:sla-matrix calc(1.8s / var(--sla-rate,1)) linear infinite}" +
    "at keyframes sla-matrix{from{transform:translateY(-60%)}to{transform:translateY(60%)}}" +
    "/* ── 传送带 ──\n两条皮带用斜纹渐变平移，板条箱夹在中间轻微上抬 —— 一眼能看出东西在往前送。 */" +
    ".sla-conveyor{width:calc(var(--sla-s,64px)*1.4);height:var(--sla-s,64px);font-size:calc(var(--sla-s,64px)*0.06)}" +
    ".sla-conveyor__belt{position:absolute;left:0;right:0;height:.2em;background:repeating-linear-gradient(90deg,currentColor 0 .18em,color-mix(in srgb,var(--sla-c,#406eeb) 22%,transparent) .18em .4em);background-size:.88em 100%;animation:sla-conveyor calc(.9s / var(--sla-rate,1)) linear infinite}" +
    ".sla-conveyor__belt--top{top:20%}" +
    ".sla-conveyor__belt--bottom{bottom:20%}" +
    ".sla-conveyor__crate{position:absolute;top:50%;left:50%;width:calc(var(--sla-s,64px)*0.36);height:calc(var(--sla-s,64px)*0.36);margin:calc(var(--sla-s,64px)*-0.18);border:.16em solid currentColor;background:color-mix(in srgb,var(--sla-c,#406eeb) 16%,transparent);box-sizing:border-box;animation:sla-conveyor-crate calc(1.6s / var(--sla-rate,1)) ease-in-out infinite}" +
    "at keyframes sla-conveyor{from{background-position:0 0}to{background-position:.88em 0}}" +
    "at keyframes sla-conveyor-crate{0%,100%{transform:translateY(0) rotate(0)}50%{transform:translateY(-.3em) rotate(-6deg)}}" +
    "/* ── 柱阵 ──\n三排五列的小柱，按列序错峰 0.08s，于是看到的是斜向推进的波而不是整排一起跳。 */" +
    ".sla-pillars{width:calc(var(--sla-s,64px)*1.2);height:var(--sla-s,64px);display:grid;grid-template-columns:repeat(5,1fr);grid-template-rows:repeat(3,1fr);align-items:end;gap:calc(var(--sla-s,64px)*0.07)}" +
    ".sla-pillars i{display:block;height:100%;border-radius:calc(var(--sla-s,64px)*0.05);background:currentColor;transform-origin:50% 100%;transform:scaleY(.3);animation:sla-pillars calc(1.4s / var(--sla-rate,1)) ease-in-out infinite}" +
    ".sla-pillars i:nth-child(5n+2){animation-delay:calc(.08s / var(--sla-rate,1))}" +
    ".sla-pillars i:nth-child(5n+3){animation-delay:calc(.16s / var(--sla-rate,1))}" +
    ".sla-pillars i:nth-child(5n+4){animation-delay:calc(.24s / var(--sla-rate,1))}" +
    ".sla-pillars i:nth-child(5n+5){animation-delay:calc(.32s / var(--sla-rate,1))}" +
    "at keyframes sla-pillars{0%,100%{transform:scaleY(.3);opacity:.45}50%{transform:scaleY(1);opacity:1}}" +
    "/* ── 卡片翻动 ──\n三张卡并排绕 Y 轴依次翻面；翻到侧面时透明度降下来，像纸片薄下去。 */" +
    ".sla-cards{width:calc(var(--sla-s,64px)*1.5);height:var(--sla-s,64px);display:flex;align-items:center;justify-content:space-between;perspective:calc(var(--sla-s,64px)*3)}" +
    ".sla-cards i{display:block;width:calc(var(--sla-s,64px)*0.38);height:calc(var(--sla-s,64px)*0.62);border-radius:calc(var(--sla-s,64px)*0.05);border:calc(var(--sla-s,64px)*0.045) solid currentColor;background:color-mix(in srgb,var(--sla-c,#406eeb) 12%,transparent);box-sizing:border-box;animation:sla-cards calc(1.5s / var(--sla-rate,1)) ease-in-out infinite}" +
    ".sla-cards i:nth-child(2){animation-delay:calc(.15s / var(--sla-rate,1))}" +
    ".sla-cards i:nth-child(3){animation-delay:calc(.3s / var(--sla-rate,1))}" +
    "at keyframes sla-cards{0%,100%{transform:rotateY(0);opacity:1}45%{transform:rotateY(180deg)}55%{opacity:.55}100%{opacity:1}}" +
    "/* ── 无动画 ── */\n.sla-none{width:1px;height:1px;opacity:0}" +
    "/* 减少动态效果：整体放慢但不停 —— 加载指示器停住会让人以为卡死 */\n@media (prefers-reduced-motion:reduce){ .sla-dots i{animation-duration:calc(2.4s / var(--sla-rate,1))} .sla-rings i:nth-child(1){animation-duration:calc(2.8s / var(--sla-rate,1))} .sla-rings i:nth-child(2){animation-duration:calc(2s / var(--sla-rate,1))} .sla-dino__runner{animation-duration:calc(1.9s / var(--sla-rate,1))} .sla-dino__cactus{animation-duration:calc(2.6s / var(--sla-rate,1))} .sla-dino__ground{animation-duration:calc(2.6s / var(--sla-rate,1))} .sla-ripple i{animation-duration:calc(3.8s / var(--sla-rate,1))} .sla-orbit i{animation-duration:calc(4.2s / var(--sla-rate,1))} .sla-bars i{animation-duration:calc(1.8s / var(--sla-rate,1))} .sla-matrix i{animation-duration:calc(3.2s / var(--sla-rate,1))} .sla-conveyor__belt{animation-duration:calc(1.8s / var(--sla-rate,1))} .sla-conveyor__crate{animation-duration:calc(2.4s / var(--sla-rate,1))} .sla-pillars i{animation-duration:calc(2.4s / var(--sla-rate,1))} .sla-cards i{animation-duration:calc(2.6s / var(--sla-rate,1))} }";


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
        { key: "dots", name: "三个圆点", description: "", previewScale: 1, html: ANIMATION_HTML.dots, css: "" },
        { key: "rings", name: "科技圆圈", description: "", previewScale: 1, html: ANIMATION_HTML.rings, css: "" },
        { key: "dino", name: "像素恐龙", description: "", previewScale: 1, html: ANIMATION_HTML.dino, css: "" },
        { key: "ripple", name: "水波涟漪", description: "", previewScale: 1, html: ANIMATION_HTML.ripple, css: "" },
        { key: "orbit", name: "双轨环绕", description: "", previewScale: 1, html: ANIMATION_HTML.orbit, css: "" },
        { key: "bars", name: "音量柱", description: "", previewScale: 1, html: ANIMATION_HTML.bars, css: "" },
        { key: "matrix", name: "点阵", description: "", previewScale: 1, html: ANIMATION_HTML.matrix, css: "" },
        { key: "conveyor", name: "传送带", description: "", previewScale: 1, html: ANIMATION_HTML.conveyor, css: "" },
        { key: "pillars", name: "柱阵", description: "", previewScale: 1, html: ANIMATION_HTML.pillars, css: "" },
        { key: "cards", name: "卡片翻动", description: "", previewScale: 1, html: ANIMATION_HTML.cards, css: "" },
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

  /*
   * 首屏四款矢量动画：hairline-loader.js 是随前端一起发布的自带副本，
   * 不依赖 CDN、不依赖 Vue。CSS 近似图先出，内置 JS 加载完成后替换为真实 SVG；
   * JS 文件缺失或执行失败时保留 CSS，不让首屏白屏。
   */
  var LOADER_HAIRLINE_FIGURES = {
    matrix: "phosphor",
    conveyor: "slow",
    pillars: "terrain",
    cards: "riffle",
  };

  function loadBundledHairline(loaderKey) {
    var figure = LOADER_HAIRLINE_FIGURES[loaderKey];
    if (!figure || !shouldMountLoader) {
      return;
    }
    mountWhenBodyReady(function () {
      var host = document.querySelector(
        "#" + LOADER_CONTAINER_ID + " .sys-loader-shell .sla-" + loaderKey,
      );
      if (!host) {
        return;
      }
      var shell = host.parentNode;
      if (!shell || shell.getAttribute("data-hairline-loading") === "true") {
        return;
      }
      shell.setAttribute("data-hairline-loading", "true");
      var script = document.createElement("script");
      script.src = "/hairline-loader.js";
      script.async = true;
      script.onload = function () {
        try {
          var mount = window.__SYS_HAIRLINE__ && window.__SYS_HAIRLINE__.mount;
          if (!mount) return;
          var vector = document.createElement("div");
          vector.className = "sla-hl";
          vector.style.cssText = "position:absolute;inset:0;width:var(--sla-s,64px);height:var(--sla-s,64px);margin:auto;line-height:0";
          host.appendChild(vector);
          var made = mount(vector, figure, { play: true });
          if (!made) {
            vector.remove();
            return;
          }
          host.classList.add("has-hl");
        } catch (error) {
          /* 自带副本异常时继续显示 CSS 近似图 */
        }
      };
      script.onerror = function () {
        /* 文件缺失时继续显示 CSS 近似图 */
      };
      (document.head || document.documentElement).appendChild(script);
    });
  }

  /* 读取动画：部署侧锁定值 > 用户选择（带 appCode 的键 > 全局键）> 默认三点 */
  function resolveLoaderType() {
    var deployed = normalizeKey(readDeployKey(LOADER_DEPLOY_ANIMATION_KEY));
    if (deployed) {
      return deployed;
    }
    var scoped = normalizeKey(localStorage.getItem(resolveLoaderStorageKey()));
    if (scoped) {
      return scoped;
    }
    var globalKey = normalizeKey(localStorage.getItem(GLOBAL_LOADER_STORAGE_KEY));
    return globalKey || "dots";
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
    loaderMap.dots ||
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
  loadBundledHairline(loaderType);

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
