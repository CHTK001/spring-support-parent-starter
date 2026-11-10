package com.chua.starter.device.support.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * webhook appKey 对外视图。
 *
 * <p>secret 只在创建接口里返回一次，列表接口该字段恒为空。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class AppKeyView {

    /** 主键 */
    private String id;

    /** 公开标识 */
    private String appKey;

    /** 名称 */
    private String name;

    /** 允许的主题 */
    private String scopes;

    /** 过期时间；为空表示永久有效 */
    private LocalDateTime expiresAt;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 明文密钥：仅创建时返回一次 */
    private String secret;
}
