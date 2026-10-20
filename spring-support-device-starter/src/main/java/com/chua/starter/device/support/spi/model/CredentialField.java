package com.chua.starter.device.spi.model;

/**
 * 凭据字段描述
 * <p>前端按厂商/平台动态渲染表单，新增厂商无需改前端。</p>
 *
 * @param key         字段名，例如 appKey、appSecret
 * @param label       展示名称
 * @param secret      是否敏感字段（前端用密码框、响应不回显）
 * @param required    是否必填
 * @param placeholder 输入提示
 * @since 4.0.0.42
 * @author CH
 */
public record CredentialField(
        String key,
        String label,
        boolean secret,
        boolean required,
        String placeholder) {
}
