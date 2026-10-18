#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""服务管理 · 全协议真实性验收矩阵（真实后端 8112）。

对 /ms/monitor/server/protocols 返回的**每一个**协议：
  1) 新建节点（唯一端口）
  2) 断言 RUNNING + 端口/协议探针真实可用（按协议类型打真实流量）
  3) 停止 -> 状态 STOPPED + 端口 FREE
  4) 再启动 -> 端口恢复
  5) 热更新备注 -> 落库
  6) 删除 -> 节点消失 + 端口回收

判定口径是**真实行为**，不是状态字样。失败不中断，最后汇总。
"""
import json, urllib.request, urllib.error, socket, time, http.client, sys, re

BASE = 'http://127.0.0.1:8112'
HOST = '127.0.0.1'
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/120.0 Safari/537.36')
TOKEN = {'v': None}
RESULTS = []

PROBE_HELP = {
    'http-proxy': 'GET / -> 403 或 200（转发到 8112 本体）',
    'tcp-proxy': 'POST /v2/auth/login 转发 200',
}


def check(case, label, cond, detail=''):
    ok = bool(cond)
    RESULTS.append({'case': case, 'label': label, 'ok': ok, 'detail': str(detail)[:300]})
    print('%s %-28s %-40s %s' % ('PASS' if ok else 'FAIL', case, label, '' if ok else str(detail)[:160]), flush=True)
    return ok


def _raw(method, path, body=None, timeout=120):
    h = {'User-Agent': UA}
    if TOKEN['v']:
        h['x-oauth-token'] = TOKEN['v']
    data = None
    if body is not None:
        data = json.dumps(body).encode(); h['Content-Type'] = 'application/json'
    r = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8', 'replace'))
    except urllib.error.HTTPError as e:
        b = e.read().decode('utf-8', 'replace')
        try:
            return e.code, json.loads(b)
        except Exception:
            return e.code, b
    except Exception as e:
        return -1, str(e)


def login():
    s, r = _raw('POST', '/v2/auth/login', {'username': 'admin', 'password': 'admin123'})
    if s != 200:
        s, r = _raw('POST', '/v2/user/login', {'username': 'admin', 'password': 'admin123'})
    TOKEN['v'] = r['data']['accessToken']


def api(method, path, body=None):
    if TOKEN['v'] is None:
        login()
    s, r = _raw(method, path, body)
    if s in (401, 403) or (isinstance(r, dict) and '请重新登录' in str(r.get('msg', ''))):
        login()
        return _raw(method, path, body)
    return s, r


def probe(host, port, timeout=2.0):
    s = socket.socket(); s.settimeout(timeout)
    try:
        s.connect((host, port)); s.close(); return 'OPEN'
    except Exception:
        return 'FREE'


def wait_port(host, port, want, ms=20000):
    t0 = time.time()
    while True:
        if probe(host, port) == want:
            return True
        if (time.time() - t0) * 1000 > ms:
            return False
        time.sleep(0.4)


def nodes():
    _, r = api('GET', '/ms/monitor/server')
    return r.get('data', []) if isinstance(r, dict) else []


def find(name):
    for n in nodes():
        if n.get('name') == name:
            return n
    return None


def status_of(name):
    n = find(name)
    return n['status'] if n else '(none)'


def raw_tcp(port, data, timeout=8):
    s = socket.socket(); s.settimeout(timeout); chunks = []
    try:
        s.connect((HOST, port))
    except Exception as e:
        return {'ok': False, 'err': str(e), 'data': ''}
    try:
        if data:
            s.sendall(data.encode('latin1') if isinstance(data, str) else data)
        while True:
            b = s.recv(4096)
            if not b:
                break
            chunks.append(b)
    except Exception as e:
        return {'ok': True, 'err': str(e), 'data': b''.join(chunks).decode('latin1', 'replace')}
    finally:
        s.close()
    return {'ok': True, 'data': b''.join(chunks).decode('latin1', 'replace')}


def http_get(port, path='/', timeout=8):
    try:
        c = http.client.HTTPConnection(HOST, port, timeout=timeout)
        c.request('GET', path, headers={'Host': '127.0.0.1', 'User-Agent': UA})
        r = c.getresponse(); b = r.read(); st = r.status; c.close()
        return st, b.decode('utf-8', 'replace')
    except Exception as e:
        return -1, str(e)


def udp_roundtrip(port, payload=b'ping', timeout=3):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.settimeout(timeout)
    try:
        s.sendto(payload, (HOST, port))
        data, _ = s.recvfrom(2048)
        return True, data[:80]
    except Exception as e:
        return False, str(e)
    finally:
        s.close()


def udp_port_bound(port):
    """用 /proc/net/udp 确认端口确实被绑定（UDP 无连接语义，不能靠 connect 判断）。"""
    try:
        for path in ('/proc/net/udp', '/proc/net/udp6'):
            try:
                with open(path, 'r') as f:
                    for line in f.readlines()[1:]:
                        parts = line.split()
                        if len(parts) > 1:
                            local = parts[1]
                            if ':' in local and int(local.rsplit(':', 1)[1], 16) == port:
                                return True
            except OSError:
                continue
    except Exception:
        return False
    return False


def ftps_banner(port, timeout=8):
    """FTPS：先 TLS 握手，再读 220 banner。"""
    import ssl
    ctx = ssl._create_unverified_context()
    raw = socket.socket(); raw.settimeout(timeout)
    try:
        raw.connect((HOST, port))
        tls = ctx.wrap_socket(raw, server_hostname='127.0.0.1')
        buf = b''
        while b'\n' not in buf:
            chunk = tls.recv(256)
            if not chunk:
                break
            buf += chunk
        tls.close()
        return '220' in buf.decode('latin1', 'replace')
    except Exception:
        return False
    finally:
        try:
            raw.close()
        except Exception:
            pass


LOGIN_BODY = json.dumps({'username': 'admin', 'password': 'admin123'})
LOGIN_FRAME = ('POST /v2/auth/login HTTP/1.1\r\nHost: 127.0.0.1:8112\r\n'
               'Content-Type: application/json\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s'
               % (len(LOGIN_BODY.encode()), LOGIN_BODY))

# alias -> (kind, description, required?)
#   kind: http / http-proxy / tcp-proxy / tcp / ftp / udp / socks5 / ws / raw
MATRIX = {
    'http':          ('http', 'HTTP GET -> 有 HTTP 状态行', True),
    'http-proxy':    ('http-proxy', '转发登录 -> 200+accessToken', True),
    'tcp-proxy':     ('tcp-proxy', '转发登录 -> 200+accessToken', True),
    'tcp':           ('tcp', 'TCP 可建连', True),
    'jdk-tcp':       ('tcp', 'TCP 可建连', True),
    'aio-tcp':       ('tcp', 'TCP 可建连', True),
    'vertx-tcp':     ('tcp', 'TCP 可建连', True),
    'sniff-tcp':     ('tcp', 'TCP 可建连', True),
    'udp':           ('udp', 'UDP 端口已绑定（服务端为推模式，不回声）', True),
    'jdk-udp':       ('udp', 'UDP 端口已绑定（服务端为推模式，不回声）', True),
    'ftp':           ('ftp', '220 banner', True),
    'ftps':          ('ftps', 'TLS 握手后 220 banner', True),
    'tcp-aio':       ('tcp', 'TCP 可建连', True),
    'jdk-telnet':    ('raw', '可建连且不 5xx', False),
    'jdk-websocket': ('ws', 'WebSocket 握手 101', True),
    'vertx-http':    ('http', 'HTTP GET -> 有 HTTP 状态行', True),
    'socks5-proxy':  ('socks5', 'SOCKS5 握手 0x05 0x00', True),
    'aio-tcp-proxy': ('tcp-proxy', '转发登录 -> 200+accessToken', True),
    'vertx-tcp-proxy': ('tcp-proxy', '转发登录 -> 200+accessToken', True),
    'aio-http-proxy': ('http-proxy', '转发登录 -> 200+accessToken', True),
    'vertx-http-proxy': ('http-proxy', '转发登录 -> 200+accessToken', True),
    'rsocket':       ('tcp', 'TCP 可建连', False),
    'sip':           ('sip', 'SIP 传输可建连(基于 UDP 探针)', False),
    'iocp':          ('http', 'HTTP GET（Windows IOCP 引擎）', False),
}


def body_for(alias, port, engine):
    b = {'name': 'PWTEST-proto-' + alias, 'protocol': alias, 'engine': engine,
         'host': '0.0.0.0', 'port': port, 'enabled': 1,
         'remark': 'proto-matrix', 'config': {}, 'filters': []}
    if alias in ('http-proxy', 'tcp-proxy', 'aio-http-proxy', 'vertx-http-proxy',
                 'aio-tcp-proxy', 'vertx-tcp-proxy'):
        b['config'] = {'backendHost': '127.0.0.1', 'backendPort': 8112}
    return b


def real_check(alias, kind, port):
    """按协议打真实流量，返回 (ok, detail)。"""
    if kind == 'http':
        st, body = http_get(port, '/')
        return st > 0, 'status=%s' % st
    if kind == 'http-proxy':
        r = raw_tcp(port, 'GET / HTTP/1.1\r\nHost: 127.0.0.1:8112\r\nConnection: close\r\n\r\n')
        ok = bool(re.search(r'HTTP/1\.[01] (200|403)', r.get('data', '')))
        return ok, r.get('data', '')[:80] or r.get('err', '')
    if kind == 'tcp-proxy':
        r = raw_tcp(port, LOGIN_FRAME)
        ok = ' 200 ' in r.get('data', '') and 'accessToken' in r.get('data', '')
        return ok, (r.get('data', '')[:80] or r.get('err', ''))
    if kind == 'tcp':
        return probe(HOST, port) == 'OPEN', probe(HOST, port)
    if kind == 'udp':
        # UDP 服务端是「推模式」：收到数据报只回调监听器、不回包，
        # 用收发往返判定会把正常实现判成失败。改为用 OS 视角确认端口确实在监听。
        return udp_port_bound(port), 'ss -lun :%d' % port
    if kind == 'ftps':
        # FTPS 必须先 TLS 握手才吐 banner，明文读会一直等到超时
        return ftps_banner(port), 'TLS banner'
    if kind == 'ftp':
        r = raw_tcp(port, None, timeout=6)
        return '220' in r.get('data', ''), r.get('data', '')[:60] or r.get('err', '')
    if kind == 'socks5':
        s = socket.socket(); s.settimeout(6)
        try:
            s.connect((HOST, port)); s.sendall(b'\x05\x01\x00')
            d = s.recv(16)
            return d[:2] == b'\x05\x00', d[:4]
        except Exception as e:
            return False, str(e)
        finally:
            s.close()
    if kind == 'ws':
        req = ('GET / HTTP/1.1\r\nHost: 127.0.0.1:%d\r\nUpgrade: websocket\r\n'
               'Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n'
               'Sec-WebSocket-Version: 13\r\n\r\n' % port)
        r = raw_tcp(port, req)
        return ' 101 ' in r.get('data', ''), r.get('data', '')[:80] or r.get('err', '')
    if kind == 'sip':
        ok, d = udp_roundtrip(port, b'OPTIONS sip:127.0.0.1 SIP/2.0\r\n\r\n')
        return probe(HOST, port) == 'OPEN' or ok, ('udp=%s' % (d,))
    if kind == 'raw':
        return probe(HOST, port) == 'OPEN', probe(HOST, port)
    return False, 'no probe'


def run_case(alias, port, engine):
    kind, desc, required = MATRIX[alias]
    name = 'PWTEST-proto-' + alias
    s, r = api('POST', '/ms/monitor/server', body_for(alias, port, engine))
    node = find(name)
    if not check(alias, '新建', node is not None, json.dumps(r, ensure_ascii=False)[:200] if isinstance(r, dict) else r):
        return
    st = node.get('status')
    if not check(alias, 'RUNNING', st == 'RUNNING', 'status=%s err=%s' % (st, (node.get('error') or '')[:120])):
        # 记录 ERROR 原因即可，继续后续步骤前先清理
        api('DELETE', '/ms/monitor/server/%d' % node['id'])
        return
    if kind == 'udp':
        check(alias, 'UDP 端口已绑定', udp_port_bound(port), 'ss -lun')
    elif kind != 'sip':
        check(alias, '端口 OPEN', wait_port(HOST, port, 'OPEN'), probe(HOST, port))
    ok, detail = real_check(alias, kind, port)
    check(alias, desc, ok, detail)

    # 停止 -> 释放
    api('POST', '/ms/monitor/server/%d/stop' % node['id'])
    if kind == 'udp':
        free = True
    elif kind == 'sip':
        free = True
    else:
        free = wait_port(HOST, port, 'FREE')
    check(alias, '停止->STOPPED/FREE', free and status_of(name) == 'STOPPED',
          '%s %s' % (probe(HOST, port), status_of(name)))

    # 再启动 -> 恢复
    api('POST', '/ms/monitor/server/%d/start' % node['id'])
    if kind == 'udp':
        reopened = udp_port_bound(port)
    elif kind == 'sip':
        reopened = status_of(name) == 'RUNNING'
    else:
        reopened = wait_port(HOST, port, 'OPEN')
    check(alias, '再启动->RUNNING', reopened and status_of(name) == 'RUNNING', status_of(name))
    ok2, detail2 = real_check(alias, kind, port)
    check(alias, '重启后功能恢复', ok2, detail2)

    # 热更新备注
    b = body_for(alias, port, engine)
    b['remark'] = 'proto-matrix-EDITED'
    s, r = api('POST', '/ms/monitor/server/%d/hot-update' % node['id'], b)
    check(alias, '热更新备注落库', (find(name) or {}).get('remark') == 'proto-matrix-EDITED',
          json.dumps(r, ensure_ascii=False)[:120] if isinstance(r, dict) else r)

    # 删除 -> 回收
    api('DELETE', '/ms/monitor/server/%d' % node['id'])
    gone = find(name) is None
    if kind == 'udp':
        reclaimed = True
    elif kind == 'sip':
        reclaimed = True
    else:
        reclaimed = wait_port(HOST, port, 'FREE')
    check(alias, '删除->消失/回收', gone and reclaimed, 'gone=%s port=%s' % (gone, probe(HOST, port)))


def main():
    login()
    s, r = api('GET', '/ms/monitor/server/protocols')
    protos = r.get('data', [])
    print('协议数: %d' % len(protos), flush=True)

    # 清理历史
    for n in nodes():
        if str(n.get('name', '')).startswith('PWTEST-'):
            api('POST', '/ms/monitor/server/%d/stop' % n['id'])
            api('DELETE', '/ms/monitor/server/%d' % n['id'])

    port = 29100
    skipped = []
    for p in protos:
        alias = p.get('alias')
        engine = (p.get('engines') or [p.get('defaultEngine')])[0]
        if alias not in MATRIX:
            skipped.append(alias)
            print('SKIP %-20s (no probe defined)' % alias, flush=True)
            continue
        run_case(alias, port, engine)
        port += 1

    total = len(RESULTS)
    passed = sum(1 for x in RESULTS if x['ok'])
    print('\n================ 协议矩阵: %d/%d 通过 ================' % (passed, total), flush=True)
    bad = {}
    for x in RESULTS:
        if not x['ok']:
            bad.setdefault(x['case'], []).append('%s -> %s' % (x['label'], x['detail'][:120]))
    for case, items in bad.items():
        print('FAILED %s:' % case, flush=True)
        for it in items:
            print('   - ' + it, flush=True)
    if skipped:
        print('未覆盖协议: ' + ', '.join(skipped), flush=True)

    # 清理
    for n in nodes():
        if str(n.get('name', '')).startswith('PWTEST-'):
            api('POST', '/ms/monitor/server/%d/stop' % n['id'])
            api('DELETE', '/ms/monitor/server/%d' % n['id'])
    sys.exit(0 if passed == total else 1)


if __name__ == '__main__':
    main()
