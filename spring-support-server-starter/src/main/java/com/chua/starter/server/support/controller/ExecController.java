package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.entity.MsExecJob;
import com.chua.starter.server.support.entity.MsExecLog;
import com.chua.starter.server.support.service.ExecService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 批量执行接口：批量命令、文件分发与计划任务
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-批量作业", description = "批量命令/文件分发/cron 计划任务")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/exec")
@RequiredArgsConstructor
public class ExecController {

    /** 批量执行服务 */
    private final ExecService execService;

    /**
     * 批量执行命令
     *
     * @param body 请求体：主机标识 / 命令 / 超时seconds
     * @return 各主机日志
     */
    @Operation(summary = "批量执行命令")
    @PostMapping("/command")
    public ReturnResult<List<MsExecLog>> runCommand(@RequestBody Map<String, Object> body) {
        List<Long> hostIds = parseLongList(body.get("hostIds"));
        String command = (String) body.get("command");
        Integer timeout = toInteger(body.get("timeoutSeconds"));
        return execService.runCommand(hostIds, command, timeout, null);
    }

    /**
     * 文件分发
     *
     * @param hostIds   主机 标识 集合（逗号分隔）
     * @param directory 目标目录
     * @param file      文件
     * @return 各主机日志
     * @param @RequestParam("hostIds" @请求参数("主机标识"
     */
    @Operation(summary = "文件分发")
    @PostMapping("/file")
    public ReturnResult<List<MsExecLog>> distributeFile(@RequestParam("hostIds") String hostIds,
                                                        @RequestParam(required = false) String directory,
                                                        @RequestParam("file") MultipartFile file) {
        return execService.distributeFile(parseLongCsv(hostIds), directory, file);
    }

    /**
     * 新增计划任务
     *
     * @param job 任务
     * @return 结果
     */
    @Operation(summary = "新增计划任务")
    @PostMapping("/job")
    public ReturnResult<MsExecJob> createJob(@RequestBody MsExecJob job) {
        return execService.createJob(job);
    }

    /**
     * 更新计划任务
     *
     * @param id  主键
     * @param job 任务
     * @return 结果
     */
    @Operation(summary = "更新计划任务")
    @PutMapping("/job/{id}")
    public ReturnResult<MsExecJob> updateJob(@PathVariable Long id, @RequestBody MsExecJob job) {
        return execService.updateJob(id, job);
    }

    /**
     * 删除计划任务
     *
     * @param id 主键
     * @return 结果
     */
    @Operation(summary = "删除计划任务")
    @DeleteMapping("/job/{id}")
    public ReturnResult<Void> deleteJob(@PathVariable Long id) {
        return execService.deleteJob(id);
    }

    /**
     * 计划任务列表
     *
     * @return 任务列表
     */
    @Operation(summary = "计划任务列表")
    @GetMapping("/job")
    public ReturnResult<List<MsExecJob>> listJobs() {
        return execService.listJobs();
    }

    /**
     * 立即触发任务
     *
     * @param id 主键
     * @return 执行日志
     */
    @Operation(summary = "立即触发任务")
    @PostMapping("/job/{id}/run")
    public ReturnResult<List<MsExecLog>> runJobNow(@PathVariable Long id) {
        return execService.runJobNow(id);
    }

    /**
     * 执行日志查询
     *
     * @param type   类型过滤，可为空
     * @param hostId 主机过滤，可为空
     * @return 日志列表
     * @param false false
     */
    @Operation(summary = "执行日志查询")
    @GetMapping("/log")
    public ReturnResult<List<MsExecLog>> listLogs(@RequestParam(required = false) String type,
                                                  @RequestParam(required = false) Long hostId) {
        return execService.listLogs(type, hostId);
    }

    /**
     * 解析对象为长整型列表（JSON 数组或字符串）
     *
     * @param value 原始值
     * @return 长整型列表，非法输入返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<Long> parseLongList(Object value) {
        if (value instanceof List<?> rawList) {
            List<Long> ids = new java.util.ArrayList<>(rawList.size());
            for (Object item : rawList) {
                Long parsed = toLong(item);
                if (parsed != null) {
                    ids.add(parsed);
                }
            }
            return ids;
        }
        return parseLongCsv(value == null ? "" : value.toString());
    }

    /**
     * 解析逗号分隔的主机 标识
     *
     * @param csv 逗号分隔串
     * @return 长整型列表
     */
    private List<Long> parseLongCsv(String csv) {
        List<Long> ids = new java.util.ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            Long parsed = toLong(part.trim());
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        return ids;
    }

    /**
     * 安全转换长整型
     *
     * @param value 原始值
     * @return 长整型或 空
     */
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全转换整型
     *
     * @param value 原始值
     * @return 整型或 空
     */
    private Integer toInteger(Object value) {
        Long parsed = toLong(value);
        return parsed == null ? null : parsed.intValue();
    }
}
