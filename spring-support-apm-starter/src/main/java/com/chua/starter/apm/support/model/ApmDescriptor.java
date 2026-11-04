package com.chua.starter.apm.support.model;

import java.util.List;

/**
 * 前端「有没有 APM」的唯一判据。
 * <p>
 * {@code /api/apm/v1/status} 直接返回本结构：{@link #present()} 为 false 时，
 * 页面必须渲染「当前服务器未接入 APM」的降级视图，而不是空白或报错。
 * {@link #candidates()} 把所有已探测到的引擎（含不可用的）一起给出来，
 * 方便运维一眼看出「装了但连不上」和「压根没装」的区别。
 * </p>
 *
 * @param present       是否选中了可用的 APM
 * @param enabled       模块是否被配置启用
 * @param name          选中引擎名（{@code skywalking} / {@code hotspot} / {@code opentelemetry}）
 * @param version       引擎/后端版本
 * @param mode          运行模式：agent / remote / none
 * @param endpoint      后端地址（进程内引擎为 {@code local}）
 * @param capabilities  能力清单
 * @param message       说明/失败原因
 * @param candidates    全部候选引擎及其可用性
 * @param checkedAt     探测时间毫秒戳
 * @author CH
 * @since 4.0.0.51
 */
public record ApmDescriptor(boolean present,
                            boolean enabled,
                            String name,
                            String version,
                            String mode,
                            String endpoint,
                            List<String> capabilities,
                            String message,
                            List<ApmCandidate> candidates,
                            long checkedAt) {

    /**
     * 候选引擎探测结果
     *
     * @param name      引擎名
     * @param available 是否可用
     * @param mode      运行模式
     * @param version   版本
     * @param endpoint  后端地址
     * @param message   说明/失败原因
     * @author CH
     * @since 4.0.0.51
     */
    public record ApmCandidate(String name,
                               boolean available,
                               String mode,
                               String version,
                               String endpoint,
                               String message) {
    }

    /**
     * 构建「无 APM」描述
     *
     * @param enabled    模块是否启用
     * @param message    原因
     * @param candidates 候选列表
     * @return 描述
     */
    public static ApmDescriptor absent(boolean enabled, String message, List<ApmCandidate> candidates) {
        return new ApmDescriptor(false, enabled, null, "unknown", "none", null,
                List.of(), message, candidates, System.currentTimeMillis());
    }
}
