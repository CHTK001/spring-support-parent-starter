package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 主机画像控制器
 * <p>
 * 画像页所有远端数据一律经 agent HTTP 获取：概览与实时指标在成功后回写主机台账，
 * agent 不可达时返回明确原因（同时记入 ms_server_host 的 agent 失败列），不再返回空壳数据。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "主机画像", description = "基于 Agent 的主机画像数据")
@RestController
@RequiredArgsConstructor
@RequestMapping(ServerConstants.API_PREFIX + "/host/profile")
public class HostProfileController {

    /**
     * 画像服务
    */
    private final HostProfileService profileService;

    /**
     * 概览 + 实时指标。
     *
     * @param hostId  主机标识
     * @param persist 是否回写主机台账，默认 true
     * @return 画像数据
     */
    @Operation(summary = "主机画像概览", description = "拉取 agent /info 与 /metrics，并回写主机台账")
    @GetMapping("/{hostId}")
    public ReturnResult<Map<String, Object>> overview(@PathVariable Long hostId,
                                                     @RequestParam(defaultValue = "true") boolean persist) {
        return profileService.refresh(hostId, persist);
    }

    /**
     * 画像列表分区。
     *
     * @param hostId  主机标识
     * @param section 分区：ports/processes/services/disks/network/env
     * @param page    页码（仅进程/服务生效）
     * @param size    每页条数（仅进程/服务生效）
     * @return 分区数据
     */
    @Operation(summary = "主机画像分区", description = "监听端口/进程/服务/磁盘/网卡/环境变量")
    @GetMapping("/{hostId}/{section}")
    public ReturnResult<List<Map<String, Object>>> section(@PathVariable Long hostId,
                                                          @PathVariable String section,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return profileService.section(hostId, section, page, size);
    }
}
