package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.DeletedFileReleaseVO;
import com.chua.starter.monitor.support.model.HostProfileVO;
import com.chua.starter.monitor.support.model.HostResourceSummaryVO;
import com.chua.starter.monitor.support.model.MountRequestVO;
import com.chua.starter.monitor.support.model.SystemConfigApplyVO;
import com.chua.starter.monitor.support.service.HostProfileService;
import com.chua.starter.monitor.support.service.HostSummarySseService;
import com.chua.starter.server.support.constants.ServerConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * 资源摘要推送服务（状态条的句柄 / inode 走 SSE，不再由页面轮询）
     */
    private final HostSummarySseService summarySseService;

    /**
     * 底部状态条资源摘要（一次性拉取）。
     *
     * @param hostId 主机标识
     * @return 句柄数与根分区 inode 使用率
     */
    @Operation(summary = "主机资源摘要", description = "句柄数与根分区 inode 使用率，供状态条一次性拉取")
    @GetMapping("/{hostId}/summary")
    public ReturnResult<HostResourceSummaryVO> summary(@PathVariable Long hostId) {
        return profileService.summary(hostId);
    }

    /**
     * 订阅资源摘要推送：订阅后立即推一次，之后每 30 秒推一次（经主机资产 SSE 下发）。
     *
     * @param hostId   主机标识
     * @param clientId 前端 SSE 连接标识
     * @return 是否订阅成功
     */
    @Operation(summary = "订阅主机资源摘要", description = "经主机资产 SSE 的 host-summary 事件定向推送")
    @PostMapping("/{hostId}/summary/subscribe")
    public ReturnResult<Void> subscribeSummary(@PathVariable Long hostId, @RequestParam String clientId) {
        summarySseService.subscribe(clientId, hostId);
        return ReturnResult.ok();
    }

    /**
     * 取消资源摘要订阅。
     *
     * @param clientId 前端 SSE 连接标识
     * @return 是否取消成功
     */
    @Operation(summary = "取消主机资源摘要订阅", description = "切换主机或离开页面时调用")
    @DeleteMapping("/summary/subscribe")
    public ReturnResult<Void> unsubscribeSummary(@RequestParam String clientId) {
        summarySseService.unsubscribe(clientId);
        return ReturnResult.ok();
    }

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
     * @param section 分区：ports/processes/services/disks/network/env/firewall/mounts/system-configs/deleted-files
     * @param page    页码（仅进程/服务/端口/防火墙生效）
     * @param size    每页条数（仅进程/服务/端口/防火墙生效）
     * @param keyword 关键字（仅进程/服务/端口/防火墙生效，跨页命中）
     * @return 分区数据
     */
    @Operation(summary = "主机画像分区",
            description = "监听端口/进程/服务/磁盘/网卡/环境变量/防火墙/挂载盘/系统配置/已删除文件，"
                    + "进程与服务支持关键字检索")
    @GetMapping("/{hostId}/{section}")
    public ReturnResult<?> section(@PathVariable("hostId") Long hostId,
                                   @PathVariable("section") String section,
                                   @RequestParam(value = "page", required = false) Integer page,
                                   @RequestParam(value = "size", required = false) Integer size,
                                   @RequestParam(value = "keyword", required = false) String keyword) {
        String name = section == null ? "" : section.trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case HostProfileService.SECTION_PORTS -> profileService.ports(hostId, page, size, keyword);
            case HostProfileService.SECTION_PROCESSES -> profileService.processes(hostId, page, size, keyword);
            case HostProfileService.SECTION_SERVICES -> profileService.services(hostId, page, size, keyword);
            case HostProfileService.SECTION_DISKS -> profileService.disks(hostId);
            case HostProfileService.SECTION_NETWORK -> profileService.network(hostId);
            case HostProfileService.SECTION_ENV -> profileService.env(hostId);
            case HostProfileService.SECTION_FIREWALL -> profileService.firewall(hostId, page, size, keyword);
            case HostProfileService.SECTION_MOUNTS -> profileService.mounts(hostId);
            case HostProfileService.SECTION_SYSTEM_CONFIGS -> profileService.systemConfigs(hostId);
            case HostProfileService.SECTION_DELETED_FILES -> profileService.deletedFiles(hostId);
            default -> ReturnResult.error("不支持的画像分区: " + section);
        };
    }

    /**
     * 修改一项系统配置（写后回读确认）。
     *
     * @param hostId  主机标识
     * @param request 配置键与值
     * @return 成功时为结果说明
     */
    @Operation(summary = "修改主机系统配置", description = "按白名单校验后下发 sysctl 并持久化，写后回读确认")
    @PostMapping("/{hostId}/system-configs")
    public ReturnResult<String> applySystemConfig(@PathVariable("hostId") Long hostId,
                                                  @RequestBody SystemConfigApplyVO request) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        return profileService.applySystemConfig(hostId, request.key(), request.value());
    }

    /**
     * 挂载一个设备 / 卷。
     *
     * @param hostId  主机标识
     * @param request 设备 / 挂载点 / 文件系统类型
     * @return 成功时为结果说明
     */
    @Operation(summary = "挂载设备", description = "Linux 走 mount、Windows 走盘符；下发后由通道确认")
    @PostMapping("/{hostId}/mounts/mount")
    public ReturnResult<String> mount(@PathVariable("hostId") Long hostId,
                                      @RequestBody MountRequestVO request) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        return profileService.mount(hostId, request.source(), request.target(), request.fsType());
    }

    /**
     * 卸载一个挂载点 / 盘符。
     *
     * @param hostId  主机标识
     * @param request 挂载点
     * @return 成功时为结果说明
     */
    @Operation(summary = "卸载挂载点", description = "Linux 走 umount、Windows 走盘符；下发后由通道确认")
    @PostMapping("/{hostId}/mounts/unmount")
    public ReturnResult<String> unmount(@PathVariable("hostId") Long hostId,
                                        @RequestBody MountRequestVO request) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        return profileService.unmount(hostId, request.target());
    }

    /**
     * 释放已删除文件占用的空间（截断描述符，不杀进程）。
     *
     * @param hostId  主机标识
     * @param request 进程号与描述符
     * @return 成功时为结果说明
     */
    @Operation(summary = "释放已删除文件", description = "把进程仍持有的已删除文件描述符截断为 0，不杀进程")
    @PostMapping("/{hostId}/deleted-files/release")
    public ReturnResult<String> releaseDeletedFile(@PathVariable("hostId") Long hostId,
                                                   @RequestBody DeletedFileReleaseVO request) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        return profileService.releaseDeletedFile(hostId, request.pid(), request.fd());
    }
}
