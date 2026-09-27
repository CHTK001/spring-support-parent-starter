package com.chua.starter.datasourcehub.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.DataSourceTunnelVO;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceTunnelDTO;

/**
 * 数据源 SSH 隧道服务（高级配置）。
 *
 * <p>用于「目标库在本机不可直连」的场景：本机先与一台可达的 SSH 服务器建立隧道，
 * 再经隧道本地端口去连目标库。反向隧道（REMOTE，对应 {@code ssh -R}）适用于
 * 目标库在内网、而 SSH 服务器与目标库同网段的情况。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface DatasourceTunnelService {

    /**
     * 隧道本地端点。
     *
     * @param host 本地绑定地址
     * @param port 本地监听端口（实际分配值，非配置值）
     */
    record TunnelEndpoint(String host, int port) {
    }

    /**
     * 若该数据源配置了启用的隧道，则确保隧道已打开并返回本地端点；未配置或停用时返回 null。
     *
     * <p>幂等：已打开且存活的隧道直接复用，不会重复建连。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 隧道本地端点；未启用时 null
     */
    TunnelEndpoint openIfEnabled(Long datasourceId);

    /**
     * 查询数据源的隧道配置。
     *
     * @param datasourceId 数据源 ID
     * @return 隧道配置；未配置时 data 为 null
     */
    ReturnResult<DataSourceTunnelVO> get(Long datasourceId);

    /**
     * 保存（新增或覆盖）数据源的隧道配置，一个数据源至多一条。
     *
     * @param datasourceId 数据源 ID
     * @param data         隧道配置
     * @return 保存后的配置
     */
    ReturnResult<DataSourceTunnelVO> save(Long datasourceId, DataSourceTunnelDTO data);

    /**
     * 删除数据源的隧道配置并关闭已打开的隧道。
     *
     * @param datasourceId 数据源 ID
     * @return 是否删除成功
     */
    ReturnResult<Boolean> delete(Long datasourceId);
}
