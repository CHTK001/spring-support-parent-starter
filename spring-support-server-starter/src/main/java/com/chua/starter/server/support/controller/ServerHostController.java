package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.service.ServerHostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器主机接口
 * <p>
 * 提供主机台账 CRUD、启停、连通性测试与身份绑定管理。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-主机", description = "主机台账管理")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/host")
@RequiredArgsConstructor
public class ServerHostController {

    /** 主机服务 */
    private final ServerHostService serverHostService;

    /**
     * 新增主机
     *
     * @param host 主机
     * @return 结果
     */
    @Operation(summary = "新增主机")
    @PostMapping
    public ReturnResult<MsServerHost> create(@RequestBody MsServerHost host) {
        return serverHostService.create(host);
    }

    /**
     * 更新主机
     *
     * @param id   主键
     * @param host 主机
     * @return 结果
     */
    @Operation(summary = "更新主机")
    @PutMapping("/{id}")
    public ReturnResult<MsServerHost> update(@PathVariable Long id, @RequestBody MsServerHost host) {
        return serverHostService.update(id, host);
    }

    /**
     * 删除主机
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除主机")
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return serverHostService.delete(id);
    }

    /**
     * 查询单个主机
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "查询单个主机")
    @GetMapping("/{id}")
    public ReturnResult<MsServerHost> get(@PathVariable Long id) {
        return serverHostService.get(id);
    }

    /**
     * 条件查询主机列表
     *
     * @param keyword 关键词，可为空
     * @param groupId 分组 标识，可为空
     * @param status  启用状态，可为空
     * @return 主机列表
     * @param false false
     */
    @Operation(summary = "条件查询主机")
    @PostMapping("/query")
    public ReturnResult<List<MsServerHost>> query(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long groupId,
                                                  @RequestParam(required = false) Integer status) {
        return serverHostService.query(keyword, groupId, status);
    }

    /**
     * 列表查询主机（获取，兼容前端列表页）
     *
     * <p>前端主机列表页使用 {@code GET /host} 并携带 {@code keyword/groupId/status} 过滤，
     * 此处与 {@code POST /query} 行为一致，仅 HTTP 方法不同。</p>
     *
     * @param keyword 关键词，可为空
     * @param groupId 分组 标识，可为空
     * @param status  启用状态，可为空
     * @return 主机列表
     * @param false false
     */
    @Operation(summary = "列表查询主机（GET）")
    @GetMapping
    public ReturnResult<List<MsServerHost>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long groupId,
                                                 @RequestParam(required = false) Integer status) {
        return serverHostService.query(keyword, groupId, status);
    }

    /**
     * 启停主机
     *
     * @param id      主键
     * @param enabled 是否启用
     * @return 结果
     */
    @Operation(summary = "启停主机")
    @PutMapping("/{id}/status")
    public ReturnResult<Void> updateStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return serverHostService.updateStatus(id, enabled);
    }

    /**
     * 连通性测试
     *
     * @param id 主键
     * @return 测试输出（uname 等）
     */
    @Operation(summary = "连通性测试")
    @PostMapping("/{id}/test-connect")
    public ReturnResult<String> testConnect(@PathVariable Long id) {
        return serverHostService.testConnect(id);
    }

    /**
     * Prometheus 凭据校验：GET 目标主机 /metrics 端点
     *
     * @param id 主机主键
     * @return 校验结果
     */
    @Operation(summary = "Prometheus 凭据校验")
    @PostMapping("/{id}/test-prometheus")
    public ReturnResult<String> testPrometheus(@PathVariable Long id) {
        return serverHostService.testPrometheus(id);
    }

    /**
     * 绑定身份
     *
     * @param id         主机 标识
     * @param identityId 身份 标识
     * @param sort       优先级，可为空
     * @return 结果
     * @param @PathVariable("id" @路径变量("标识"
     */
    @Operation(summary = "绑定登录身份")
    @PostMapping("/{id}/identity/{identityId}")
    public ReturnResult<Void> bindIdentity(@PathVariable("id") Long id,
                                           @PathVariable("identityId") Long identityId,
                                           @RequestParam(required = false) Integer sort) {
        return serverHostService.bindIdentity(id, identityId, sort);
    }

    /**
     * 解除身份绑定
     *
     * @param id         主机 标识
     * @param identityId 身份 标识
     * @return 结果
     * @param @PathVariable("id" @路径变量("标识"
     */
    @Operation(summary = "解除登录身份")
    @DeleteMapping("/{id}/identity/{identityId}")
    public ReturnResult<Void> unbindIdentity(@PathVariable("id") Long id,
                                             @PathVariable("identityId") Long identityId) {
        return serverHostService.unbindIdentity(id, identityId);
    }

    /**
     * 查询已绑定身份 标识 列表
     *
     * @param id 主机 标识
     * @return 身份 标识 列表（按优先级）
     */
    @Operation(summary = "查询已绑定身份")
    @GetMapping("/{id}/identities")
    public ReturnResult<List<Long>> listIdentities(@PathVariable Long id) {
        return serverHostService.listBoundIdentityIds(id);
    }

    /**
     * 硬件探测：SSH 连上目标主机自动采集 CPU/内存/磁盘/系统版本/IP
     *
     * @param id 主机 标识
     * @return 更新后的主机（含硬件信息）
     */
    @Operation(summary = "硬件探测")
    @PostMapping("/{id}/detect")
    public ReturnResult<MsServerHost> detect(@PathVariable Long id) {
        return serverHostService.detectHardware(id);
    }

    /**
     * 刷新公网 IP：探测目标主机出口公网 IP 并回写
     *
     * @param id 主机 标识
     * @return 刷新后的公网 IP
     */
    @Operation(summary = "刷新公网IP")
    @PostMapping("/{id}/refresh-public-ip")
    public ReturnResult<String> refreshPublicIp(@PathVariable Long id) {
        return serverHostService.refreshPublicIp(id);
    }

    /**
     * 批量同步主机资产（虚拟线程并发 SSH 探测，主机/硬件/IP 信息同步回资产表）
     * <p>
     * 仅当探测到的公网 IP 或内网 IP 与库中不一致时触发更新并记录变更明细；
     * 未变化的主机跳过，不重复回写。
     * </p>
     *
     * @param hostIds 目标主机 ID 列表（可选；为空则同步全部启用主机）
     * @return 同步结果概要（total/updated/unchanged/failed/changes）
     */
    @Operation(summary = "批量同步主机资产")
    @PostMapping("/sync")
    public ReturnResult<java.util.Map<String, Object>> syncHosts(@RequestBody(required = false) java.util.List<Long> hostIds) {
        return serverHostService.syncHosts(hostIds);
    }

    /**
     * 端口扫描：依赖本机 nmap 对目标主机做 TCP 连接扫描，解析开放端口并回写资产表
     *
     * @param id 主机 标识
     * @return 更新后的主机（含端口扫描结果）
     */
    @Operation(summary = "端口扫描")
    @PostMapping("/{id}/scan-ports")
    public ReturnResult<MsServerHost> scanPorts(@PathVariable Long id) {
        return serverHostService.scanPorts(id);
    }

    /**
     * 导出主机列表（CSV 下载）
     *
     * @param keyword 关键词，可为空
     * @param groupId 分组 标识，可为空
     * @return CSV 文件流
     * @param false false
     */
    @Operation(summary = "导出主机列表(CSV)")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long groupId) {
        ReturnResult<List<MsServerHost>> result = serverHostService.query(keyword, groupId, null);
        List<MsServerHost> list = result.getData();
        if (list == null) {
            list = java.util.List.of();
        }
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM，Excel 打开不乱码
        sb.append("ID,名称,编码,地址,端口,系统类型,系统版本,CPU核心,内存(MB),磁盘(GB),公网IP,内网IP,标签,描述,状态,在线\n");
        for (MsServerHost h : list) {
            sb.append(joinCsv(h.getId())).append(',');
            sb.append(joinCsv(h.getServerHostName())).append(',');
            sb.append(joinCsv(h.getServerHostCode())).append(',');
            sb.append(joinCsv(h.getServerHostAddress())).append(',');
            sb.append(joinCsv(h.getServerHostPort())).append(',');
            sb.append(joinCsv(h.getServerHostOsType())).append(',');
            sb.append(joinCsv(h.getServerHostOsVersion())).append(',');
            sb.append(joinCsv(h.getServerHostCpuCores())).append(',');
            sb.append(joinCsv(h.getServerHostMemoryMb())).append(',');
            sb.append(joinCsv(h.getServerHostDiskGb())).append(',');
            sb.append(joinCsv(h.getServerHostPublicIp())).append(',');
            sb.append(joinCsv(h.getServerHostPrivateIp())).append(',');
            sb.append(joinCsv(h.getServerHostTags())).append(',');
            sb.append(joinCsv(h.getServerHostDescription())).append(',');
            sb.append(h.getServerHostStatus() != null && h.getServerHostStatus() == 1 ? "启用" : "停用").append(',');
            sb.append(h.getServerHostOnline() != null && h.getServerHostOnline() == 1 ? "在线" : "离线").append('\n');
        }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=hosts-" + System.currentTimeMillis() + ".csv")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .body(bytes);
    }

    /**
     * CSV 字段转义（含逗号/引号/换行时加引号）
     *
     * @param value 字段值
     * @return 转义后的 CSV 字段
     */
    private String joinCsv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
