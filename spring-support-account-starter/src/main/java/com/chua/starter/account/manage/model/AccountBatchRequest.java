package com.chua.starter.account.manage.model;

import java.util.List;

/**
 * 批量操作请求体。
 *
 * <p>批量启用 / 停用 / 校验 / 删除共用同一个请求形状：只给账号标识清单，
 * 逐条执行、逐条汇报结果。清单为 {@code null} 或空时不执行任何操作。</p>
 *
 * @param accountIds 账号标识清单（重复项由服务层去重）
 * @author CH
 * @since 4.0.0.60
 */
public record AccountBatchRequest(List<String> accountIds) {
}
