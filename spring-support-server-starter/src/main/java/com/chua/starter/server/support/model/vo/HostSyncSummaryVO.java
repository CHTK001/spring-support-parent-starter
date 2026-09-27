package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 批量主机资产同步结果概要
 * <p>
 * 三档计数互斥且满足 {@code total = updated + unchanged + failed}，
 * {@code changes} 只收录 IP 真正变化的主机；键名与历史 Map 载荷一致。
 * </p>
 *
 * @param total     本次参与同步的主机数
 * @param updated   有字段回写的主机数
 * @param unchanged 探测值与库中一致、跳过回写的主机数
 * @param failed    探测或回写失败的主机数
 * @param changes   IP 变更明细
 * @author CH
 * @since 4.0.0.42
 */
public record HostSyncSummaryVO(
        int total,
        int updated,
        int unchanged,
        int failed,
        List<HostSyncChangeVO> changes
) {

    /**
     * 规范构造器：IP 变更明细做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。唯一构造点传入的明细非空且元素非空，
     * 故可直接使用 {@code List.copyOf}。</p>
     *
     * @param total     本次参与同步的主机数
     * @param updated   有字段回写的主机数
     * @param unchanged 探测值与库中一致、跳过回写的主机数
     * @param failed    探测或回写失败的主机数
     * @param changes   IP 变更明细
     */
    public HostSyncSummaryVO {
        changes = List.copyOf(changes);
    }
}
