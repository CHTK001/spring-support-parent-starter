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
 *       trace: true               // 跟随 x-trace-id：请求自动带头、响应自动采纳（默认 true）
 *     });
 *     LoggerJS.info('页面已就绪');
 *     LoggerJS.error('下单失败', { orderId: 123 });
 *   </script>
 *
 * 推送契约：POST /v2/logger/ingest，体为 {"events":[LoggerEvent...]}；
 * 事件缺省补齐 ts/level/app/instance/traceId（traceId 回落请求头 x-trace-id）。
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
    trace: true,
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

  function push(event) {
    try {
      if (!event || typeof event !== 'object') {
        return;
      }
      var row = {
        ts: event.ts || Date.now(),
        app: event.app || config.app,
        instance: event.instance || config.instance,
        level: String(event.level || 'INFO').toUpperCase(),
        logger: event.logger || 'browser',
        thread: 'main',
        message: event.message == null ? '' : String(event.message),
        traceId: event.traceId || traceId
      };
      if (event.stacktrace) {
        row.stacktrace = String(event.stacktrace);
      }
      if (event.mdc) {
        row.mdc = event.mdc;
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
    global.addEventListener('error', function (event) {
      try {
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
    });
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

  function installTrace() {
    // 出站请求自动带 x-trace-id；响应回来采纳服务端回写的 x-trace-id
    if (typeof global.fetch === 'function') {
      var originalFetch = global.fetch;
      global.fetch = function () {
        var args = Array.prototype.slice.call(arguments);
        try {
          var input = args[0];
          var url = typeof input === 'string' ? input : (input && input.url) || '';
          if (url.indexOf(config.endpoint) === -1) {
            var init = args[1] || {};
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
            if (xhr.setRequestHeader) {
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
        if (config.console && !installed) {
          installConsole();
        }
        if (config.errors && !installed) {
          installErrors();
        }
        if (config.trace && !installed) {
          installTrace();
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
    flush: function () { flush(false); }
  };
})(typeof window !== 'undefined' ? window : this);
