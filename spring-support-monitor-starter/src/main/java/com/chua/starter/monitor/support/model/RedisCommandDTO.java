package com.chua.starter.monitor.support.model;

import java.util.Objects;

/**
 * Redis 通用命令执行请求体（命令控制台）
 *
 * <p>命令整行按 shell 风格分词：支持双引号/单引号包裹含空格的参数
 * （双引号内可用反斜杠转义），与 redis-cli 的输入习惯一致。</p>
 *
 * @param datasourceId 数据源 ID
 * @param db           逻辑库序号，null 时用数据源默认库
 * @param command      命令整行（如 {@code JSON.GET "my key"}）
 * @author CH
 * @since 4.0.0.43
 */
public record RedisCommandDTO(
        Long datasourceId,
        Integer db,
        String command
) {

    /**
     * 规范构造器：命令去首尾空白。
     *
     * @param datasourceId 数据源 ID
     * @param db           逻辑库序号
     * @param command      命令整行
     */
    public RedisCommandDTO {
        command = command == null ? null : command.trim();
        Objects.requireNonNull(datasourceId, "datasourceId 不能为 null");
    }
}
