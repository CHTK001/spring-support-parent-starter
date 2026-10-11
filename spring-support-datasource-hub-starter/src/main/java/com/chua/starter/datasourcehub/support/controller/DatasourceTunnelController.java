package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.SysLog;
import com.chua.starter.datasourcehub.support.model.DataSourceTunnelVO;
import com.chua.starter.datasourcehub.support.model.dto.DataSourceTunnelDTO;
import com.chua.starter.datasourcehub.support.service.DatasourceTunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源 SSH 隧道配置接口（高级配置）。
 *
 * <p>用于「目标库在本机不可直连」的场景：配置一条隧道后，{@code /ms/datasource/{id}/test}
 * 会先确保隧道打开，再经隧道本地端口去连目标库并测延迟。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据源中心 - SSH 隧道", description = "内网不可直连时的隧道中转配置")
@RestController
@RequestMapping("/ms/datasource/tunnel")
@RequiredArgsConstructor
public class DatasourceTunnelController {

    /**
     * 隧道服务
     */
    private final DatasourceTunnelService tunnelService;

    /**
     * 查询数据源的隧道配置。
     *
     * @param datasourceId 数据源 ID
     * @return 隧道配置；未配置时 data 为 null
     */
    @Operation(summary = "查询数据源隧道配置")
    @GetMapping("/{datasourceId}")
    public ReturnResult<DataSourceTunnelVO> get(@PathVariable Long datasourceId) {
        return tunnelService.get(datasourceId);
    }

    /**
     * 保存数据源的隧道配置（存在则覆盖，一个数据源至多一条）。
     *
     * @param datasourceId 数据源 ID
     * @param data         隧道配置
     * @return 保存后的配置
     */
    @SysLog(name = "保存数据源隧道配置")
    @Operation(summary = "保存数据源隧道配置")
    @PutMapping("/{datasourceId}")
    public ReturnResult<DataSourceTunnelVO> save(@PathVariable Long datasourceId, @RequestBody DataSourceTunnelDTO data) {
        return tunnelService.save(datasourceId, data);
    }

    /**
     * 删除数据源的隧道配置并关闭隧道。
     *
     * @param datasourceId 数据源 ID
     * @return 是否删除成功
     */
    @SysLog(name = "删除数据源隧道配置")
    @Operation(summary = "删除数据源隧道配置")
    @DeleteMapping("/{datasourceId}")
    public ReturnResult<Boolean> delete(@PathVariable Long datasourceId) {
        return tunnelService.delete(datasourceId);
    }
}
