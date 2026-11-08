package com.chua.starter.account.manage.model;

import java.util.List;

/**
 * 批量操作结果。
 *
 * @param total     请求条数（去重后）
 * @param succeeded 成功条数
 * @param failed    失败条数
 * @param failures  失败明细（顺序与请求一致）
 * @author CH
 * @since 4.0.0.60
 */
public record AccountBatchResult(int total, int succeeded, int failed, List<AccountBatchFailure> failures) {
}
