package com.chua.starter.datasearch.support;

import com.chua.common.support.spi.ServiceProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据检索 SPI 装载工具。
 *
 * <p>顶栏天气/台风等能力以 {@code @Spi} 实现接入，遍历全部实现时有两个坑：</p>
 * <ul>
 *   <li>同一实现可能同时以「登记名」和「注解值」两个别名注册，被 {@code collect()} 取到两次，
 *       导致重复外呼，这里按实现类去重；</li>
 *   <li>宿主未引入 datasearch 模块时 SPI 基类缺失，装载不能把调用方一起带崩。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
public final class SpiProviders {

    private SpiProviders() {
    }

    /**
     * 装载指定 SPI 接口的全部实现（按实现类去重，保持注册顺序）。
     *
     * @param type SPI 接口
     * @param <T>  接口类型
     * @return 实现列表；无实现或装载失败时返回空列表
     */
    public static <T> List<T> list(Class<T> type) {
        Map<Class<?>, T> distinct = new LinkedHashMap<>();
        try {
            for (T impl : ServiceProvider.of(type).collect()) {
                if (impl != null) {
                    distinct.putIfAbsent(impl.getClass(), impl);
                }
            }
        } catch (Throwable ex) {
            log.warn("[datasearch] SPI 装载失败: type={}, msg={}", type.getName(), ex.getMessage());
            return List.of();
        }
        return new ArrayList<>(distinct.values());
    }
}
