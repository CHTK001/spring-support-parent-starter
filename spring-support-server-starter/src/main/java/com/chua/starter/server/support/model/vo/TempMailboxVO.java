package com.chua.starter.server.support.model.vo;

/**
 * 临时邮箱地址
 * <p>
 * 由 TEMP 来源服务商实时申请，字段名与历史 Map 载荷完全一致。
 * </p>
 *
 * @param email    临时邮箱地址
 * @param provider 服务商名（fce/luckmail/mailnest/mailtm）
 * @author CH
 * @since 4.0.0.42
 */
public record TempMailboxVO(
        String email,
        String provider
) {
}
