package com.chua.starter.server.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.model.vo.TunnelVO;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.TunnelService;
import com.chua.starter.server.support.util.SshClients;
import com.chua.ssh.support.client.SshClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 主机隧道服务实现 —— 内存托管 SSH 端口转发
 *
 * <p>每条隧道维护两个句柄：<b>配置</b>（{@link TunnelVO}，随 CRUD 增删）与
 * <b>活跃转发</b>（{@code SshClient#forward()} 的 AutoCloseable 结果，只有
 * RUNNING 的隧道持有）。正向（LOCAL）= 本机监听 → 远端目标；反向（REMOTE）
 * = 远端监听 → 本机回流，与 {@code ssh -L / ssh -R} 语义一致。</p>
 *
 * <p>受控主机凭据来自 {@link ServerHostService#resolveCredential}；连接建立
 * 走 {@link SshClients#open}（带默认超时），不复用 {@link ConnectionManager}
 * 命令会话（隧道需要长连接，命令会话是一次性的）。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TunnelServiceImpl implements TunnelService {

    /**
     * 主机 映射器
     */
    private final MsServerHostMapper hostMapper;

    /**
     * 主机服务（凭据解析）
     */
    private final ServerHostService serverHostService;

    /**
     * 隧道配置表：hostId -> id -> 隧道
     */
    private final Map<Long, Map<Long, TunnelVO>> tunnels = new ConcurrentHashMap<>();

    /**
     * 活跃转发句柄表：hostId:id -> AutoCloseable（仅 RUNNING 持有）
     */
    private final Map<String, AutoCloseable> handles = new ConcurrentHashMap<>();

    /**
     * 隧道 id 自增序列
     */
    private final AtomicLong idSeq = new AtomicLong(1);

    @Override
    public ReturnResult<List<TunnelVO>> list(Long hostId) {
        List<TunnelVO> list = new ArrayList<>(tunnels.getOrDefault(hostId, Map.of()).values());
        return ReturnResult.ok(list);
    }

    @Override
    public ReturnResult<TunnelVO> create(Long hostId, TunnelVO d) {
        try {
            ensureHost(hostId);
            if (d == null) {
                return ReturnResult.error("隧道参数为空");
            }
            String dir = normalizeDir(d.getDir());
            TunnelVO vo = new TunnelVO();
            vo.setId(idSeq.getAndIncrement());
            vo.setName(blankDefault(d.getName(), dir.equals("forward") ? "正向隧道" : "反向隧道"));
            vo.setDir(dir);
            vo.setProto(blankDefault(d.getProto(), "TCP"));
            vo.setIdentity(blankDefault(d.getIdentity(), ""));
            // 计算 from/to
            if (dir.equals("forward")) {
                String listen = blankDefault(d.getListen(), "127.0.0.1");
                String port = blankDefault(d.getPort(), "22");
                String targetHost = blankDefault(d.getTargetHost(), "");
                String targetPort = blankDefault(d.getTargetPort(), port);
                vo.setListen(listen);
                vo.setPort(port);
                vo.setTargetHost(targetHost);
                vo.setTargetPort(targetPort);
                vo.setFrom(listen + ":" + port);
                vo.setTo(targetHost + ":" + targetPort);
                vo.setVia(hostNameOf(hostId) + " (跳板)");
            } else {
                String listen = blankDefault(d.getListen(), "0.0.0.0");
                String port = blankDefault(d.getPort(), "8080");
                String backHost = blankDefault(d.getBackHost(), "127.0.0.1");
                String backPort = blankDefault(d.getBackPort(), port);
                vo.setListen(listen);
                vo.setPort(port);
                vo.setBackHost(backHost);
                vo.setBackPort(backPort);
                vo.setFrom(listen + ":" + port);
                vo.setTo(backHost + ":" + backPort);
                vo.setVia(hostNameOf(hostId));
            }
            vo.setStatus("stopped");
            tunnels.computeIfAbsent(hostId, k -> new ConcurrentHashMap<>()).put(vo.getId(), vo);
            // 默认不自动建立（前端建好后手动启动），直接返回 stopped 状态
            return ReturnResult.ok(vo);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[tunnel] 建立隧道失败 hostId={}: {}", hostId, e.getMessage());
            return ReturnResult.error("建立隧道失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<TunnelVO> updateState(Long hostId, Long id, String state) {
        try {
            TunnelVO vo = require(hostId, id);
            boolean wantRunning = "RUNNING".equalsIgnoreCase(state) || "running".equalsIgnoreCase(state);
            if (wantRunning && !"running".equals(vo.getStatus())) {
                AutoCloseable handle = startForward(hostId, vo);
                String key = keyOf(hostId, id);
                handles.put(key, handle);
                vo.setStatus("running");
            } else if (!wantRunning && "running".equals(vo.getStatus())) {
                closeHandle(keyOf(hostId, id));
                vo.setStatus("stopped");
            }
            return ReturnResult.ok(vo);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[tunnel] 切换隧道状态失败 hostId={} id={}: {}", hostId, id, e.getMessage());
            // 启动失败标记 fail（前端可看到连接失败），已启动的句柄清理
            TunnelVO vo = tunnels.getOrDefault(hostId, Map.of()).get(id);
            if (vo != null) {
                vo.setStatus("fail");
            }
            return ReturnResult.error("隧道启动失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> delete(Long hostId, Long id) {
        try {
            require(hostId, id);
            closeHandle(keyOf(hostId, id));
            Map<Long, TunnelVO> byHost = tunnels.get(hostId);
            if (byHost != null) {
                byHost.remove(id);
                if (byHost.isEmpty()) {
                    tunnels.remove(hostId);
                }
            }
            return ReturnResult.ok(true);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("[tunnel] 删除隧道失败 hostId={} id={}: {}", hostId, id, e.getMessage());
            return ReturnResult.error("删除隧道失败: " + e.getMessage());
        }
    }

    /* ==================== 私有工具 ==================== */

    /**
     * 校验主机存在
     *
     * @param hostId 主机 标识
     */
    private void ensureHost(Long hostId) {
        if (hostId == null || hostMapper.selectById(hostId) == null) {
            throw new IllegalStateException("主机不存在: " + hostId);
        }
    }

    /**
     * 取隧道（不存在抛错）
     *
     * @param hostId 主机 标识
     * @param id     隧道 id
     * @return 隧道
     */
    private TunnelVO require(Long hostId, Long id) {
        TunnelVO vo = tunnels.getOrDefault(hostId, Map.of()).get(id);
        if (vo == null) {
            throw new IllegalStateException("隧道不存在: " + id);
        }
        return vo;
    }

    /**
     * 启动一条 SSH 端口转发并返回句柄
     *
     * @param hostId 主机 标识
     * @param vo     隧道
     * @return AutoCloseable 句柄
     * @throws Exception 连接或转发建立失败时抛出
     */
    private AutoCloseable startForward(Long hostId, TunnelVO vo) throws Exception {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            throw new IllegalStateException("主机不存在: " + hostId);
        }
        ReturnResult<SshCredential> resolved = serverHostService.resolveCredential(hostId, null);
        if (!resolved.isOk()) {
            throw new IllegalStateException(resolved.getMsg());
        }
        SshClients.Handle handle = SshClients.open(host.getServerHostAddress(), host.getServerHostPort(), resolved.getData());
        handle.client().connect();
        // [P3C A20 豁免]
        int port = parseInt(vo.getPort(), 22);
        String bind = blankDefault(vo.getListen(), "127.0.0.1");
        SshClient.ForwardOperation forward = handle.client().forward().bindAddress(bind);
        if ("forward".equals(vo.getDir())) {
            String targetHost = blankDefault(vo.getTargetHost(), "127.0.0.1");
            int targetPort = parseInt(vo.getTargetPort(), port);
            forward.local(port, targetHost, targetPort);
        } else {
            String backHost = blankDefault(vo.getBackHost(), "127.0.0.1");
            int backPort = parseInt(vo.getBackPort(), port);
            forward.remote(port, backHost, backPort);
        }
        // 句柄链：转发启动成功后，关闭时先停转发再断连接
        AutoCloseable forwardHandle = forward.start();
        return () -> {
            try {
                forwardHandle.close();
            } finally {
                handle.close();
            }
        };
    }

    /**
     * 关闭活跃转发句柄
     *
     * @param key hostId:id
     */
    private void closeHandle(String key) {
        AutoCloseable h = handles.remove(key);
        if (h != null) {
            try {
                h.close();
            } catch (Exception e) {
                log.warn("[tunnel] 关闭转发失败 key={}: {}", key, e.getMessage());
            }
        }
    }

    /**
     * 隧道键
     *
     * @param hostId 主机 标识
     * @param id     隧道 id
     * @return hostId:id
     */
    private String keyOf(Long hostId, Long id) {
        return hostId + ":" + id;
    }

    /**
     * 主机名
     *
     * @param hostId 主机 标识
     * @return 主机名，空 为 unknown
     */
    private String hostNameOf(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        return host == null ? "#" + hostId : (host.getServerHostName() == null ? "#" + hostId : host.getServerHostName());
    }

    /**
     * 方向归一化
     *
     * @param dir 原始方向
     * @return forward / reverse
     */
    private String normalizeDir(String dir) {
        if (dir == null || dir.isBlank()) {
            return "forward";
        }
        return "reverse".equalsIgnoreCase(dir.trim()) ? "reverse" : "forward";
    }

    /**
     * 空值兜底
     *
     * @param value 原始值
     * @param def   默认值
     * @return 非空值或默认值
     */
    private String blankDefault(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim();
    }

    /**
     * 端口解析（非法回退默认）
     *
     * @param value 端口串
     * @param def   默认值
     * @return 端口
     */
    private int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return def;
        }
    }
}
