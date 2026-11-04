package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 服务实例列表项。
 *
 * @param id            实例 ID
 * @param serviceName   所属服务
 * @param name          实例名（hostname@pid 或 uuid）
 * @param language      语言（JAVA / GO …）
 * @param heartbeatTime 最后心跳毫秒戳，未知填 0
 * @param tags          属性列表（ip / os / process 等）
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingInstanceVO(String id,
                                   String serviceName,
                                   String name,
                                   String language,
                                   long heartbeatTime,
                                   List<TagVO> tags) {
}
