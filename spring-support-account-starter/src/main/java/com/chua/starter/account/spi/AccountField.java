package com.chua.starter.account.spi;

/**
 * 账号表单字段描述。
 *
 * <p>由 {@link AccountKindProvider#fields()} 声明，{@code GET /api/accounts/kinds}
 * 原样回传给前端；前端按描述渲染表单，新增种类不再需要改表单组件。</p>
 *
 * <p>{@code key} 取两类值：{@code sys_account} 的列名（{@code username} /
 * {@code endpoint} / {@code secret} …）或 {@code config} 里的键名
 * （{@code imapHost} / {@code templateCode} / {@code secretId} …），
 * 前端据此决定写回哪一层。</p>
 *
 * @param key         字段名（列名或 {@code config} 键名）
 * @param label       中文标题
 * @param required    是否必填（前端标星，保存前拦一次）
 * @param secret      是否敏感（输入框打码、接口不回显）
 * @param placeholder 占位提示
 * @author CH
 * @since 4.0.0.61
 */
public record AccountField(String key, String label, boolean required, boolean secret, String placeholder) {

    /**
     * 普通可选字段。
     *
     * @param key         字段名
     * @param label       中文标题
     * @param placeholder 占位提示
     * @return 字段描述
     */
    public static AccountField of(String key, String label, String placeholder) {
        return new AccountField(key, label, false, false, placeholder);
    }

    /**
     * 必填字段。
     *
     * @param key         字段名
     * @param label       中文标题
     * @param placeholder 占位提示
     * @return 字段描述
     */
    public static AccountField required(String key, String label, String placeholder) {
        return new AccountField(key, label, true, false, placeholder);
    }

    /**
     * 敏感字段（打码、不回显）。是否必填由种类自己判断，默认不必填：
     * 凭据可以先引用密钥库、再补明文。
     *
     * @param key         字段名
     * @param label       中文标题
     * @param placeholder 占位提示
     * @return 字段描述
     */
    public static AccountField secret(String key, String label, String placeholder) {
        return new AccountField(key, label, false, true, placeholder);
    }
}
