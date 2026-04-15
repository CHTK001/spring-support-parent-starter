package com.chua.starter.spider.support.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 凭证引用对象（不存储明文密码）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpiderCredentialRef {

    /** 凭证 ID，引用外部凭证管理系统 */
    private String credentialId;

    /** 凭证类型（如 BASIC、COOKIE、TOKEN、SESSION） */
    private String credentialType;
}
