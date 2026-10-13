package com.chua.starter.monitor.support.model;

import java.util.Objects;

/**
 * Redis 通用命令执行结果（命令控制台）
 *
 * <p>reply 为归一化后的 RESP 回复：状态/批量字符串转 String、整数转 Long、
 * 数组递归转 List、Map（RESP3）转 LinkedHashMap，nil 保持 null，
 * 可直接经 JSON 序列化返回前端按形态渲染。</p>
 *
 * @param command 实际执行的命令（分词后以空格重连）
 * @param reply   归一化后的回复
 * @author CH
 * @since 4.0.0.43
 */
public record RedisCommandResultVO(
        String command,
        Object reply
) {

    /**
     * 规范构造器：回复允许为 null（nil 回复），命令必须非空。
     *
     * @param command 实际执行的命令
     * @param reply   归一化后的回复
     */
    public RedisCommandResultVO {
        Objects.requireNonNull(command, "command 不能为 null");
    }
}
