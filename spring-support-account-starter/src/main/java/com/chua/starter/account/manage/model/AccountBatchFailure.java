package com.chua.starter.account.manage.model;

/**
 * 批量操作中的单条失败。
 *
 * <p>批量操作不做整体回滚：能改的照改，改不了的在这里逐条说明原因，
 * 界面据此提示「成功 N 条、失败 M 条」，而不是一条失败就把整批打成全失败。</p>
 *
 * @param accountId 账号标识
 * @param code      机器可读错误码（{@code FORBIDDEN} / {@code NOT_FOUND} …）
 * @param message   人类可读失败原因
 * @author CH
 * @since 4.0.0.60
 */
public record AccountBatchFailure(String accountId, String code, String message) {
}
