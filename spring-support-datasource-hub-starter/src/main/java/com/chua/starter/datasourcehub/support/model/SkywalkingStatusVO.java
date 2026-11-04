package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * 引擎探测状态。
 *
 * @param name         引擎标识（skywalking）
 * @param version      引擎/后端版本，未知填 {@code unknown}
 * @param available    是否可用
 * @param enabled      是否被配置启用
 * @param endpoint     后端地址
 * @param mode         引擎运行模式：agent（进程内）/ remote（远端后端）
 * @param capabilities 能力清单
 * @param message      说明/失败原因
 * @param checkedAt    探测时间毫秒戳
 * @author CH
 * @since 4.0.0.51
 */
public record SkywalkingStatusVO(String name,
                                 String version,
                                 boolean available,
                                 boolean enabled,
                                 String endpoint,
                                 String mode,
                                 List<String> capabilities,
                                 String message,
                                 long checkedAt) {
}
