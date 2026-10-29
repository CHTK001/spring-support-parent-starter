package com.chua.starter.datasourcehub.support.dynamicapi;

/**
 * 动态接口发布结果。
 *
 * <p>记录一次 {@link DynamicApiService#publish(Long)} 发布调用的结果：
 * 接口 id、暴露路径（standalone 时在独立服务端可下载的地址；
 * embedded 时走宿主自己 Spring MVC 映射）与发布模式。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
public record DynamicApiPublishResult(
        /**
         * 接口 id
         */
        Long id,
        /**
         * 暴露路径
         */
        String path,
        /**
         * 发布模式：embedded / standalone
         */
        String publishMode
) {
}
