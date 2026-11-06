package com.chua.starter.datasearch.configuration;

import com.chua.starter.datasearch.controller.ExchangeRateV2Controller;
import com.chua.starter.datasearch.controller.HolidayV2Controller;
import com.chua.starter.datasearch.controller.IpLookupV2Controller;
import com.chua.starter.datasearch.controller.LocationV2Controller;
import com.chua.starter.datasearch.controller.TyphoonV2Controller;
import com.chua.starter.datasearch.controller.WeatherV2Controller;
import com.chua.starter.datasearch.service.ExchangeRateDataService;
import com.chua.starter.datasearch.service.HolidayDataService;
import com.chua.starter.datasearch.service.IpLookupDataService;
import com.chua.starter.datasearch.service.TyphoonDataService;
import com.chua.starter.datasearch.service.WeatherDataService;
import com.chua.starter.datasearch.service.impl.ExchangeRateDataServiceImpl;
import com.chua.starter.datasearch.service.impl.HolidayDataServiceImpl;
import com.chua.starter.datasearch.service.impl.IpLookupDataServiceImpl;
import com.chua.starter.datasearch.service.impl.TyphoonDataServiceImpl;
import com.chua.starter.datasearch.service.impl.WeatherDataServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 数据检索服务化自动配置。
 *
 * <p>天气 / 定位 / 台风 / 汇率 / 节假日 / IP 归属地六个控制器与五个数据服务由本配置注册，
 * 宿主引入依赖即生效，不要求 {@code @ComponentScan} 覆盖 {@code com.chua.starter.datasearch}
 * （多数宿主只扫自己的子包，扫不到这里）。</p>
 *
 * <p>生效条件：</p>
 * <ul>
 *   <li>Servlet Web 应用（控制器依赖 Spring MVC）；</li>
 *   <li>{@code plugin.datasearch.enabled} 不为 false（默认开启）；</li>
 *   <li>宿主已自建同名 Bean 时逐个退让，避免与宿主实现重复注册。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = DataSearchServiceConfiguration.PROPERTY_PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DataSearchServiceConfiguration {

    /**
     * 开关配置前缀（plugin.datasearch.enabled）
     */
    public static final String PROPERTY_PREFIX = "plugin.datasearch";

    /**
     * 天气数据服务（多气象源归一成本系统契约）
     *
     * @return 天气数据服务
     */
    @Bean
    @ConditionalOnMissingBean
    public WeatherDataService weatherDataService() {
        return new WeatherDataServiceImpl();
    }

    /**
     * 台风数据服务（风圈报文解析 + 多机构预报收敛）
     *
     * @return 台风数据服务
     */
    @Bean
    @ConditionalOnMissingBean
    public TyphoonDataService typhoonDataService() {
        return new TyphoonDataServiceImpl();
    }

    /**
     * 汇率数据服务（最新价 + 周期涨跌 + 周期极值）
     *
     * @return 汇率数据服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ExchangeRateDataService exchangeRateDataService() {
        return new ExchangeRateDataServiceImpl();
    }

    /**
     * 节假日数据服务（HolidayProvider SPI 归一成前端契约）
     *
     * @return 节假日数据服务
     */
    @Bean
    @ConditionalOnMissingBean
    public HolidayDataService holidayDataService() {
        return new HolidayDataServiceImpl();
    }

    /**
     * IP 归属地数据服务（本地 IP 库解析，无外呼）
     *
     * @return IP 归属地数据服务
     */
    @Bean
    @ConditionalOnMissingBean
    public IpLookupDataService ipLookupDataService() {
        return new IpLookupDataServiceImpl();
    }

    /**
     * 顶栏天气接口
     *
     * @param weatherDataService 天气数据服务
     * @return 天气控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public WeatherV2Controller weatherV2Controller(WeatherDataService weatherDataService) {
        return new WeatherV2Controller(weatherDataService);
    }

    /**
     * 顶栏定位解析接口
     *
     * @param weatherDataService 天气数据服务（定位与天气同源，收口在一个服务里）
     * @return 定位控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public LocationV2Controller locationV2Controller(WeatherDataService weatherDataService) {
        return new LocationV2Controller(weatherDataService);
    }

    /**
     * 顶栏台风接口
     *
     * @param typhoonDataService 台风数据服务
     * @return 台风控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public TyphoonV2Controller typhoonV2Controller(TyphoonDataService typhoonDataService) {
        return new TyphoonV2Controller(typhoonDataService);
    }

    /**
     * 顶栏汇率接口
     *
     * @param exchangeRateDataService 汇率数据服务
     * @return 汇率控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public ExchangeRateV2Controller exchangeRateV2Controller(
            ExchangeRateDataService exchangeRateDataService) {
        return new ExchangeRateV2Controller(exchangeRateDataService);
    }

    /**
     * 节假日前台接口
     *
     * @param holidayDataService 节假日数据服务
     * @return 节假日控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public HolidayV2Controller holidayV2Controller(HolidayDataService holidayDataService) {
        return new HolidayV2Controller(holidayDataService);
    }

    /**
     * IP 归属地接口
     *
     * @param ipLookupDataService IP 归属地数据服务
     * @return IP 归属地控制器
     */
    @Bean
    @ConditionalOnMissingBean
    public IpLookupV2Controller ipLookupV2Controller(IpLookupDataService ipLookupDataService) {
        return new IpLookupV2Controller(ipLookupDataService);
    }
}
