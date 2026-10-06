package com.chua.starter.datasourcehub.support.model;

import java.time.LocalDateTime;

/**
 * Nginx 实时运行态（SSE 推送用）。
 *
 * <p><strong>为什么不直接推 {@link NginxOverviewVO}</strong>：总览一次要读配置目录、
 * 日志清单、版本、监听端口、语法检查，是给「打开页面」用的重查询。SSE 每 30s 推一次，
 * 按总览的口径推会把被管主机打穿（本项目一个数据源就是一条 SSH 短连接 + 远端 fork）。
 * 本 VO 只含趋势与状态胶囊真正要用的字段，SSH 往返与总览等价，但省掉了
 * 目录遍历与 {@code nginx -t}。</p>
 *
 * <p><strong>口径与总览一致</strong>：{@code running} 走 {@code detectRunning}，
 * {@code uptimeText} 走 {@code uptimeText}，活跃连接取自 {@code stub_status}，
 * 三者都复用总览的同一份实现，避免「总览说运行中、推送说已停止」这种自相矛盾。</p>
 *
 * <p>取不到值时对应字段为 null，由前端显示「—」，<strong>不填 0</strong>：
 * 填 0 会在实时态胶囊上凭空出现「已停止」，把「没读到」误报成「真的停了」。</p>
 *
 * @param datasourceId      数据源 id
 * @param running           运行态：RUNNING / STOPPED / UNKNOWN
 * @param masterPid         master 进程 pid，取不到为 null
 * @param uptimeText        已运行时长文案
 * @param activeConnections 活跃连接数，取不到为 null
 * @param statusAvailable   是否成功读到 stub_status
 * @param collectedAt       本次采集时刻
 * @param message           面向用户的说明（异常时说明原因）
 * @author CH
 * @since 4.0.0
 */
public record NginxRuntimeVO(
        Long datasourceId,
        String running,
        Long masterPid,
        String uptimeText,
        Long activeConnections,
        boolean statusAvailable,
        LocalDateTime collectedAt,
        String message
) {
}
