#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""服务管理 · 真实后端(8112)全功能验收（L1/E1/S1 + F1/P1/T1 的 API 等价实现）。

不依赖前端 8848：直接用后端 REST + 直连节点端口打真实流量。
修正：统一使用全局令牌并自动续期，避免把 token 误当请求体发送。
"""
import json, urllib.request, urllib.error, socket, time, http.client, sys, threading

BASE = 'http://127.0.0.1:8112'
HOST = '127.0.0.1'
BACKEND_IP = '192.168.50.198'
UA = ('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/120.0 Safari/537.36')

RESULTS = []
TOKEN = {'v': None}


def _raw(method, path, body=None, token=None, timeout=200):
    h = {'User-Agent': UA}
    if token:
        h['x-oauth-token'] = token
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        h['Content-Type'] = 'application/json'
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
    if s != 200 or not isinstance(r, dict):
        s, r = _raw('POST', '/v2/user/login', {'username': 'admin', 'password': 'admin123'})
    TOKEN['v'] = r['data']['accessToken']
    return TOKEN['v']


def api(method, path, body=None, timeout=200, _retry=True):
    """带全局令牌调用；401/403 时自动续期重试一次。"""
    if TOKEN['v'] is None:
        login()
    s, r = _raw(method, path, body, TOKEN['v'], timeout)
    auth_fail = (s in (401, 403)) or (isinstance(r, dict) and not r.get('success', True)
                                      and '请重新登录' in str(r.get('msg', '')))
    if auth_fail and _retry:
        login()
        return api(method, path, body, timeout, _retry=False)
    return s, r


def node_http(port, method, path, headers=None, body=None, timeout=30):
    c = http.client.HTTPConnection(HOST, port, timeout=timeout)
    h = dict(headers or {})
    if isinstance(body, str):
        body = body.encode()
    c.request(method, path, body=body, headers=h)
    r = c.getresponse()
    data = r.read()
    st = r.status
    c.close()
    return st, data


def probe(host, port, timeout=2.5):
    s = socket.socket()
    s.settimeout(timeout)
    try:
        s.connect((host, port))
        s.close()
        return 'OPEN'
    except Exception:
        return 'FREE'


def wait_port(host, port, want, ms=25000):
    t0 = time.time()
    while True:
        st = probe(host, port)
        if st == want:
            return True
        if (time.time() - t0) * 1000 > ms:
            return False
        time.sleep(0.4)


def read_banner(host, port, timeout=8000):
    s = socket.socket()
    s.settimeout(timeout / 1000.0)
    try:
        s.connect((host, port))
    except Exception as e:
        return '(error %s)' % e
    buf = b''
    try:
        while b'\n' not in buf:
            chunk = s.recv(256)
            if not chunk:
                break
            buf += chunk
    except Exception:
        pass
    finally:
        s.close()
    return buf.decode('latin1', 'replace').strip()


def raw_tcp(host, port, data, timeout=10):
    s = socket.socket()
    s.settimeout(timeout)
    chunks = []
    try:
        s.connect((host, port))
    except Exception as e:
        return {'ok': False, 'err': str(e), 'data': ''}
    try:
        s.sendall(data.encode('latin1'))
        while True:
            b = s.recv(4096)
            if not b:
                break
            chunks.append(b)
    except Exception as e:
        return {'ok': False, 'err': str(e), 'data': b''.join(chunks).decode('latin1', 'replace')}
    finally:
        s.close()
    return {'ok': True, 'data': b''.join(chunks).decode('latin1', 'replace')}


def check(label, cond, detail=''):
    ok = bool(cond)
    RESULTS.append((label, ok, detail))
    line = '%s %s' % ('PASS' if ok else 'FAIL', label)
    if not ok and detail:
        line += '  <<< ' + str(detail)[:400]
    print(line, flush=True)
    return ok


def list_nodes():
    _, r = api('GET', '/ms/monitor/server')
    return r.get('data', []) if isinstance(r, dict) else []


def find(name):
    for n in list_nodes():
        if n.get('name') == name:
            return n
    return None


def status_of(name):
    n = find(name)
    return n['status'] if n else '(none)'


def cleanup(prefix='PWTEST-'):
    n = 0
    for x in list_nodes():
        if str(x.get('name', '')).startswith(prefix):
            api('POST', '/ms/monitor/server/%d/stop' % x['id'])
            api('DELETE', '/ms/monitor/server/%d' % x['id'])
            n += 1
    return n


FS_PARAMS = {
    'openPreview': 'true', 'openDownload': 'true', 'openRange': 'true',
    'openWatermark': 'false', 'openRemoteFile': 'false', 'thirdUrlPreview': 'NONE',
    'openWebjars': 'true', 'watermark': '', 'layout': 'true', 'showFormat': 'true',
    'showSize': 'true', 'showTime': 'true', 'layoutType': 'SIMPLE', 'imageFilter': 'NONE',
}


def fs_body(name, port, layout='true'):
    p = dict(FS_PARAMS)
    p['layout'] = layout
    return {'name': name, 'protocol': 'http', 'engine': 'jdk', 'host': '0.0.0.0',
            'port': port, 'enabled': 1, 'remark': 'api-accept',
            'config': {'privateBuckets': 'localFileStorage'},
            'filters': [{'name': 'file-storage', 'type': 'file-storage', 'order': 0,
                         'enabled': True, 'params': p}]}


def proxy_body(name, port, proto, bp=8112):
    return {'name': name, 'protocol': proto, 'engine': 'jdk', 'host': '0.0.0.0',
            'port': port, 'enabled': 1, 'remark': 'api-accept-proxy',
            'config': {'backendHost': '127.0.0.1', 'backendPort': bp}, 'filters': []}


def http_body(name, port, remark='api-accept'):
    return {'name': name, 'protocol': 'http', 'engine': 'jdk', 'host': '0.0.0.0',
            'port': port, 'enabled': 1, 'remark': remark, 'config': {}, 'filters': []}


def main():
    login()
    print('== 登录成功, token len=%d ==' % len(TOKEN['v']), flush=True)
    cleanup()

    # ---------------- F1 文件服务器 ----------------
    print('\n===== F1 文件服务器(HTTP + file-storage, 私有桶 localFileStorage) =====', flush=True)
    PORT = 28080
    wait_port(BACKEND_IP, PORT, 'FREE', 8000)
    s, r = api('POST', '/ms/monitor/server', fs_body('PWTEST-accept-fileserver', PORT))
    check('F1.1 创建节点成功', s == 200 and r.get('success'), '%s %s' % (s, json.dumps(r, ensure_ascii=False)[:200]))
    check('F1.2 节点 RUNNING', status_of('PWTEST-accept-fileserver') == 'RUNNING', status_of('PWTEST-accept-fileserver'))
    check('F1.3 端口 28080 OPEN', wait_port(BACKEND_IP, PORT, 'OPEN'), probe(BACKEND_IP, PORT))
    auth = {'x-oauth-token': TOKEN['v']}
    st, body = node_http(PORT, 'GET', '/localFileStorage/', headers=auth)
    check('F1.4 带令牌目录页 200 且含 localFileStorage', st == 200 and b'localFileStorage' in body, 'st=%s' % st)
    st, _ = node_http(PORT, 'GET', '/localFileStorage/')
    check('F1.5 无令牌 → 401', st == 401, 'st=%s' % st)
    st, _ = node_http(PORT, 'GET', '/localFileStorage/', headers={'x-oauth-token': 'BAD'})
    check('F1.6 坏令牌 → 401', st == 401, 'st=%s' % st)
    CONTENT = 'acceptance file server payload 2026'
    st, body = node_http(PORT, 'PUT', '/localFileStorage/pwtest-accept.txt',
                         headers={**auth, 'Content-Type': 'text/plain'}, body=CONTENT)
    check('F1.7 上传 200 + SUCCESS', st == 200 and b'SUCCESS' in body, 'st=%s' % st)
    st, body = node_http(PORT, 'GET', '/localFileStorage/pwtest-accept.txt', headers=auth)
    check('F1.8 下载逐字节一致', st == 200 and body.decode() == CONTENT, 'st=%s b=%s' % (st, body[:60]))
    st, body = node_http(PORT, 'GET', '/localFileStorage/pwtest-accept.txt', headers={**auth, 'Range': 'bytes=0-4'})
    check('F1.9 Range 206 + 前 5 字节', st == 206 and body.decode() == CONTENT[:5], 'st=%s b=%s' % (st, body[:20]))
    st, body = node_http(PORT, 'DELETE', '/localFileStorage/pwtest-accept.txt', headers=auth)
    check('F1.10 删除 200 + SUCCESS', st == 200 and b'SUCCESS' in body, 'st=%s' % st)
    st, _ = node_http(PORT, 'GET', '/localFileStorage/pwtest-accept.txt', headers=auth)
    check('F1.11 删后 404', st == 404, 'st=%s' % st)

    node = find('PWTEST-accept-fileserver')
    idx = 0
    for i, f in enumerate(node.get('filters') or []):
        if f.get('type') == 'file-storage':
            idx = i
    samples, stop_flag = [], {'v': False}

    def sampler():
        while not stop_flag['v']:
            samples.append(probe(BACKEND_IP, PORT, 0.8))
            time.sleep(0.05)
    th = threading.Thread(target=sampler); th.start()
    s, r = api('POST', '/ms/monitor/server/%d/filters/%d/params' % (node['id'], idx), {'layout': False})
    check('F1.12 热更新 layout=false 成功', s == 200 and r.get('success'), '%s %s' % (s, json.dumps(r, ensure_ascii=False)[:160]))
    st, _ = node_http(PORT, 'GET', '/localFileStorage/', headers=auth)
    check('F1.13 热更新后目录页立即 404', st == 404, 'st=%s' % st)
    api('POST', '/ms/monitor/server/%d/filters/%d/params' % (node['id'], idx), {'layout': True})
    ok = False
    for _ in range(20):
        st, _ = node_http(PORT, 'GET', '/localFileStorage/', headers=auth)
        if st == 200:
            ok = True; break
        time.sleep(0.3)
    check('F1.14 再热更新 layout=true 恢复 200', ok, 'st=%s' % st)
    stop_flag['v'] = True; th.join()
    check('F1.15 热更新全程端口 OPEN', all(x == 'OPEN' for x in samples),
          'n=%d nonopen=%d' % (len(samples), len([x for x in samples if x != 'OPEN'])))
    s, r = api('POST', '/ms/monitor/server/%d/stop' % node['id'])
    check('F1.16 停止 → 端口 FREE', wait_port(BACKEND_IP, PORT, 'FREE') and status_of('PWTEST-accept-fileserver') == 'STOPPED',
          '%s %s' % (probe(BACKEND_IP, PORT), status_of('PWTEST-accept-fileserver')))
    api('POST', '/ms/monitor/server/%d/start' % node['id'])
    st, _ = node_http(PORT, 'GET', '/localFileStorage/', headers=auth)
    check('F1.17 再启动 → 端口 OPEN + 服务恢复 200', wait_port(BACKEND_IP, PORT, 'OPEN') and st == 200, 'st=%s' % st)
    api('DELETE', '/ms/monitor/server/%d' % node['id'])
    check('F1.18 删除 → 节点消失 + 端口 FREE',
          find('PWTEST-accept-fileserver') is None and wait_port(BACKEND_IP, PORT, 'FREE'))

    # ---------------- P1 HTTP 代理 ----------------
    print('\n===== P1 HTTP 反向代理(后端 127.0.0.1:8112) =====', flush=True)
    HP = 28081
    wait_port(BACKEND_IP, HP, 'FREE', 8000)
    s, r = api('POST', '/ms/monitor/server', proxy_body('PWTEST-accept-httpproxy', HP, 'http-proxy'))
    check('P1.1 创建 http-proxy 成功', s == 200 and r.get('success'), '%s' % json.dumps(r, ensure_ascii=False)[:160])
    check('P1.2 RUNNING + 端口 OPEN', status_of('PWTEST-accept-httpproxy') == 'RUNNING' and wait_port(BACKEND_IP, HP, 'OPEN'))
    _, pr = api('GET', '/ms/monitor/server/proxies')
    check('P1.3 proxies 端点包含该节点', any(x.get('name') == 'PWTEST-accept-httpproxy' for x in (pr.get('data') or [])))
    lbody = json.dumps({'username': 'admin', 'password': 'admin123'})
    frame = ('POST /v2/auth/login HTTP/1.1\r\nHost: 127.0.0.1:8112\r\n'
             'Content-Type: application/json\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s'
             % (len(lbody.encode()), lbody))
    res = raw_tcp(BACKEND_IP, HP, frame)
    check('P1.4 经代理登录 200 + accessToken', res['ok'] and ' 200 ' in res['data'] and 'accessToken' in res['data'], res['data'][:120])
    res2 = raw_tcp(BACKEND_IP, HP, 'GET / HTTP/1.1\r\nHost: 127.0.0.1:8112\r\nConnection: close\r\n\r\n')
    check('P1.5 经代理 GET / → 403(转发到 8112 本体)', ' 403 ' in res2['data'], res2['data'][:120])
    res3 = raw_tcp(BACKEND_IP, 8112, frame)
    check('P1.6 直连 8112 基线 200 + accessToken', ' 200 ' in res3['data'] and 'accessToken' in res3['data'], res3['data'][:120])
    pn = find('PWTEST-accept-httpproxy')
    api('POST', '/ms/monitor/server/%d/stop' % pn['id'])
    res4 = raw_tcp(BACKEND_IP, HP, 'GET / HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n')
    check('P1.7 停止 → 端口 FREE + 连接拒绝', wait_port(BACKEND_IP, HP, 'FREE') and not res4['ok'], res4.get('data', '')[:80])
    api('POST', '/ms/monitor/server/%d/start' % pn['id'])
    res5 = raw_tcp(BACKEND_IP, HP, frame)
    check('P1.8 再启动 → 端口 OPEN + 转发恢复', wait_port(BACKEND_IP, HP, 'OPEN') and ' 200 ' in res5['data'],
          res5['data'][:120])
    api('DELETE', '/ms/monitor/server/%d' % pn['id'])
    check('P1.9 删除 → 节点消失', find('PWTEST-accept-httpproxy') is None)

    # ---------------- T1 TCP 代理 ----------------
    print('\n===== T1 TCP 端口代理(后端 127.0.0.1:8112) =====', flush=True)
    TP = 28082
    wait_port(BACKEND_IP, TP, 'FREE', 8000)
    s, r = api('POST', '/ms/monitor/server', proxy_body('PWTEST-accept-tcpproxy', TP, 'tcp-proxy'))
    check('T1.1 创建 tcp-proxy 成功', s == 200 and r.get('success'), '%s' % json.dumps(r, ensure_ascii=False)[:160])
    check('T1.2 RUNNING + 端口 OPEN', status_of('PWTEST-accept-tcpproxy') == 'RUNNING' and wait_port(BACKEND_IP, TP, 'OPEN'))
    tn = find('PWTEST-accept-tcpproxy')
    check('T1.3 serverUrl 为 tcp://', str((tn or {}).get('serverUrl', '')).startswith('tcp://'), (tn or {}).get('serverUrl'))
    res = raw_tcp(BACKEND_IP, TP, frame)
    check('T1.4 经 TCP 代理登录 200 + accessToken', ' 200 ' in res['data'] and 'accessToken' in res['data'], res['data'][:120])
    api('POST', '/ms/monitor/server/%d/stop' % tn['id'])
    check('T1.5 停止 → 端口 FREE', wait_port(BACKEND_IP, TP, 'FREE'), probe(BACKEND_IP, TP))
    api('POST', '/ms/monitor/server/%d/start' % tn['id'])
    res = raw_tcp(BACKEND_IP, TP, frame)
    check('T1.6 再启动 → 端口 OPEN + 转发恢复', wait_port(BACKEND_IP, TP, 'OPEN') and ' 200 ' in res['data'], res['data'][:120])
    api('DELETE', '/ms/monitor/server/%d' % tn['id'])
    check('T1.7 删除 → 节点消失', find('PWTEST-accept-tcpproxy') is None)

    # ---------------- L1 正常生命周期 ----------------
    print('\n===== L1 正常生命周期(HTTP 28120) =====', flush=True)
    LP = 28120
    wait_port(BACKEND_IP, LP, 'FREE', 8000)
    before = len(list_nodes())
    s, r = api('POST', '/ms/monitor/server', http_body('PWTEST-life-http', LP, 'lifecycle-remark-1'))
    check('L1.1 创建并启动成功', s == 200 and r.get('success'), json.dumps(r, ensure_ascii=False)[:200])
    check('L1.2 列表 +1 且 RUNNING + 端口 OPEN',
          len(list_nodes()) == before + 1 and status_of('PWTEST-life-http') == 'RUNNING' and wait_port(BACKEND_IP, LP, 'OPEN'))
    ln = find('PWTEST-life-http')
    check('L1.3 详情含端口与备注', str(ln.get('port')) == str(LP) and ln.get('remark') == 'lifecycle-remark-1')
    s, r = api('POST', '/ms/monitor/server/%d/hot-update' % ln['id'],
               http_body('PWTEST-life-http', LP, 'lifecycle-remark-EDITED'))
    check('L1.4 编辑(热更新)成功', s == 200 and r.get('success'), json.dumps(r, ensure_ascii=False)[:200])
    check('L1.5 备注改动真的落库', find('PWTEST-life-http').get('remark') == 'lifecycle-remark-EDITED',
          find('PWTEST-life-http').get('remark'))
    api('POST', '/ms/monitor/server/%d/stop' % ln['id'])
    check('L1.6 停止 → FREE + STOPPED',
          wait_port(BACKEND_IP, LP, 'FREE') and status_of('PWTEST-life-http') == 'STOPPED', status_of('PWTEST-life-http'))
    api('POST', '/ms/monitor/server/%d/start' % ln['id'])
    check('L1.7 启动 → OPEN + RUNNING',
          wait_port(BACKEND_IP, LP, 'OPEN') and status_of('PWTEST-life-http') == 'RUNNING', status_of('PWTEST-life-http'))
    api('DELETE', '/ms/monitor/server/%d' % ln['id'])
    check('L1.8 删除 → 回基线 + 端口 FREE',
          len(list_nodes()) == before and wait_port(BACKEND_IP, LP, 'FREE'), 'count=%d' % len(list_nodes()))

    # ---------------- E1 错误态 ----------------
    print('\n===== E1 错误态(端口冲突 8112 → 自愈 → 回退) =====', flush=True)
    FP = 28121
    wait_port(BACKEND_IP, FP, 'FREE', 8000)
    check('E1.0 前置: 8112 OPEN', probe(BACKEND_IP, 8112) == 'OPEN', probe(BACKEND_IP, 8112))
    api('POST', '/ms/monitor/server', http_body('PWTEST-life-err', 8112))
    en = find('PWTEST-life-err')
    check('E1.1 端口被占 → 节点存在且 ERROR', en is not None and en.get('status') == 'ERROR', status_of('PWTEST-life-err'))
    raw = json.dumps(en, ensure_ascii=False)
    check('E1.2 错误态带失败原因', any(k in raw for k in ('占用', '冲突', '失败', '监听')), raw[:300])
    s, r = api('POST', '/ms/monitor/server/%d/hot-update' % en['id'], http_body('PWTEST-life-err', FP))
    healed = False
    for _ in range(40):
        if status_of('PWTEST-life-err') == 'RUNNING':
            healed = True; break
        time.sleep(0.5)
    check('E1.3 改空闲端口 → 自愈 RUNNING', healed, status_of('PWTEST-life-err'))
    check('E1.4 自愈后端口 OPEN', wait_port(BACKEND_IP, FP, 'OPEN', 30000), probe(BACKEND_IP, FP))
    s, r = api('POST', '/ms/monitor/server/%d/hot-update' % en['id'], http_body('PWTEST-life-err', 8112))
    back = False
    for _ in range(80):
        if status_of('PWTEST-life-err') == 'ERROR':
            back = True; break
        time.sleep(0.5)
    check('E1.5 再改坏端口 → 回退 ERROR', back, status_of('PWTEST-life-err'))
    check('E1.6 回退后旧端口 FREE', wait_port(BACKEND_IP, FP, 'FREE', 20000), probe(BACKEND_IP, FP))
    s, r = api('POST', '/ms/monitor/server/%d/start' % en['id'])
    check('E1.7 ERROR 节点启动接口返回(成功的错误结果)不抛 5xx', s == 200 and not isinstance(r, str),
          '%s %s' % (s, str(r)[:160]))
    en2 = find('PWTEST-life-err')
    check('E1.8 ERROR 节点仍在列表', en2 is not None and en2.get('status') in ('RUNNING', 'ERROR'), status_of('PWTEST-life-err'))
    api('DELETE', '/ms/monitor/server/%d' % en['id'])
    check('E1.9 错误节点删除复原', find('PWTEST-life-err') is None)

    # ---------------- S1 FTP ----------------
    print('\n===== S1 SPI 协议 ftp(28122) =====', flush=True)
    FTPP = 28122
    wait_port(BACKEND_IP, FTPP, 'FREE', 8000)
    s, r = api('POST', '/ms/monitor/server', {'name': 'PWTEST-life-ftp', 'protocol': 'ftp', 'engine': 'jdk',
               'host': '0.0.0.0', 'port': FTPP, 'enabled': 1, 'remark': 'api-accept-ftp', 'config': {}, 'filters': []})
    check('S1.1 创建 ftp 成功', s == 200 and r.get('success'), json.dumps(r, ensure_ascii=False)[:200])
    check('S1.2 RUNNING + 端口 OPEN', status_of('PWTEST-life-ftp') == 'RUNNING' and wait_port(BACKEND_IP, FTPP, 'OPEN'),
          status_of('PWTEST-life-ftp'))
    banner = read_banner(BACKEND_IP, FTPP)
    check('S1.3 真实 TCP 读到 220 banner', '220' in banner, repr(banner))
    fn = find('PWTEST-life-ftp')
    api('POST', '/ms/monitor/server/%d/stop' % fn['id'])
    check('S1.4 停止 → 端口 FREE', wait_port(BACKEND_IP, FTPP, 'FREE'), probe(BACKEND_IP, FTPP))
    api('POST', '/ms/monitor/server/%d/start' % fn['id'])
    b2 = read_banner(BACKEND_IP, FTPP)
    check('S1.5 再启动 → OPEN + banner 220 恢复', wait_port(BACKEND_IP, FTPP, 'OPEN') and '220' in b2, repr(b2))
    api('DELETE', '/ms/monitor/server/%d' % fn['id'])
    check('S1.6 删除 → 节点消失 + 端口 FREE',
          find('PWTEST-life-ftp') is None and wait_port(BACKEND_IP, FTPP, 'FREE'))

    # ---------------- 过滤器启停 ----------------
    print('\n===== 过滤器热启停 =====', flush=True)
    wait_port(BACKEND_IP, 28083, 'FREE', 8000)
    api('POST', '/ms/monitor/server', fs_body('PWTEST-accept-toggle', 28083))
    tn2 = find('PWTEST-accept-toggle')
    auth2 = {'x-oauth-token': TOKEN['v']}
    st, _ = node_http(28083, 'GET', '/localFileStorage/', headers=auth2)
    check('FT.1 目录页 200(鉴权生效)', st == 200, 'st=%s' % st)
    idx2 = [i for i, f in enumerate(tn2.get('filters') or []) if f.get('type') == 'file-storage']
    idx2 = idx2[0] if idx2 else 0
    s, r = api('POST', '/ms/monitor/server/%d/filters/%d/toggle?enabled=false' % (tn2['id'], idx2))
    check('FT.2 停用 file-storage 过滤器成功', s == 200 and r.get('success'), json.dumps(r, ensure_ascii=False)[:160])
    st, _ = node_http(28083, 'GET', '/localFileStorage/', headers=auth2)
    check('FT.3 停用后目录页不再 200', st != 200, 'st=%s' % st)
    api('POST', '/ms/monitor/server/%d/filters/%d/toggle?enabled=true' % (tn2['id'], idx2))
    ok = False
    for _ in range(20):
        st, _ = node_http(28083, 'GET', '/localFileStorage/', headers=auth2)
        if st == 200:
            ok = True; break
        time.sleep(0.3)
    check('FT.4 启用后目录页恢复 200', ok, 'st=%s' % st)
    api('DELETE', '/ms/monitor/server/%d' % tn2['id'])

    # ---------------- 元数据 ----------------
    print('\n===== 元数据端点 =====', flush=True)
    _, r = api('GET', '/ms/monitor/server/protocols')
    protos = [p.get('alias') for p in (r.get('data') or [])]
    check('M1 protocols 含 http/http-proxy/tcp-proxy', all(x in protos for x in ('http', 'http-proxy', 'tcp-proxy')), str(protos))
    check('M2 protocols 含 SPI 协议 ftp', 'ftp' in protos, str(protos))
    _, r = api('GET', '/ms/monitor/server/filters')
    ftypes = [x.get('type') for x in (r.get('data') or [])]
    check('M3 filters 含 file-storage', 'file-storage' in ftypes, str(ftypes))

    n = cleanup()
    print('\n清理残留 PWTEST 节点: %d' % n, flush=True)
    passed = sum(1 for _, ok, _ in RESULTS if ok)
    total = len(RESULTS)
    print('\n================ 汇总: %d/%d 通过 ================' % (passed, total), flush=True)
    for label, ok, detail in RESULTS:
        if not ok:
            print('  FAILED: %s  %s' % (label, str(detail)[:200]), flush=True)
    sys.exit(0 if passed == total else 1)


if __name__ == '__main__':
    main()
