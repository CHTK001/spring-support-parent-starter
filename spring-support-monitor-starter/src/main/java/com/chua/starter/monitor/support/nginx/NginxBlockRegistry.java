package com.chua.starter.monitor.support.nginx;

import java.util.Set;

/**
 * 区块名注册表：判断一个名称是「区块」还是「叶子指令」。
 *
 * <p>解析阶段不需要这张表——{@code {} } 本身就是分界。真正需要它的是 UI 模式的
 * 「新增」操作：用户在表单里敲了一个名字，后端必须知道该生成
 * {@code location /api { }} 还是 {@code root /data;}。</p>
 *
 * <p>只收录 nginx 官方与常见第三方模块的区块名。名单之外的名称按指令处理——
 * 这与 nginx 自身的兜底行为一致（未知指令若带 {@code { }} 仍会被解析成区块，
 * 但表单新增时按指令生成更安全，因为指令不会造成块结构不平衡）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxBlockRegistry {

    /**
     * 已知区块名
     */
    private static final Set<String> BLOCKS = Set.of(
            "http", "events", "stream",
            "server", "location", "upstream", "types",
            "map", "geo", "split_clients", "charset_map", "perl_set", "js_set", "js_import",
            "if", "limit_except", "match",
            "mail", "smtp_auth", "imap", "pop3", "auth_http"
    );

    /**
     * 工具类不允许实例化。
     */
    private NginxBlockRegistry() {
    }

    /**
     * 是否区块名。
     *
     * @param name 名称
     * @return true 表示区块
     */
    public static boolean isBlockName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.trim();
        if (n.isEmpty()) {
            return false;
        }
        // location 允许 `location @fallback` 这种命名 location
        if (n.startsWith("location @")) {
            return true;
        }
        return BLOCKS.contains(n);
    }
}
