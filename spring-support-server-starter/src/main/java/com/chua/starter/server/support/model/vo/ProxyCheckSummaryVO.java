package com.chua.starter.server.support.model.vo;

/**
 * 批量检测概要
 * <p>
 * 单条探测内部已回写探测结果，此处只汇总条数，供页面提示「检测了多少、还剩多少可用」。
 * </p>
 *
 * @param checked   已探测条数
 * @param reachable 其中可达条数
 * @author CH
 * @since 4.0.0.42
 */
public record ProxyCheckSummaryVO(
        int checked,
        int reachable
) {
}
