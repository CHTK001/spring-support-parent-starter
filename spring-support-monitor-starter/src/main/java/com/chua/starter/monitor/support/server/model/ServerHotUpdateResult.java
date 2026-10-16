package com.chua.starter.monitor.support.server.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务管理 —— 热更新结果。
 *
 * <p>区分三种落地方式，便于前端提示「哪些改动即时生效、哪些需要重启」：</p>
 * <ul>
 *   <li>{@code hotApplied = true}：监听参数未变，仅重建过滤器链（不重启服务器）</li>
 *   <li>{@code restarted = true}：监听地址/端口/引擎变更，服务器已按新配置重启</li>
 *   <li>二者皆 假：节点未运行，定义已保存，等待下次启动生效</li>
 * </ul>
 *
 * <p>补充：节点未运行但 {@code enabled=1} 且本次监听参数变更（或节点处于错误态）
 * 时，会按新配置拉起，此时 {@code restarted = true}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "服务管理-热更新结果")
public class ServerHotUpdateResult {

    /**
     * 更新后的节点视图
    */
    @Schema(description = "更新后的节点视图")
    private ServerNodeView node;

    /**
     * 是否已热应用（未重启，仅过滤器链重建）
    */
    @Schema(description = "是否已热应用(未重启, 仅过滤器链重建)")
    private boolean hotApplied;

    /**
     * 是否已重启（监听参数变更触发）
    */
    @Schema(description = "是否已重启(监听参数变更触发)")
    private boolean restarted;

    /**
     * 本次实际生效的改动项描述
    */
    @Schema(description = "本次实际生效的改动项描述")
    private List<String> appliedItems = new ArrayList<>();

    /**
     * 结果说明文案
    */
    @Schema(description = "结果说明文案")
    private String message;

    /**
     * 追加一条生效项。
     *
     * @param item 生效项描述
     */
    public void addAppliedItem(String item) {
        if (appliedItems == null) {
            appliedItems = new ArrayList<>();
        }
        appliedItems.add(item);
    }
}
