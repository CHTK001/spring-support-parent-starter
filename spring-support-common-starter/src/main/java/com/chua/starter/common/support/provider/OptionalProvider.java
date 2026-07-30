package com.chua.starter.common.support.provider;

import com.chua.common.support.spi.definition.SpiOption;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.properties.OptionalProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_SYSTEM;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * 获取选项
 *
 * @author CH
 */
@RestController
@RequestMapping("/v1/option")
@ConditionalOnProperty(prefix = OptionalProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
@Tag(name = "选项")
public class OptionalProvider  {
    private static final Logger log = LoggerFactory.getLogger(OptionalProvider.class);
    /**
     * 构造函数
     *
     * @param apiProperties ApiProperties
     * @param beanFactory BeanFactory
     */
    public OptionalProvider(ApiProperties apiProperties, BeanFactory beanFactory) {
        this.apiProperties = apiProperties;
        this.beanFactory = beanFactory;
    }


    private final ApiProperties apiProperties;
    private final BeanFactory beanFactory;
    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @Operation(summary = "获取已经加载选项")
    @GetMapping("objects/get")
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'OPTION:OBJECT_GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> objects(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }

        type = StringUtils.utf8Str(type);
        List<?> rawOptions = ServiceProvider.of(getType(type)).options();
        List<SpiOption> options = rawOptions.stream().map(item -> {
            if (item instanceof SpiOption spiOption) {
                return spiOption;
            }
            if (item instanceof ServiceDefinition definition) {
                return SpiOption.of(definition.getName(), definition.getDescribe(), definition.getSupportedTypes());
            }
            return SpiOption.of(String.valueOf(item), null, null);
        }).toList();
        if(!StringUtils.isBlank(name)) {
             for (SpiOption option : options) {
                String optionName = option.name();
                if(StringUtils.equalsIgnoreCase(optionName, name)) {
                    return ReturnResult.ok(Collections.singletonList(option));
                }
            }
        }

        return ReturnResult.ok(options);
    }

    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    private String[] getTypes(String type) {
        ApiProperties.SpiConfig spi = apiProperties.getSpi();
        if (spi != null && spi.isEnable() && spi.getMapping() != null) {
            Object obj = spi.getMapping().get(type);
            if (obj instanceof String[]) return (String[]) obj;
            if (obj instanceof String) return ((String) obj).split(",");
            return new String[]{type};
        }
        return new String[]{type};
    }

    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    private String getType(String type) {
        ApiProperties.SpiConfig spi = apiProperties.getSpi();
        if (spi != null && spi.isEnable() && spi.getMapping() != null) {
            return MapUtils.getString(spi.getMapping(), type);
        }
        return type;
    }

    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @Operation(summary = "获取选项")
    @GetMapping("get")
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'OPTION:GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> get(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }

        type = StringUtils.utf8Str(type);
        List<?> rawOptions = ServiceProvider.of(getType(type)).options();
        List<SpiOption> options = rawOptions.stream().map(item -> {
            if (item instanceof SpiOption spiOption) {
                return spiOption;
            }
            if (item instanceof ServiceDefinition definition) {
                return SpiOption.of(definition.getName(), definition.getDescribe(), definition.getSupportedTypes());
            }
            return SpiOption.of(String.valueOf(item), null, null);
        }).toList();
        if(!StringUtils.isBlank(name)) {
            List<SpiOption> collect = options.stream().filter(spiOption -> {
                String[] supportedTypes = spiOption.supportedTypes();
                String optionName = spiOption.name();
                return ArrayUtils.containsIgnoreCase(supportedTypes, name) || StringUtils.containsIgnoreCase(optionName, name);
            }).toList();
             return ReturnResult.ok(collect);
        }

        return ReturnResult.ok(options);
    }
    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @Operation(summary = "获取选项")
    @GetMapping("list")
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'OPTION:LIST' + #type + '' + #name")
    public ReturnResult<Map<String, List<SpiOption>>> list(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
        }
        String[] split = StringUtils.utf8Str(type).split(",");
        Map<String, List<SpiOption>> map = MapUtils.newHashMap();
        for (String s : split) {
            String[] types = getTypes(s);
            for (String item : types) {
                List<SpiOption> options = (List<SpiOption>) (List<?>) ServiceProvider.of(item).options();
                if(!StringUtils.isBlank(name)) {
                    for (SpiOption option : options) {
                        String optionName = option.name();
                        if (StringUtils.equalsIgnoreCase(optionName, name)) {
                            map.put(s, Collections.singletonList(option));
                            break;
                        }
                    }
                    continue;
                }
                map.computeIfAbsent(s, it -> new LinkedList<>()).addAll(options);
            }
        }

        return ReturnResult.ok(map);
    }
}
