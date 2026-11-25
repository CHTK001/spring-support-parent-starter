'use strict';
/**
 * npm 插件 Node 侧车宿主（host.js）
 *
 * <p>由 Java 端 {@code NpmPluginHostService} 以子进程方式拉起，通过 stdio 上的
 * JSON-lines 协议通信。每个会话流（streamId）在 begin 时按角色配置装载一组
 * npm 中间件插件（导出 {@code onEvent(ev, ctx)} / {@code onResponse(body, ctx)}），
 * 后续流事件逐帧交给插件链处理。</p>
 *
 * <p>请求：{@code {id, type: "begin"|"event"|"response"|"end"|"ping", streamId, plugins, event, body}}
 * <br>响应：{@code {id, ok, loaded?, errors?, event?, body?, error?}}</p>
 *
 * <p>插件约定（与 @magpie-community/middleware-think-tags 一致）：
 * {@code onEvent(ev, ctx)} 返回修改后的事件或 undefined（原样透传）；
 * {@code onResponse(body, ctx)} 同理，{@code ctx.status >= 400} 时由插件自行跳过；
 * {@code ctx.state} 在单个流内跨帧持久化；{@code ctx.options} 为角色配置的插件参数。</p>
 *
 * <p>空闲自退：持续无消息超过 NPM_HOST_IDLE_MS（默认 5 分钟）后进程退出，
 * 由 Java 端在下次调用时重新拉起。</p>
 */
const path = require('path');
const fs = require('fs');
const readline = require('readline');

const PLUGIN_DIR = process.env.NPM_PLUGIN_DIR || path.join(process.cwd(), 'npm-plugins');
const IDLE_MS = Number(process.env.NPM_HOST_IDLE_MS || 300000) || 300000;

/** streamId → { plugins: [{name, module, ctx}] } */
const streams = new Map();
let idleTimer = null;

function touchIdle() {
  if (idleTimer) clearTimeout(idleTimer);
  idleTimer = setTimeout(() => process.exit(0), IDLE_MS);
}
touchIdle();

function send(obj) {
  try {
    process.stdout.write(JSON.stringify(obj) + '\n');
  } catch (e) {
    // 输出通道断裂：由 Java 端读者线程感知 EOF 并处理
  }
}

function errText(e) {
  return e && e.message ? e.message : String(e);
}

function logErr(where, name, e) {
  try {
    process.stderr.write('[npm-host] ' + where + ' ' + name + ' 失败: ' + errText(e) + '\n');
  } catch (_) {
    // ignore
  }
}

function resolveModule(name) {
  const dir = path.join(PLUGIN_DIR, 'node_modules', name);
  try {
    return normalizeModule(require(dir));
  } catch (directError) {
    const mod = loadByEntryFields(dir, name);
    if (mod !== null) return mod;
    throw directError;
  }
}

/**
 * 按包元数据解析入口：支持 magpie.middleware（样板插件约定）、main、exports、index.*；
 * 兼容 ESM 包（Node >= 22.12 的 require(esm) 同步加载，要求依赖图无顶层 await）。
 */
function loadByEntryFields(dir, name) {
  let pkg = null;
  try {
    pkg = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'));
  } catch (e) {
    return null;
  }
  const candidates = [];
  const push = (v) => {
    if (typeof v === 'string' && v && candidates.indexOf(v) < 0) candidates.push(v);
  };
  if (pkg.magpie) push(pkg.magpie.middleware || pkg.magpie.entry);
  push(pkg.main);
  const ex = pkg.exports;
  if (typeof ex === 'string') push(ex);
  else if (ex && typeof ex === 'object') {
    const dot = ex['.'] || ex['./index.js'];
    if (typeof dot === 'string') push(dot);
    else if (dot && typeof dot === 'object') ['import', 'require', 'default'].forEach((k) => push(dot[k]));
  }
  ['index.js', 'index.mjs', 'index.cjs'].forEach(push);
  for (const rel of candidates) {
    try {
      const mod = normalizeModule(require(path.join(dir, rel)));
      if (mod) return mod;
    } catch (e) {
      // 换下一个候选入口
    }
  }
  return null;
}

/** ESM 命名空间与 default 导出的互操作归一 */
function normalizeModule(mod) {
  if (!mod) return mod;
  if (typeof mod.onEvent === 'function' || typeof mod.onResponse === 'function') return mod;
  const d = mod.default;
  if (d && (typeof d.onEvent === 'function' || typeof d.onResponse === 'function')) return d;
  return mod;
}

function handleMessage(msg) {
  const id = msg.id;
  const type = msg.type;
  const streamId = msg.streamId;
  try {
    if (type === 'ping') {
      send({ id, ok: true, pong: true, node: process.version, streams: streams.size });
      return;
    }
    if (type === 'begin') {
      const plugins = [];
      const errors = [];
      for (const p of (msg.plugins || [])) {
        try {
          const module = resolveModule(p.name);
          if (typeof module.onEvent !== 'function' && typeof module.onResponse !== 'function') {
            errors.push('插件 ' + p.name + ' 未导出 onEvent/onResponse，已跳过');
            continue;
          }
          plugins.push({ name: p.name, module, ctx: { state: {}, options: p.options || {}, status: 200 } });
        } catch (e) {
          errors.push('插件 ' + p.name + ' 加载失败: ' + errText(e));
        }
      }
      if (plugins.length > 0) {
        streams.set(streamId, { plugins });
      }
      send({ id, ok: true, loaded: plugins.length, errors });
      return;
    }
    if (type === 'end') {
      if (streams.has(streamId)) {
        streams.delete(streamId);
      }
      send({ id, ok: true });
      return;
    }
    const stream = streams.get(streamId);
    if (!stream) {
      // 流状态不存在（宿主重启/已结束）：原样透传，不阻断对话
      send({ id, ok: true, event: msg.event, body: msg.body });
      return;
    }
    if (type === 'event') {
      let ev = msg.event;
      for (const p of stream.plugins) {
        if (typeof p.module.onEvent !== 'function') continue;
        try {
          const out = p.module.onEvent(ev, p.ctx);
          if (out !== undefined && out !== null) ev = out;
        } catch (e) {
          logErr('onEvent', p.name, e);
        }
      }
      send({ id, ok: true, event: ev });
      return;
    }
    if (type === 'response') {
      let body = msg.body;
      for (const p of stream.plugins) {
        if (typeof p.module.onResponse !== 'function') continue;
        try {
          p.ctx.status = body && typeof body.status === 'number' ? body.status : 200;
          const out = p.module.onResponse(body, p.ctx);
          if (out !== undefined && out !== null) body = out;
        } catch (e) {
          logErr('onResponse', p.name, e);
        }
      }
      send({ id, ok: true, body });
      return;
    }
    send({ id, ok: false, error: '未知消息类型: ' + type });
  } catch (e) {
    send({ id, ok: false, error: errText(e) });
  }
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on('line', (line) => {
  touchIdle();
  if (!line || !line.trim()) return;
  let msg;
  try {
    msg = JSON.parse(line);
  } catch (e) {
    send({ id: -1, ok: false, error: 'JSON 解析失败: ' + errText(e) });
    return;
  }
  handleMessage(msg);
});
rl.on('close', () => process.exit(0));
process.on('SIGTERM', () => process.exit(0));
process.on('SIGINT', () => process.exit(0));
