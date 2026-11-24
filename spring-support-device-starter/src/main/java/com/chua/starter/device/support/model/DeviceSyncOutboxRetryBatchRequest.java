package com.chua.starter.device.support.model;

import java.util.List;

/**
 * 推送队列（outbox）批量重试请求。
 *
 * @param ids 待重试的队列记录 ID 清单
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceSyncOutboxRetryBatchRequest(List<String> ids) {
}
