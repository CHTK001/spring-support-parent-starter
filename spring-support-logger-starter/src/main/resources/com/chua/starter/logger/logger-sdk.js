/**
 * logger-browser-sdk（零依赖 UMD）：浏览器端日志采集与推送。
 *
 * 用法：
 *   <script src="/v2/logger/sdk.js"></script>
 *   <script>
 *     LoggerJS.init({
 *       app: 'my-web-app',        // 应用名，缺省取 location.hostname
 *       level: 'info',            // console 采集下限：debug|info|warn|error
 *       console: true,            // 劫持 console（默认 true）
 *       errors: true,             // 捕获 window.onerror / unhandledrejection（默认 true）
 *       network: true,            // 采集 fetch / XHR 网络失败：HTTP >= 400 或连接失败（默认 true）
 *       resources: true,          // 采集资源加载错误：script / img / link 等（默认 true）
 *       trace: true,              // 跟随 x-trace-id：请求自动带头、响应自动采纳（默认 true）
 *       sampleRate: 1,            // 采样率 0~1，1 = 全量（默认 1）
 *       ignoreErrors: [],         // 忽略的错误消息：字符串（包含匹配）或正则
 *       beforeSend: null,         // (event) => event | false：返回 false 丢弃该事件
 *       sessionId: ''             // 会话标识，缺省自动生成并写入 sessionStorage
 *     });
 *     LoggerJS.info('页面已就绪');
 *     LoggerJS.error('下单失败', { orderId: 123 });
 *   </script>
 *
 * 推送契约：POST /v2/logger/ingest，体为 {"events":[LoggerEvent...]}；
 * 事件缺省补齐 ts/level/app/instance/traceId（traceId 回落请求头 x-trace-id），
 * 并自动附带 mdc.page（路径 + 查询串）与 mdc.sessionId（会话标识）。
 */
(function (global) {
  'use strict';

  var LEVEL_WEIGHT = { TRACE: 0, DEBUG: 1, INFO: 2, WARN: 3, ERROR: 4 };
  var CONSOLE_LEVEL = { error: 'ERROR', warn: 'WARN', info: 'INFO', log: 'INFO', debug: 'DEBUG' };

  var config = {
    endpoint: '/v2/logger/ingest',
    app: '',
    instance: 'browser',
    level: 'INFO',
    console: true,
    errors: true,
    network: true,
    resources: true,
    trace: true,
    sampleRate: 1,
    ignoreErrors: [],
    beforeSend: null,
    sessionId: '',
    flushInterval: 3000,
    maxBatch: 20,
    token: '',
    headers: null
  };

  var queue = [];
  var timer = null;
  var flushing = false;
  var installed = false;
  var traceId = newId();
  var originalConsole = {};

  function newId() {
    var hex = '0123456789abcdef';
    var out = '';
    for (var i = 0; i < 32; i++) {
      out += hex.charAt(Math.floor(Math.random() * 16));
    }
    return out;
  }

  function minWeight() {
    var lv = String(config.level || 'INFO').toUpperCase();
    return LEVEL_WEIGHT.hasOwnProperty(lv) ? LEVEL_WEIGHT[lv] : LEVEL_WEIGHT.INFO;
  }

  function format(args) {
    var parts = [];
    for (var i = 0; i < args.length; i++) {
      var arg = args[i];
      try {
        if (arg instanceof Error) {
          parts.push(arg.stack || (arg.name + ': ' + arg.message));
        } else if (typeof arg === 'object' && arg !== null) {
          parts.push(JSON.stringify(arg));
        } else {
          parts.push(String(arg));
        }
      } catch (e) {
        parts.push('[unformattable]');
      }
    }
    return parts.join(' ');
  }

  /** 当前页面标识（路径 + 查询串），自动附到 mdc.page */
  function currentPage() {
    try {
      return global.location ? global.location.pathname + global.location.search : '';
    } catch (e) {
      return '';
    }
  }

  /** 会话标识：优先用配置值，否则读写 sessionStorage（不可用时退化为每次新生成） */
  function ensureSessionId() {
    if (config.sessionId) {
      return;
    }
    var key = 'logger-session-id';
    try {
      var stored = global.sessionStorage ? global.sessionStorage.getItem(key) : null;
      config.sessionId = stored || newId();
      if (!stored && global.sessionStorage) {
        global.sessionStorage.setItem(key, config.sessionId);
      }
    } catch (e) {
      config.sessionId = newId();
    }
  }

  /** 采样：sampleRate = 1 全量；0~1 之间按比例随机放行 */
  function sampled() {
    var rate = Number(config.sampleRate);
    if (!isFinite(rate) || rate >= 1) {
      return true;
    }
    return rate > 0 && Math.random() < rate;
  }

  /** 错误消息是否命中忽略清单（字符串包含匹配 / 正则） */
  function isIgnored(message) {
    var list = config.ignoreErrors;
    if (!list || !list.length) {
      return false;
    }
    for (var i = 0; i < list.length; i++) {
      var item = list[i];
      try {
        if (item instanceof RegExp) {
          if (item.test(message)) {
            return true;
          }
        } else if (item && message.indexOf(String(item)) !== -1) {
          return true;
        }
      } catch (e) {
        /* 忽略非法条目 */
      }
    }
    return false;
  }

  function push(event) {
    try {
      if (!event || typeof event !== 'object') {
        return;
      }
      if (!sampled()) {
        return;
      }
      var message = event.message == null ? '' : String(event.message);
      if (isIgnored(message)) {
        return;
      }
      if (typeof config.beforeSend === 'function') {
        var processed = config.beforeSend(event);
        if (!processed) {
          return;
        }
        if (typeof processed === 'object') {
          event = processed;
          message = event.message == null ? '' : String(event.message);
        }
      }
      var row = {
        ts: event.ts || Date.now(),
        app: event.app || config.app,
        instance: event.instance || config.instance,
        level: String(event.level || 'INFO').toUpperCase(),
        logger: event.logger || 'browser',
        thread: 'main',
        message: message,
        traceId: event.traceId || traceId
      };
      if (event.stacktrace) {
        row.stacktrace = String(event.stacktrace);
      }
      var mdc = {};
      if (config.sessionId) {
        mdc.sessionId = config.sessionId;
      }
      var page = currentPage();
      if (page) {
        mdc.page = page;
      }
      if (event.mdc) {
        for (var key in event.mdc) {
          if (Object.prototype.hasOwnProperty.call(event.mdc, key)) {
            mdc[key] = event.mdc[key];
          }
        }
      }
      if (Object.keys(mdc).length > 0) {
        row.mdc = mdc;
      }
      queue.push(row);
      if (queue.length >= config.maxBatch) {
        flush();
      }
    } catch (e) {
      /* 采集失败不影响宿主页面 */
    }
  }

  function log(level) {
    var args = Array.prototype.slice.call(arguments, 1);
    push({ level: level, message: format(args), logger: 'browser' });
  }

  function send(body, useBeacon) {
    var headers = { 'Content-Type': 'application/json', 'x-logger-app': config.app };
    if (config.trace && traceId) {
      headers['x-trace-id'] = traceId;
    }
    if (config.token) {
      headers['x-logger-token'] = config.token;
    }
    if (config.headers) {
      for (var key in config.headers) {
        if (Object.prototype.hasOwnProperty.call(config.headers, key)) {
          headers[key] = config.headers[key];
        }
      }
    }
    if (useBeacon && global.navigator && typeof global.navigator.sendBeacon === 'function') {
      try {
        var blob = new Blob([body], { type: 'application/json' });
        if (global.navigator.sendBeacon(config.endpoint, blob)) {
          return;
        }
      } catch (e) {
        /* 回落 fetch */
      }
    }
    fetch(config.endpoint, { method: 'POST', headers: headers, body: body, keepalive: useBeacon })
      .then(function (res) {
        // 服务端返回的 x-trace-id 优先（与后端 MDC 链路对齐）
        try {
          var serverTrace = res.headers.get('x-trace-id');
          if (serverTrace) {
            traceId = serverTrace;
          }
        } catch (e) {
          /* 忽略 */
        }
      })
      .catch(function () {
        /* 推送失败静默：日志通道不得影响业务 */
      });
  }

  function flush(useBeacon) {
    if (flushing || queue.length === 0) {
      return;
    }
    var batch = queue.splice(0, queue.length);
    try {
      flushing = true;
      send(JSON.stringify({ events: batch }), useBeacon === true);
    } catch (e) {
      /* 忽略 */
    } finally {
      flushing = false;
    }
  }

  function schedule() {
    if (timer || config.flushInterval <= 0) {
      return;
    }
    timer = setInterval(function () {
      flush(false);
    }, config.flushInterval);
  }

  function installConsole() {
    if (!global.console) {
      return;
    }
    var floor = minWeight();
    ['error', 'warn', 'info', 'log', 'debug'].forEach(function (method) {
      originalConsole[method] = global.console[method];
      global.console[method] = function () {
        var args = Array.prototype.slice.call(arguments);
        try {
          var level = CONSOLE_LEVEL[method];
          if (LEVEL_WEIGHT[level] >= floor) {
            push({ level: level, message: format(args), logger: 'console' });
          }
        } catch (e) {
          /* 采集失败不影响原 console */
        }
        try {
          originalConsole[method].apply(global.console, args);
        } catch (e) {
          /* 忽略 */
        }
      };
    });
  }

  function installErrors() {
    // capture=true：资源加载错误（img/script/link）不冒泡，只能在捕获阶段在 window 上收到
    global.addEventListener('error', function (event) {
      try {
        var target = event && event.target;
        if (config.resources && target && target !== global && (target.src || target.href)) {
          push({
            level: 'ERROR',
            logger: 'resource',
            message: '资源加载失败: ' + (target.tagName || 'resource') + ' ' + (target.src || target.href)
          });
          return;
        }
        if (!config.errors) {
          return;
        }
        var message = event && event.message ? event.message : 'script error';
        push({
          level: 'ERROR',
          logger: 'window.onerror',
          message: message,
          stacktrace: event && event.error && event.error.stack ? event.error.stack : ''
        });
      } catch (e) {
        /* 忽略 */
      }
    }, true);
    global.addEventListener('unhandledrejection', function (event) {
      try {
        var reason = event && event.reason ? event.reason : 'unhandled rejection';
        push({
          level: 'ERROR',
          logger: 'unhandledrejection',
          message: reason instanceof Error ? reason.message : String(reason),
          stacktrace: reason instanceof Error && reason.stack ? reason.stack : ''
        });
      } catch (e) {
        /* 忽略 */
      }
    });
  }

  /** fetch / XHR 统一包装：trace 头注入 + 服务端 trace 采纳 + 网络失败采集 */
  function installHttp() {
    if (typeof global.fetch === 'function') {
      var originalFetch = global.fetch;
      global.fetch = function () {
        var args = Array.prototype.slice.call(arguments);
        var url = '';
        var method = 'GET';
        try {
          var input = args[0];
          url = typeof input === 'string' ? input : (input && input.url) || '';
          var init = args[1] || {};
          method = String(init.method || (input && input.method) || 'GET').toUpperCase();
          if (config.trace && url.indexOf(config.endpoint) === -1) {
            var headers = new Headers(init.headers || (input && input.headers) || {});
            if (!headers.has('x-trace-id')) {
              headers.set('x-trace-id', traceId);
            }
            init.headers = headers;
            args[1] = init;
          }
        } catch (e) {
          /* 头注入失败按原样请求 */
        }
        var promise = originalFetch.apply(this, args);
        if (config.network && url && url.indexOf(config.endpoint) === -1) {
          try {
            promise.then(function (res) {
              try {
                var status = res && res.status;
                if (status >= 400) {
                  push({
                    level: 'ERROR',
                    logger: 'network.fetch',
                    message: method + ' ' + url + ' -> HTTP ' + status
                  });
                }
              } catch (e) {
                /* 忽略 */
              }
            }).catch(function () {
              push({ level: 'ERROR', logger: 'network.fetch', message: method + ' ' + url + ' -> 网络失败' });
            });
          } catch (e) {
            /* 忽略 */
          }
        }
        try {
          promise.then(function (res) {
            var serverTrace = res.headers && res.headers.get ? res.headers.get('x-trace-id') : null;
            if (serverTrace) {
              traceId = serverTrace;
            }
          }).catch(function () { /* 忽略 */ });
        } catch (e) {
          /* 忽略 */
        }
        return promise;
      };
    }
    if (typeof global.XMLHttpRequest === 'function') {
      var originalOpen = global.XMLHttpRequest.prototype.open;
      var originalSend = global.XMLHttpRequest.prototype.send;
      global.XMLHttpRequest.prototype.open = function (method, url) {
        try {
          this.__loggerUrl = String(url || '');
          this.__loggerMethod = String(method || 'GET').toUpperCase();
        } catch (e) {
          /* 忽略 */
        }
        return originalOpen.apply(this, arguments);
      };
      global.XMLHttpRequest.prototype.send = function () {
        var xhr = this;
        try {
          if (String(xhr.__loggerUrl || '').indexOf(config.endpoint) === -1) {
            xhr.addEventListener('load', function () {
              try {
                var serverTrace = xhr.getResponseHeader && xhr.getResponseHeader('x-trace-id');
                if (serverTrace) {
                  traceId = serverTrace;
                }
              } catch (e) {
                /* 忽略 */
              }
            });
            if (config.network) {
              xhr.addEventListener('loadend', function () {
                try {
                  var status = xhr.status;
                  if (status === 0 || status >= 400) {
                    push({
                      level: 'ERROR',
                      logger: 'network.xhr',
                      message: (xhr.__loggerMethod || 'GET') + ' ' + (xhr.__loggerUrl || '') + ' -> '
                        + (status === 0 ? '网络失败' : 'HTTP ' + status)
                    });
                  }
                } catch (e) {
                  /* 忽略 */
                }
              });
            }
            if (config.trace && xhr.setRequestHeader) {
              xhr.setRequestHeader('x-trace-id', traceId);
            }
          }
        } catch (e) {
          /* 忽略 */
        }
        return originalSend.apply(this, arguments);
      };
    }
  }

  global.LoggerJS = {
    init: function (options) {
      try {
        options = options || {};
        for (var key in options) {
          if (Object.prototype.hasOwnProperty.call(options, key)) {
            config[key] = options[key];
          }
        }
        if (!config.app) {
          config.app = global.location ? global.location.hostname : 'browser';
        }
        ensureSessionId();
        if (config.console && !installed) {
          installConsole();
        }
        if ((config.errors || config.resources) && !installed) {
          installErrors();
        }
        if ((config.trace || config.network) && !installed) {
          installHttp();
        }
        installed = true;
        schedule();
        global.addEventListener('pagehide', function () {
          flush(true);
        });
        global.addEventListener('visibilitychange', function () {
          if (global.document && global.document.visibilityState === 'hidden') {
            flush(true);
          }
        });
      } catch (e) {
        /* 初始化失败不影响宿主页面 */
      }
    },
    push: push,
    log: function () { log.apply(null, ['INFO'].concat(Array.prototype.slice.call(arguments))); },
    info: function () { log.apply(null, ['INFO'].concat(Array.prototype.slice.call(arguments))); },
    warn: function () { log.apply(null, ['WARN'].concat(Array.prototype.slice.call(arguments))); },
    error: function () { log.apply(null, ['ERROR'].concat(Array.prototype.slice.call(arguments))); },
    debug: function () { log.apply(null, ['DEBUG'].concat(Array.prototype.slice.call(arguments))); },
    setTraceId: function (id) { if (id) { traceId = String(id); } },
    getTraceId: function () { return traceId; },
    getSessionId: function () { return config.sessionId; },
    flush: function () { flush(false); }
  };
})(typeof window !== 'undefined' ? window : this);
