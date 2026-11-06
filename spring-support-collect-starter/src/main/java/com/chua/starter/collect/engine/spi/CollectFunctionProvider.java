package com.chua.starter.collect.engine.spi;

import java.util.List;

/**
 * 自定义函数来源扩展点。
 *
 * <p>引擎不直接查库，由实现方（默认是函数库服务）把启用的函数交出来；第三方 jar 也可以用
 * {@code @Spi} 注册自己的函数来源，实现零侵入扩库。函数名冲突时先注册的来源生效并告警。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFunctionProvider {

    /**
     * 当前可用的自定义函数。
     *
     * <p>实现要能在采集线程里被反复调用，代价高的话自行缓存；注册表只在缓存过期时调用一次。</p>
     *
     * @return 函数清单，停用的不要返回
     */
    List<CollectFunctionDef> loadEnabled();
}
