// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasearch.service.impl;

import com.chua.common.support.network.ip.GeoCity;
import com.chua.common.support.network.ip.GeoSetting;
import com.chua.common.support.network.ip.IpPosition;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.datasearch.pojo.ip.IpLookupVO;
import com.chua.starter.datasearch.service.IpLookupDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 归属地数据服务实现。
 *
 * <p>直接消费 {@code IpPosition} SPI（ip2region / geoip2 等本地库，毫秒级、无外呼），
 * 而不是 {@code IptablesService}：后者在接口上钉了
 * {@code @Cacheable(cacheManager = "redis-cache-600")}，宿主没有注册该缓存管理器
 * （未引入 redis + ai 模块）时，一次查询就会抛
 * {@code Cannot find cache named 'redis-cache-600'}。顶栏的 IP 提示是弱依赖，
 * 不该因为缓存装配缺一块就整块失败。</p>
 *
 * <p>解析器按需初始化并复用：SPI 实例构造要读整个 xdb 文件进内存，
 * 每次请求新建会让一次点击多花几十毫秒。查询结果按 IP 做进程内缓存，
 * 同一个 IP 在页面上往往被多个组件同时渲染。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@Service
public class IpLookupDataServiceImpl implements IpLookupDataService {

    /**
     * 默认 IP 解析器名称（与 {@code plugin.ip.ip-type} 的默认值一致）
     */
    private static final String DEFAULT_IP_TYPE = "ip2region";

    /**
     * IPv4 段数
     */
    private static final int IPV4_PARTS = 4;

    /**
     * IPv4 单段上限
     */
    private static final int IPV4_MAX_SEGMENT = 255;

    /**
     * 端口分隔符
     */
    private static final String PORT_SEPARATOR = ":";

    /**
     * 查询结果缓存上限（IP 归属地几乎不变，缓存可以长期有效）
     */
    private static final int CACHE_LIMIT = 2048;

    /**
     * IP → 归属地（值可为 空，表示该 IP 查不到，避免反复穿透到解析器）
     */
    private final Map<String, IpLookupVO> cache = new ConcurrentHashMap<>();

    /**
     * 解析器（懒加载，volatile 保证双重检查可见性）
     */
    private volatile IpPosition position;

    /**
     * 解析器是否已尝试加载（加载失败也置位，避免每次请求重试构造）
     */
    private volatile boolean positionLoaded;

    @Override
    public IpLookupVO lookup(String ip) {
        String address = normalize(ip);
        if (address == null) {
            return null;
        }
        IpLookupVO cached = cache.get(address);
        if (cached != null) {
            return cached;
        }
        IpPosition handler = position();
        if (handler == null) {
            return null;
        }
        GeoCity city;
        try {
            city = handler.getCity(address);
        } catch (Exception e) {
            log.warn("[ip-lookup] 归属地解析失败: ip={}, msg={}", address, e.getMessage());
            return null;
        }
        if (city == null) {
            return null;
        }
        IpLookupVO result = toVo(address, city);
        if (result == null) {
            return null;
        }
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(address, result);
        return result;
    }

    /**
     * 归属地实体 → 契约。
     *
     * @param ip   查询的 IP
     * @param city 归属地
     * @return 出参；四项行政信息全空时返回 空（避免前端显示一条只有 IP 的空行）
     */
    private IpLookupVO toVo(String ip, GeoCity city) {
        String country = blankToNull(city.getCountry());
        String province = blankToNull(city.getProvince());
        String cityName = blankToNull(city.getCity());
        String isp = blankToNull(city.getIsp());
        if (country == null && province == null && cityName == null && isp == null) {
            return null;
        }
        return new IpLookupVO(ip, country, province, cityName, isp,
                city.getLatitude(), city.getLongitude(),
                blankToNull(city.getPostal()), blankToNull(city.getTimeZone()));
    }

    /**
     * 取（或首次构造）IP 解析器。
     *
     * <p>构造顺序：先按 {@code ip2region} 名取（默认实现、自带 xdb 资源），
     * 失败则退到任意一个已注册实现。全部失败返回 空 并只告警一次。</p>
     *
     * @return 解析器；无实现时返回 空
     */
    private IpPosition position() {
        if (positionLoaded) {
            return position;
        }
        synchronized (this) {
            if (positionLoaded) {
                return position;
            }
            position = createPosition();
            positionLoaded = true;
        }
        return position;
    }

    /**
     * 构造 IP 解析器实例。
     *
     * @return 解析器；失败时返回 空
     */
    private IpPosition createPosition() {
        GeoSetting setting = GeoSetting.builder().databaseFile("").build();
        try {
            IpPosition loaded = ServiceProvider.of(IpPosition.class)
                    .getNewExtension(DEFAULT_IP_TYPE, setting);
            if (loaded != null) {
                return loaded;
            }
        } catch (Exception e) {
            log.warn("[ip-lookup] {} 解析器不可用: {}", DEFAULT_IP_TYPE, e.getMessage());
        }
        try {
            for (IpPosition candidate : ServiceProvider.of(IpPosition.class).collect()) {
                if (candidate != null) {
                    log.info("[ip-lookup] 改用可用解析器: {}", candidate.getClass().getSimpleName());
                    return candidate;
                }
            }
        } catch (Exception e) {
            log.warn("[ip-lookup] IP 解析器装载失败: {}", e.getMessage());
        }
        log.warn("[ip-lookup] 无可用 IP 归属地数据源（请引入 ip2region 或 geoip2 依赖）");
        return null;
    }

    /**
     * 归一 IP：去空格、去端口，只接受合法 IPv4。
     *
     * <p>本地库只收录 IPv4，IPv6 直接判为不可解析：让它带着解析器扫一遍再失败，
     * 只会让前端多等一次无意义的查询。</p>
     *
     * @param raw 原始入参
     * @return 合法 IPv4；非法时返回 空
     */
    private String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        int portIndex = value.lastIndexOf(PORT_SEPARATOR);
        if (portIndex > 0 && value.indexOf(PORT_SEPARATOR) == portIndex) {
            // 只有单冒号时才是 ip:port；IPv6 有多个冒号，不能切
            value = value.substring(0, portIndex);
        }
        String[] parts = value.split("\\.");
        if (parts.length != IPV4_PARTS) {
            return null;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return null;
                }
            }
            int segment = Integer.parseInt(part);
            if (segment > IPV4_MAX_SEGMENT) {
                return null;
            }
        }
        return value;
    }

    /**
     * 空白转 null（数据源常用空串表示该级未知）。
     *
     * @param value 原始值
     * @return 去空格后的值；空白时为 空
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
