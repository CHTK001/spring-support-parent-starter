package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 拓扑节点。
 *
 * @param id    节点 ID
 * @param name  节点名
 * @param type  节点类型（service / database / cache / queue / gateway …）
 * @param real  是否为真实服务（false 表示外部依赖等虚拟节点）
 * @param isSelf 是否为「当前服务」
 * @param layer 分层（可空）
 * @author CH
 * @since 4.0.0.51
 */
public record TopologyNodeVO(String id,
                             String name,
                             String type,
                             boolean real,
                             boolean isSelf,
                             String layer) {
}
