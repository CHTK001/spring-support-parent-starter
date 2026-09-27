package com.chua.starter.job.support.manage.vo;

import lombok.Data;

import java.util.List;

/**
 * 执行器节点出参（「远程作业」页用）。
 *
 * <p>数据来自执行器注册上报（{@code job-reg} / {@code job-handlers}），
 * 节点断开后立即从列表消失。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobNodeVO {

    /**
     * 同步通道客户端标识
     */
    private String clientId;

    /**
     * 执行器自述节点标识（通常带进程号）
     */
    private String nodeId;

    /**
     * 角色（EXECUTOR 等）
     */
    private String role;

    /**
     * 调度中心长链地址（执行器侧配置，如 tcp://127.0.0.1:18081）
     */
    private String serverUrl;

    /**
     * 处理器清单
     */
    private List<String> handlers;

    /**
     * 处理器数量
     */
    private int handlerCount;

    /**
     * 在线状态（当前列表只出在线节点，恒为 true）
     */
    private boolean online = true;

    /**
     * 首次登记时间（毫秒）
     */
    private long registeredAt;
}
