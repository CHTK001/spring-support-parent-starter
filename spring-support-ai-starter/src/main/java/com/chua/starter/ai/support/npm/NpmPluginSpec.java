package com.chua.starter.ai.support.npm;

/**
 * 单个 npm 中间件插件配置（角色 {@code settings.npmPlugins} 数组条目）。
 *
 * @param name    npm 包名（支持 {@code @scope/name}）
 * @param options 传给插件 {@code ctx.options} 的参数（如 {@code {"mode":"strip"}}），可为 null
 * @author CH
 * @since 4.0.0.52
 */
public record NpmPluginSpec(String name, Object options) {
}
