package com.chua.starter.monitor.support.spi;

import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorTask;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集结果模型
 * <p>
   * 提供者 输出的统一结构, 包含原始输出与解析后的指标点列表, 调度器据此写入数据库。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Data
public class CollectResult {

    /**
     * 所属任务
     */
    private MsMonitorTask task;

    /**
     * 所属主机
     */
    private MsMonitor monitor;

    /**
     * 原始输出文本(供调试/AI 分析)
     */
    private String rawOutput;

    /**
     * 解析后的指标点
     */
    private List<MetricPoint> points = new ArrayList<>();

    /**
     * 采集时间
     */
    private LocalDateTime collectTime = LocalDateTime.now();

    /**
     * 是否成功
     */
    private boolean success = true;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 添加一个指标点
     *
     * @param point 指标点
     * @return 当前 collect结果
     */
    public CollectResult addPoint(MetricPoint point) {
        if (this.points == null) {
            this.points = new ArrayList<>();
        }
        this.points.add(point);
        return this;
    }

    /**
     * 标记失败
     *
     * @param errorMessage 错误信息
     * @return 当前 collect结果
     */
    public CollectResult fail(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
        return this;
    }
}