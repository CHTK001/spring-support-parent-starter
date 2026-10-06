package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Objects;

/**
 * Nginx 配置文件解析结果。
 *
 * @param path      配置文件路径
 * @param content   文件原始内容
 * @param root      语法树根节点（虚拟根，children 为顶层语句）
 * @param nodeCount 节点总数
 * @param serverCount  识别到的 server 区块数量
 * @param locationCount 识别到的 location 区块数量
 * @param upstreamCount 识别到的 upstream 区块数量
 * @param unknownCount 未登记元数据的指令数量
 * @param warnings  解析告警
 * @author CH
 * @since 4.0.0.42
 */
public record NginxConfigParseVO(
        String path,
        String content,
        NginxConfigNodeVO root,
        int nodeCount,
        int serverCount,
        int locationCount,
        int upstreamCount,
        int unknownCount,
        List<String> warnings
) {

    /**
     * 规范构造器：对解析告警做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。告警唯一来源是
     * {@code NginxParser#warnings()}，它返回解析器持有的 final ArrayList，
     * 元素均为拼接所得的提示文本，故不存在 null 集合或 null 元素。</p>
     *
     * @param warnings 解析告警，不能为 null
     */
    public NginxConfigParseVO {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings 不能为 null"));
    }
}
