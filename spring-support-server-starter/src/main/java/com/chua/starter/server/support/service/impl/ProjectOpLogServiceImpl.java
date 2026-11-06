package com.chua.starter.server.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.text.json.Json;
import com.chua.starter.server.support.entity.MsServerProjectOpLog;
import com.chua.starter.server.support.mapper.MsServerProjectOpLogMapper;
import com.chua.starter.server.support.service.ProjectOpLogService;
import com.chua.starter.server.support.sse.ReleaseSseHub;
import com.chua.starter.server.support.util.ProjectOperationLabels;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 项目操作日志服务实现。
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectOpLogServiceImpl implements ProjectOpLogService {

    /**
     * 状态：进行中
     */
    private static final String STATUS_RUNNING = "RUNNING";

    /**
     * 状态：成功
     */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 状态：失败
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 操作日志 Mapper
     */
    private final MsServerProjectOpLogMapper opLogMapper;

    /**
     * 发布管理 SSE 通道（日志面板实时刷新）
     */
    private final ReleaseSseHub releaseSseHub;

    @Override
    public void recordStart(Long projectId, String projectName, Long releaseId, String operation, String taskId) {
        MsServerProjectOpLog row = new MsServerProjectOpLog();
        row.setProjectId(projectId);
        row.setProjectName(projectName);
        row.setReleaseId(releaseId);
        row.setOperation(operation);
        row.setOperationLabel(ProjectOperationLabels.label(operation));
        row.setStatus(STATUS_RUNNING);
        row.setTaskId(taskId);
        row.setOperatorName(currentOperator());
        row.setStartTime(LocalDateTime.now());
        opLogMapper.insert(row);
        publish(ReleaseSseHub.EVENT_OP_LOG, row);
    }

    @Override
    public void recordFinish(String taskId, boolean success, String message) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        MsServerProjectOpLog row = opLogMapper.selectOne(
                new LambdaQueryWrapper<MsServerProjectOpLog>()
                        .eq(MsServerProjectOpLog::getTaskId, taskId)
                        .last("limit 1"));
        if (row == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long duration = row.getStartTime() == null
                ? 0L
                : Math.max(0L, Duration.between(row.getStartTime(), now).toMillis());
        String text = StringUtils.hasText(message)
                ? message
                : (success ? "操作成功" : "操作失败");
        opLogMapper.update(null, new LambdaUpdateWrapper<MsServerProjectOpLog>()
                .eq(MsServerProjectOpLog::getId, row.getId())
                .set(MsServerProjectOpLog::getStatus, success ? STATUS_SUCCESS : STATUS_FAILED)
                .set(MsServerProjectOpLog::getMessage, text)
                .set(MsServerProjectOpLog::getEndTime, now)
                .set(MsServerProjectOpLog::getDurationMs, duration));
        row.setStatus(success ? STATUS_SUCCESS : STATUS_FAILED);
        row.setMessage(text);
        row.setEndTime(now);
        row.setDurationMs(duration);
        publish(ReleaseSseHub.EVENT_OP_LOG, row);
    }

    @Override
    public void recordOne(Long projectId, String projectName, Long releaseId,
                          String operation, boolean success, String message) {
        MsServerProjectOpLog row = new MsServerProjectOpLog();
        row.setProjectId(projectId);
        row.setProjectName(projectName);
        row.setReleaseId(releaseId);
        row.setOperation(operation);
        row.setOperationLabel(ProjectOperationLabels.label(operation));
        row.setStatus(success ? STATUS_SUCCESS : STATUS_FAILED);
        row.setMessage(StringUtils.hasText(message) ? message : (success ? "操作成功" : "操作失败"));
        row.setOperatorName(currentOperator());
        row.setStartTime(LocalDateTime.now());
        row.setEndTime(row.getStartTime());
        row.setDurationMs(0L);
        opLogMapper.insert(row);
        publish(ReleaseSseHub.EVENT_OP_LOG, row);
    }

    @Override
    public ReturnResult<IPage<MsServerProjectOpLog>> page(Long projectId, Long releaseId, String operation,
                                                          String status, String keyword, int page, int pageSize) {
        LambdaQueryWrapper<MsServerProjectOpLog> query = new LambdaQueryWrapper<>();
        if (projectId != null) {
            query.eq(MsServerProjectOpLog::getProjectId, projectId);
        }
        if (releaseId != null) {
            query.eq(MsServerProjectOpLog::getReleaseId, releaseId);
        }
        if (StringUtils.hasText(operation)) {
            query.eq(MsServerProjectOpLog::getOperation, operation);
        }
        if (StringUtils.hasText(status)) {
            query.eq(MsServerProjectOpLog::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            query.like(MsServerProjectOpLog::getProjectName, keyword.trim());
        }
        query.orderByDesc(MsServerProjectOpLog::getId);
        Page<MsServerProjectOpLog> result = opLogMapper.selectPage(
                new Page<>(Math.max(1, page), Math.max(1, Math.min(pageSize, 200))), query);
        return ReturnResult.ok(result);
    }

    /**
     * 推送一条操作日志事件（失败不影响主流程）。
     *
     * @param event 事件名
     * @param row   日志行
     */
    private void publish(String event, MsServerProjectOpLog row) {
        try {
            releaseSseHub.broadcast(event, Json.toJson(row));
        } catch (Exception e) {
            log.debug("[op-log] 推送失败: {}", e.getMessage());
        }
    }

    /**
     * 取当前操作人。
     *
     * <p>后端没有统一的登录态 API，优先读网关/前端透传的用户名头，
     * 拿不到就留空，不能因为取操作人失败影响操作本身。</p>
     *
     * @return 操作人；未知返回 null
     */
    private String currentOperator() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                for (String header : new String[]{"X-User-Name", "X-Username", "x-username"}) {
                    String value = request.getHeader(header);
                    if (StringUtils.hasText(value)) {
                        return value.trim();
                    }
                }
                if (request.getUserPrincipal() != null
                        && StringUtils.hasText(request.getUserPrincipal().getName())) {
                    return request.getUserPrincipal().getName();
                }
            }
        } catch (Exception e) {
            log.debug("[op-log] 读取操作人失败: {}", e.getMessage());
        }
        return null;
    }
}
