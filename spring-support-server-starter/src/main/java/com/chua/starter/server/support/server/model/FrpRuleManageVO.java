package com.chua.starter.server.support.server.model;

import lombok.Data;

import java.util.List;

/**
 * FRP 规则管理视图（服务端统管口径）。
 *
 * <p>服务管理页 FRP 专属面板的数据源：一个 FRP 节点聚合展示
 * 「在线客户端 + 暴露规则（提供方）+ 映射规则（访问方）」三类信息，
 * 规则明细由 FrpServer 内存规则表实时读取。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class FrpRuleManageVO {

    /**
     * 在线客户端标识列表
     */
    private List<String> clients;

    /**
     * 暴露规则（提供方：localPort 上的服务以 targetHost:targetPort 对外提供）
     */
    private List<FrpRuleItem> exposes;

    /**
     * 映射规则（访问方：本机监听 localPort，访问即到 targetHost:targetPort）
     */
    private List<FrpRuleItem> maps;

    /**
     * 单条 FRP 规则。
     *
     * @param ruleId     规则标识（客户端上报或用例方生成的 UUID，撤销时按它定位）
     * @param clientId   所属客户端标识（expose=提供方 / map=访问方）
     * @param targetHost 目标地址
     * @param targetPort 目标端口
     * @param localPort  本地端口（expose=本机服务端口 / map=本机监听端口）
     */
    public record FrpRuleItem(String ruleId, String clientId, String targetHost, int targetPort, int localPort) {
    }

    /**
     * 新建规则请求（服务端下推）。
     *
     * @param clientId   接收规则的客户端标识
     * @param type       规则类型：expose=暴露规则 / map=映射规则
     * @param targetHost 目标地址
     * @param targetPort 目标端口
     * @param localPort  本地端口
     */
    @Data
    public static class FrpRuleRequest {
        private String clientId;
        private String type;
        private String targetHost;
        private Integer targetPort;
        private Integer localPort;
    }
}