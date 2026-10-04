package com.chua.starter.job.support.manage.vo;

import java.util.List;

/**
 * 执行器节点出参（「远程作业」页用）。
 *
 * <p>数据来自执行器注册上报（{@code job-reg} / {@code job-handlers}），
 * 节点断开后立即从列表消失。</p>
 *
 * @param clientId     同步通道客户端标识
 * @param nodeId       执行器自述节点标识（通常带进程号）
 * @param role         角色（EXECUTOR 等）
 * @param serverUrl    调度中心长链地址（执行器侧配置，如 tcp://127.0.0.1:18081）
 * @param handlers     处理器清单
 * @param handlerCount 处理器数量
 * @param online       在线状态（当前列表只出在线节点，恒为 true）
 * @param registeredAt 首次登记时间（毫秒）
 * @since 4.0.0.42
 * @author CH
 */
public record JobNodeVO(
        String clientId,
        String nodeId,
        String role,
        String serverUrl,
        List<String> handlers,
        int handlerCount,
        boolean online,
        long registeredAt) {

    /**
     * 规范化集合组件，阻断外部别名写入；保留 null 语义。
     */
    public JobNodeVO {
        handlers = handlers == null ? null : List.copyOf(handlers);
    }
}
