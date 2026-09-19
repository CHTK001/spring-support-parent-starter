package com.chua.starter.project.support.service;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.project.support.constants.ProjectConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 发布项目管理实时推送服务
 * <p>
 * 复用 spring-支持-Socket-* 的 {@link SocketSessionTemplate}（Socket.IO 协议），
 * 广播项目部署/回滚进度。宿主未装配 Socket 时静默降级（对象提供者 为空）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class ProjectPushService {

    /** Socket 模板（可空） */
    private final ObjectProvider<SocketSessionTemplate> socketTemplates;

    /**
     * 构造器注入
     *
     * @param socketTemplates Socket 模板
     */
    public ProjectPushService(ObjectProvider<SocketSessionTemplate> socketTemplates) {
        this.socketTemplates = socketTemplates;
    }

    /**
     * 广播部署进度
     *
     * @param projectId 项目 标识
     * @param hostId    主机 标识，可为空（表示整体阶段）
     * @param action    操作类型 DEPLOY/UPGRADE/ROLLBACK
     * @param stage     阶段：校验/CLEAN/UPLOAD/EXTRACT/执行/启动/饰面
     * @param status    状态 RUNNING/成功/失败
     * @param message   描述
     * @param version   版本标识，可为空
     */
    public void pushProgress(Long projectId, Long hostId, String action, String stage,
                              String status, String message, String version) {
        SocketSessionTemplate template = socketTemplates.getIfAvailable();
        if (template == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>(8);
        data.put("projectId", projectId);
        data.put("hostId", hostId);
        data.put("action", action);
        data.put("stage", stage);
        data.put("status", status);
        data.put("message", message);
        data.put("version", version);
        data.put("timestamp", System.currentTimeMillis());
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("module", ProjectConstants.PUSH_MODULE);
        payload.put("event", ProjectConstants.PUSH_EVENT_DEPLOY_PROGRESS);
        payload.put("data", data);
        payload.put("dataId", projectId == null ? "" : String.valueOf(projectId));
        payload.put("timestamp", System.currentTimeMillis());
        try {
            template.broadcastObject("message", payload);
        } catch (Exception e) {
            log.warn("[project] 推送部署进度失败: {}", e.getMessage());
        }
    }

    /**
     * 广播项目指标实时值（守护/指标采集专用通道）
     *
     * @param projectId  项目 标识
     * @param hostId     主机 标识，可为空
     * @param metricName 指标名（process.mem.mb / process.cpu / port.alive / http.code）
     * @param value      指标值
     * @param collectTime 采集时间
     */
    public void pushProjectMetric(Long projectId, Long hostId, String metricName, double value,
                                   java.time.LocalDateTime collectTime) {
        SocketSessionTemplate template = socketTemplates.getIfAvailable();
        if (template == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>(8);
        data.put("projectId", projectId);
        data.put("hostId", hostId);
        data.put("metricName", metricName);
        data.put("value", value);
        data.put("collectTime", collectTime == null ? null : collectTime.toString());
        data.put("timestamp", System.currentTimeMillis());
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("module", ProjectConstants.PUSH_MODULE);
        payload.put("event", ProjectConstants.PUSH_EVENT_PROJECT_METRIC);
        payload.put("data", data);
        payload.put("dataId", projectId == null ? "" : String.valueOf(projectId));
        payload.put("timestamp", System.currentTimeMillis());
        try {
            template.broadcastObject("message", payload);
        } catch (Exception e) {
            log.warn("[project] 推送项目指标失败: {}", e.getMessage());
        }
    }

    /**
     * 广播项目实时日志流帧（共享采集器专用通道）
     *
     * @param projectId  项目 标识
     * @param hostId     主机 标识
     * @param content    日志增量内容（错误帧可为空串）
     * @param cursor     下一个读取游标
     * @param eof        是否已到文件末尾
     * @param truncated  本帧是否因超长被截断
     * @param message    错误原因（成功时为空串）
     */
    public void pushProjectLog(Long projectId, Long hostId, String content, long cursor,
                               boolean eof, boolean truncated, String message) {
        SocketSessionTemplate template = socketTemplates.getIfAvailable();
        if (template == null) {
            return;
        }
        Map<String, Object> data = new HashMap<>(8);
        data.put("projectId", projectId);
        data.put("hostId", hostId);
        data.put("content", content == null ? "" : content);
        data.put("cursor", cursor);
        data.put("eof", eof);
        data.put("truncated", truncated);
        data.put("message", message == null ? "" : message);
        data.put("timestamp", System.currentTimeMillis());
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("module", ProjectConstants.PUSH_MODULE);
        payload.put("event", ProjectConstants.PUSH_EVENT_PROJECT_LOG);
        payload.put("data", data);
        payload.put("dataId", projectId == null ? "" : String.valueOf(projectId));
        payload.put("timestamp", System.currentTimeMillis());
        try {
            template.broadcastObject("message", payload);
        } catch (Exception e) {
            log.warn("[project] 推送项目日志失败: {}", e.getMessage());
        }
    }
}
