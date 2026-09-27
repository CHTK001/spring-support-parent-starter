package com.chua.starter.datasearch.service.impl;

import com.chua.common.support.datasearch.geocode.spi.GeocodeProvider;
import com.chua.common.support.datasearch.location.model.LocationInfo;
import com.chua.common.support.datasearch.location.spi.LocationProvider;
import com.chua.common.support.datasearch.weather.model.DailyForecast;
import com.chua.common.support.datasearch.weather.model.HourlyWeather;
import com.chua.common.support.datasearch.weather.model.WeatherInfo;
import com.chua.common.support.datasearch.weather.spi.WeatherProvider;
import com.chua.starter.datasearch.pojo.location.LocationVO;
import com.chua.starter.datasearch.pojo.weather.WeatherOverviewVO;
import com.chua.starter.datasearch.query.WeatherQuery;
import com.chua.starter.datasearch.service.WeatherDataService;
import com.chua.starter.datasearch.support.SpiProviders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 顶栏天气数据服务实现。
 *
 * <p>数据源经 SPI 装载（注册顺序即优先级，open-meteo 在前）：逐个尝试，
 * 拿到带 WMO 天气码的结果立即采用，只有全部数据源都缺码时才退回首个结果。
 * 出参一律补齐成基本类型数值，前端拿到的是齐全字段而不是 null。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
public class WeatherDataServiceImpl implements WeatherDataService {

    /**
     * 默认预报天数
    */
    private static final int DEFAULT_DAYS = 7;

    /**
     * 默认逐小时点数
    */
    private static final int DEFAULT_HOURS = 24;

    /**
     * 逐小时点数上限（防止入参过大撑爆响应）
    */
    private static final int MAX_HOURS = 72;

    /**
     * 预报天数上限
    */
    private static final int MAX_DAYS = 16;

    /**
     * 定位候选请求头（顺序与 SPI 的 resolveClientIp 一致）
    */
    private static final String[] IP_HEADERS = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};

    @Override
    public WeatherOverviewVO overview(WeatherQuery query) {
        WeatherInfo info = load(query);
        return info == null ? null : toOverview(info, query);
    }

    @Override
    public LocationVO resolveLocation(HttpServletRequest request) {
        List<LocationProvider> providers = SpiProviders.list(LocationProvider.class);
        if (providers.isEmpty()) {
            return null;
        }
        String clientIp = clientIp(request, providers.getFirst());
        for (LocationProvider provider : providers) {
            LocationInfo info = clientIp == null ? provider.locateSelf() : provider.locateIp(clientIp);
            // 内网/回环地址查不到归属地，退回服务器自身定位
            if (info == null && clientIp != null) {
                info = provider.locateSelf();
            }
            LocationVO vo = toLocation(info, provider.name());
            if (vo != null) {
                return vo;
            }
        }
        return null;
    }

    /**
     * 逐个数据源取天气，优先采用带 WMO 天气码的结果。
     *
     * @param query 查询条件
     * @return 天气实体；无坐标且无城市、或全部数据源不可用时返回 空
     */
    private WeatherInfo load(WeatherQuery query) {
        Double latitude = query.getLatitude();
        Double longitude = query.getLongitude();
        String city = trimToNull(query.getCity());
        if ((latitude == null || longitude == null) && city == null) {
            return null;
        }
        int days = limit(query.getDays(), DEFAULT_DAYS, MAX_DAYS);
        WeatherInfo fallback = null;
        for (WeatherProvider provider : SpiProviders.list(WeatherProvider.class)) {
            WeatherInfo info = latitude == null || longitude == null
                    ? provider.getWeather(city)
                    : provider.getWeather(latitude, longitude, days);
            if (info == null) {
                continue;
            }
            if (info.weatherCode() != null) {
                return info;
            }
            if (fallback == null) {
                fallback = info;
            }
        }
        return fallback;
    }

    /**
     * 天气实体 → 契约出参。
     *
     * @param info  天气实体
     * @param query 查询条件（截取点数与天数）
     * @return 天气总览
     */
    private WeatherOverviewVO toOverview(WeatherInfo info, WeatherQuery query) {
        WeatherOverviewVO vo = new WeatherOverviewVO();
        vo.setLocation(displayLocation(info));
        vo.setLatitude(d(info.latitude()));
        vo.setLongitude(d(info.longitude()));
        vo.setWeatherCode(i(info.weatherCode()));
        vo.setTemperature(d(info.tempC()));
        vo.setFeelsLike(d(info.feelsLikeC()));
        vo.setHumidity(i(info.humidity()));
        vo.setWindSpeed(d(info.windSpeedKmph()));
        vo.setWindDirection(d(info.windDirection()));
        vo.setPressure(i(info.pressure()));
        vo.setObservedAt(info.observationTime());
        vo.setSource(info.source());
        vo.setHourly(toHours(info.hourly(), hourPrefix(info.observationTime()),
                limit(query.getHours(), DEFAULT_HOURS, MAX_HOURS)));
        vo.setDaily(toDays(info.forecast(), limit(query.getDays(), DEFAULT_DAYS, MAX_DAYS)));
        return vo;
    }

    /**
     * 顶栏展示地名。
     *
     * <p>数据源给了地名（按城市查询）就直接用；按坐标查询时反查行政地名，
     * 取最细一级作为短名；仍拿不到才退回「本地」。</p>
     *
     * @param info 天气实体
     * @return 展示地名
     */
    private String displayLocation(WeatherInfo info) {
        String city = trimToNull(info.city());
        if (city != null) {
            return city;
        }
        Double latitude = info.latitude();
        Double longitude = info.longitude();
        if (latitude == null || longitude == null) {
            return "本地";
        }
        for (GeocodeProvider provider : SpiProviders.list(GeocodeProvider.class)) {
            String address = provider.reverseGeocode(latitude, longitude);
            if (address != null && !address.isBlank()) {
                return address.split(",")[0].trim();
            }
        }
        return "本地";
    }

    /**
     * 逐小时实体 → 契约，并裁掉观测时刻之前的点。
     *
     * @param hourly 实体列表
     * @param anchor 观测时刻前缀（yyyy-MM-ddTHH），为空表示不裁剪
     * @param max    最多返回点数
     * @return 逐小时列表
     */
    private List<WeatherOverviewVO.Hour> toHours(List<HourlyWeather> hourly, String anchor, int max) {
        List<WeatherOverviewVO.Hour> result = new ArrayList<>();
        if (hourly == null || hourly.isEmpty()) {
            return result;
        }
        int from = 0;
        if (anchor != null) {
            for (int i = 0; i < hourly.size(); i++) {
                String prefix = hourPrefix(hourly.get(i).getTime());
                if (prefix != null && prefix.compareTo(anchor) >= 0) {
                    from = i;
                    break;
                }
            }
        }
        for (int i = from; i < hourly.size() && result.size() < max; i++) {
            HourlyWeather item = hourly.get(i);
            WeatherOverviewVO.Hour hour = new WeatherOverviewVO.Hour();
            hour.setTime(item.getTime());
            hour.setWeatherCode(i(item.getWeatherCode()));
            hour.setTemperature(d(item.getTempC()));
            hour.setFeelsLike(d(item.getFeelsLikeC()));
            hour.setHumidity(i(item.getHumidity()));
            hour.setWindSpeed(d(item.getWindSpeedKmph()));
            hour.setWindDirection(d(item.getWindDirection()));
            hour.setPrecipitation(d(item.getPrecipitation()));
            hour.setPrecipitationProbability(i(item.getPrecipitationProbability()));
            result.add(hour);
        }
        return result;
    }

    /**
     * 逐日实体 → 契约。
     *
     * @param forecast 实体列表
     * @param max      最多返回天数
     * @return 逐日列表
     */
    private List<WeatherOverviewVO.Day> toDays(List<DailyForecast> forecast, int max) {
        List<WeatherOverviewVO.Day> result = new ArrayList<>();
        if (forecast == null) {
            return result;
        }
        for (DailyForecast item : forecast) {
            if (result.size() >= max) {
                break;
            }
            WeatherOverviewVO.Day day = new WeatherOverviewVO.Day();
            day.setDate(item.getDate());
            day.setWeatherCode(i(item.getWeatherCode()));
            day.setTempMax(d(item.getMaxTempC()));
            day.setTempMin(d(item.getMinTempC()));
            day.setFeelsMax(d(item.getFeelsLikeMaxC()));
            day.setFeelsMin(d(item.getFeelsLikeMinC()));
            day.setPrecipitation(d(item.getPrecipitation()));
            day.setPrecipitationProbability(i(item.getPrecipitationProbability()));
            day.setWindSpeed(d(item.getWindSpeedKmph()));
            day.setWindDirection(d(item.getWindDirection()));
            day.setUvIndex(d(item.getUvIndex()));
            day.setSunrise(hhmm(item.getSunrise()));
            day.setSunset(hhmm(item.getSunset()));
            result.add(day);
        }
        return result;
    }

    /**
     * 定位实体 → 契约。
     *
     * @param info   定位实体
     * @param source 数据源名
     * @return 定位出参；经纬度缺失时返回 空
     */
    private LocationVO toLocation(LocationInfo info, String source) {
        if (info == null || info.latitude() == null || info.longitude() == null) {
            return null;
        }
        LocationVO vo = new LocationVO();
        vo.setLatitude(info.latitude());
        vo.setLongitude(info.longitude());
        vo.setCity(firstText(info.city(), info.region(), info.country()));
        vo.setSource(source);
        return vo;
    }

    /**
     * 取客户端公网 IP（内网/回环地址视为不可定位）。
     *
     * @param request  当前请求
     * @param provider 用于解析请求头的定位数据源
     * @return 可定位的客户端 IP；不可定位时返回 空
     */
    private String clientIp(HttpServletRequest request, LocationProvider provider) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : IP_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        String ip = provider.resolveClientIp(headers, request.getRemoteAddr());
        return isLocalIp(ip) ? null : ip;
    }

    /**
     * 是否本机/内网地址（这类地址无法做 IP 归属地定位）。
     *
     * @param ip 待判定 IP
     * @return 是否本机或内网
     */
    private boolean isLocalIp(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isAnyLocalAddress()
                    || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception e) {
            log.debug("[weather] IP 归属判定失败: ip={}, msg={}", ip, e.getMessage());
            return true;
        }
    }

    /**
     * 截取时间串的「日期+小时」前缀。
     *
     * @param iso 时间串（yyyy-MM-ddTHH:mm）
     * @return 前缀；过短或为空时返回 空
     */
    private String hourPrefix(String iso) {
        return iso != null && iso.length() >= 13 ? iso.substring(0, 13) : null;
    }

    /**
     * 完整时间串取时分。
     *
     * @param iso 时间串（yyyy-MM-ddTHH:mm）
     * @return HH:mm；缺失时返回空串
     */
    private String hhmm(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : "";
    }

    /**
     * 限制入参到 [1, max]，为空取默认值。
     *
     * @param value      入参
     * @param defaultVal 默认值
     * @param max        上限
     * @return 生效值
     */
    private int limit(Integer value, int defaultVal, int max) {
        if (value == null) {
            return defaultVal;
        }
        return Math.max(1, Math.min(max, value));
    }

    /**
     * 取首个非空文本。
     *
     * @param values 候选
     * @return 首个非空值；全空时返回 本地
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "本地";
    }

    /**
     * 空白转 null。
     *
     * @param value 文本
     * @return 去空格后的文本；空白时返回 空
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 数值兜底。
     *
     * @param value 数值
     * @return 数值；为空时 0
     */
    private double d(Double value) {
        return value == null ? 0D : value;
    }

    /**
     * 整数兜底。
     *
     * @param value 数值
     * @return 数值；为空时 0
     */
    private int i(Integer value) {
        return value == null ? 0 : value;
    }
}
