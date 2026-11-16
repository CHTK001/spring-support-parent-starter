package com.chua.starter.datasearch.provider.limit;

/**
 * 限制机制 SPI：一种「<b>怎么拿</b>」一个实现，不逐家服务商写死。
 *
 * <p>内置实现按「手动配置 → 官方文档登记表 → 上游响应头动态观测」顺序串起来，
 * 由 {@link ProviderLimitMechanisms} 统一调度；新增机制有两种接入方式，都不需要改
 * 模块源码：</p>
 * <ul>
 *   <li><b>Spring Bean</b>：把实现声明为 {@code @Component} / {@code @Bean}，
 *       由宿主的装配类收集后交给 {@link ProviderLimitMechanisms#install}；</li>
 *   <li><b>JAR SPI</b>：在 {@code META-INF/services/} 下登记本接口实现，
 *       无 Spring 上下文（单测、CLI）时由 {@link java.util.ServiceLoader} 装载。</li>
 * </ul>
 *
 * <p>同一 {@link #kind()} 只保留一个实现：Spring Bean 优先，其后是 ServiceLoader，
 * 最后是内置兜底。顺序由 {@link #order()} 决定，数值小的先执行。</p>
 *
 * <p>注意与本包「数据侧」SPI 的区别：{@link com.chua.starter.datasearch.provider.spi.ProviderLimitProvider}
 * 回答的是「<b>去哪拿</b>」（读哪份登记表 / 哪个控制台），本接口回答的是「<b>怎么拿</b>」
 * （手动填、查文档、读响应头）。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public interface ProviderLimitMechanism {

    /**
     * 机制标识（manual / doc / header，扩展实现自定义）
     *
     * @return 机制标识
     */
    String kind();

    /**
     * 查询限制；拿不到时返回 {@code null}（不要返回「全空但存在」的对象）
     *
     * @param context 探测上下文
     * @return 限制快照或 null
     */
    ProviderLimit query(ProviderLimitContext context);

    /**
     * 执行顺序，数值小的先执行。
     *
     * <p>内置值：手动 10、官方文档 20、响应头 30。扩展机制按语义插空，
     * 例如「厂商控制台接口查询」放在 25（文档之后、响应头之前）。</p>
     *
     * @return 顺序值
     */
    default int order() {
        return 100;
    }
}
