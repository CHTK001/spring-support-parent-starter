package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 主机文件批量删除结果。
 *
 * <p>逐个删除并逐个回报：删一半失败时前端要能看到「哪几个没删掉、为什么」，
 * 不能笼统地提示「删除失败」让用户自己猜。</p>
 *
 * @param succeeded 成功条数
 * @param failed    失败条数
 * @param failures  失败明细（{@code key: 原因}）
 * @author CH
 * @since 4.0.0.62
 */
public record HostFileDeleteVO(
        int succeeded,
        int failed,
        List<String> failures
) {

    /**
     * 规范构造器：失败明细做防御性拷贝。
     *
     * @param succeeded 成功条数
     * @param failed    失败条数
     * @param failures  失败明细
     */
    public HostFileDeleteVO {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
