package com.chua.starter.server.support.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Kubernetes 能力探测结果（工作台能力门控的数据源）
 *
 * <p>探测走 SSH 执行 {@code kubectl version --output=json}（已配置 kubeconfig 的
 * 控制节点可执行）。{@code installed=false} 时前端隐藏 K8s 域入口。</p>
 *
 * @param installed     kubectl 可用且集群可达
 * @param apiVersion    k8s API 版本，如 v1.31.2
 * @param serverVersion 服务端 Git 版本，如 v1.31.2
 * @param nodes         集群节点总数
 * @param readyNodes    就绪节点数
 * @author CH
 * @since 4.0.0.44
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkbenchK8sCapabilityVO(
        boolean installed,
        String apiVersion,
        String serverVersion,
        int nodes,
        int readyNodes
) {

    /**
     * 便捷构造：未检测到集群
     *
     * @return 未安装能力对象
     */
    public static WorkbenchK8sCapabilityVO notInstalled() {
        return new WorkbenchK8sCapabilityVO(false, null, null, 0, 0);
    }

    /**
     * 便捷构造：已安装
     *
     * @param apiVersion    API 版本
     * @param serverVersion 服务端版本
     * @param nodes         节点总数
     * @param readyNodes    就绪节点数
     * @return 已安装能力对象
     */
    public static WorkbenchK8sCapabilityVO installed(String apiVersion, String serverVersion,
                                                     int nodes, int readyNodes) {
        return new WorkbenchK8sCapabilityVO(true, apiVersion, serverVersion, nodes, readyNodes);
    }
}