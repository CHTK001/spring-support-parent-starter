package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.HostProfileVO;
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

import java.util.Locale;

/**
 * 主机画像控制器
 * <p>
 * 画像页所有远端数据一律经采集通道（Agent / SSH / WinRM / 本机 OSHI）获取：概览与实时指标在
 * 成功后回写主机台账，通道不可达时把原因显式返回并记入 ms_server_host 的失败列，不返回空壳数据。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "主机画像", description = "基于采集通道的主机画像数据")
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
     * @param live    是否实时采集：默认 false，只读台账快照（进入画像不触发采集）；true 时经通道实时拉取
     * @param persist 实时采集时是否回写主机台账，默认 true（仅 live=true 生效）
     * @return 画像数据
     */
    @Operation(summary = "主机画像概览", description = "默认只读台账快照；live=true 实时采集基础信息与指标并回写台账")
    @GetMapping("/{hostId}")
    public ReturnResult<HostProfileVO> overview(@PathVariable Long hostId,
                                                @RequestParam(defaultValue = "false") boolean live,
                                                @RequestParam(defaultValue = "true") boolean persist) {
        return live ? profileService.refresh(hostId, persist) : profileService.snapshot(hostId);
    }

    /**
     * 画像列表分区。
     *
     * @param hostId  主机标识
     * @param section 分区：ports/processes/services/disks/network/env
     * @param page    页码（仅进程/服务生效）
     * @param size    每页条数（仅进程/服务生效）
     * @param keyword 关键字（仅进程/服务生效，跨页命中）
     * @return 分区数据
     */
    @Operation(summary = "主机画像分区", description = "监听端口/进程/服务/磁盘/网卡/环境变量，进程与服务支持关键字检索")
    @GetMapping("/{hostId}/{section}")
    public ReturnResult<?> section(@PathVariable Long hostId,
                                  @PathVariable String section,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size,
                                  @RequestParam(required = false) String keyword) {
        String name = section == null ? "" : section.trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case HostProfileService.SECTION_PORTS -> profileService.ports(hostId, page, size, keyword);
            case HostProfileService.SECTION_PROCESSES -> profileService.processes(hostId, page, size, keyword);
            case HostProfileService.SECTION_SERVICES -> profileService.services(hostId, page, size, keyword);
            case HostProfileService.SECTION_DISKS -> profileService.disks(hostId);
            case HostProfileService.SECTION_NETWORK -> profileService.network(hostId);
            case HostProfileService.SECTION_ENV -> profileService.env(hostId);
            case HostProfileService.SECTION_FIREWALL -> profileService.firewall(hostId, page, size, keyword);
            default -> ReturnResult.error("不支持的画像分区: " + section);
        };
    }
}
