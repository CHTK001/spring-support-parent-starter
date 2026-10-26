package com.chua.starter.server.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.vo.TunnelVO;

import java.util.List;

/**
 * 主机隧道服务 —— 正向/反向 SSH 端口转发托管
 *
 * <p>隧道 = 经受控主机维护一条 SSH 端口转发：正向（{@code ssh -L}）把本机
 * 监听端口接到远端目标；反向（{@code ssh -R}）把远端监听端口接回本机。
 * 活跃隧道由 {@code SshClient#forward()} 托管（AutoCloseable 句柄），
 * 配置存内存 Map（进程内生命周期）。</p>
 *
 * <p>端点与前端 {@code pages/server/src/api/index.ts} 的 tunnel 桩严格对齐：
 * 前缀 {@code /ms/server/host/{hostId}/tunnel}，响应统一 {@code ReturnResult}。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public interface TunnelService {

    /**
     * 列出主机的全部隧道
     *
     * @param hostId 主机 标识
     * @return 隧道列表（含运行态）
     */
    ReturnResult<List<TunnelVO>> list(Long hostId);

    /**
     * 建立隧道（正向/反向）
     *
     * @param hostId 主机 标识
     * @param d      隧道定义（dir=forward/reverse）
     * @return 新建的隧道（RUNNING）
     */
    ReturnResult<TunnelVO> create(Long hostId, TunnelVO d);

    /**
     * 切换隧道运行态（启动/停止）
     *
     * @param hostId 主机 标识
     * @param id     隧道 id
     * @param state  RUNNING / STOPPED
     * @return 更新后的隧道
     */
    ReturnResult<TunnelVO> updateState(Long hostId, Long id, String state);

    /**
     * 删除隧道（停止并移除）
     *
     * @param hostId 主机 标识
     * @param id     隧道 id
     * @return 是否删除成功
     */
    ReturnResult<Boolean> delete(Long hostId, Long id);
}
