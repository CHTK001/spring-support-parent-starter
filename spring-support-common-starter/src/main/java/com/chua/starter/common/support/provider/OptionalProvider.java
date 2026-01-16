package com.chua.starter.common.support.provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.ObjectContext;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.SpiOption;
import com.chua.common.support.spi.definition.ServiceDefinition;
import com.chua.common.support.spi.definition.ServiceDefinitionUtils;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.properties.OptionalProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
public class OptionalProvider {
    /**
     * 构造函数
     *
     * @param apiProperties ApiProperties
     */
    public OptionalProvider(ApiProperties apiProperties) {
        this.apiProperties = apiProperties;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OptionalProvider.class);


    private final ApiProperties apiProperties;
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
            return ReturnResult.error();
        }

        Class<?> aClass = ClassUtils.toType(getType(type));
        Map<String, ?> beanOfTypes = ObjectContext.getInstance().getBeanOfTypes(aClass);
        List<SpiOption> options = MapUtils.mapToList(beanOfTypes, (key, value) -> {
            ServiceDefinition serviceDefinition = ServiceDefinitionUtils.buildDefinitionAlias(aClass, null, value, value.getClass(), null, key, 0);
            return serviceDefinition.toSpiOption();
        });
        if(!StringUtils.isBlank(name)) {
             for (SpiOption option : options) {
                if(StringUtils.equalsIgnoreCase(option.getName(), name)) {
                    return ReturnResult.success(Collections.singletonList(option));
                }
            }
        }

        return ReturnResult.success(options);
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
            return MapUtils.getStringArray(spi.getMapping(), type);
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
            return ReturnResult.error();
        }

        type = StringUtils.utf8Str(type);
        List<SpiOption> options = ServiceProvider.of(getType(type)).options();
        if(!StringUtils.isBlank(name)) {
            List<SpiOption> collect = options.stream().filter(spiOption -> ArrayUtils.containsIgnoreCase(spiOption.getSupportedTypes(), name) || StringUtils.containsIgnoreCase(spiOption.getName(), name)).toList();
             return ReturnResult.success(collect);
        }

        return ReturnResult.success(options);
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
            return ReturnResult.error();
        }
        String[] split = StringUtils.utf8Str(type).split(",");
        Map<String, List<SpiOption>> map = MapUtils.newHashMap();
        for (String s : split) {
            String[] types = getTypes(s);
            for (String item : types) {
                List<SpiOption> options = ServiceProvider.of(item).options();
                if(!StringUtils.isBlank(name)) {
                    for (SpiOption option : options) {
                        if (StringUtils.equalsIgnoreCase(option.getName(), name)) {
                            map.put(s, Collections.singletonList(option));
                            break;
                        }
                    }
                    continue;
                }
                map.computeIfAbsent(s, it -> new LinkedList<>()).addAll(options);
            }
        }

        return ReturnResult.success(map);
    }
}

