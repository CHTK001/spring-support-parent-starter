package com.chua.starter.common.support.provider;

import com.chua.common.support.lang.code.ReturnResult;
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
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.chua.starter.common.support.constant.CacheConstant.CACHE_MANAGER_FOR_SYSTEM;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
* 获取选项
*
* @author CH
* @since 4.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/option")
@ConditionalOnProperty(prefix = OptionalProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = false)
@Tag(name = "选项")
public class OptionalProvider {
    /**
    * 构造函数
    *
    * @param apiProperties api属性
    * @param beanFactory Bean工厂
    */
    public OptionalProvider(ApiProperties apiProperties, BeanFactory beanFactory) {
        this.apiProperties = apiProperties;
        this.beanFactory = beanFactory;
    }


    private final ApiProperties apiProperties; // api属性
    private final BeanFactory beanFactory; // Bean工厂
    /**
    * 获取选项
    *
    * @param type 类型
    * @param name 名称，可为空
    * @return 获取选项
    */
    @Operation(summary = "获取已经加载选项")
    @GetMapping("objects/get")
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'OPTION:OBJECT_GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> objects(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }

        Class<?> aClass = ClassUtils.toType(getType(type));
        // 容量随 beanFactory 类型与注册 Bean 数量变化，无法预估，按需扩容
        Map<String, ?> beanOfTypes = new HashMap<>();
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            beanOfTypes = listableBeanFactory.getBeansOfType(aClass);
        }
        List<SpiOption> options = MapUtils.mapToList(beanOfTypes, (key, value) -> new SpiOption() {
            private final ServiceDefinition def = ServiceDefinitionUtils.buildDefinitionAlias(aClass, null, value, value.getClass(), null, key, 0);
            @Override
            public String name() {
                return def.getName();
            }
            @Override
            public String[] supportedTypes() {
                return new String[0];
            }
        });
        if(!StringUtils.isBlank(name)) {
             for (SpiOption option : options) {
                String optionName = option.name();
                if(StringUtils.equalsIgnoreCase(optionName, name)) {
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
            Object obj = spi.getMapping().get(type);
            if (obj instanceof String[]) {
                return (String[]) obj;
            }
            if (obj instanceof String) {
                return ((String) obj).split(",");
            }
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
    * @param name 名称，可为空
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
        List<SpiOption> options = toSpiOptions(ServiceProvider.of(getType(type)).options());
        if(!StringUtils.isBlank(name)) {
            List<SpiOption> collect = options.stream().filter(spiOption -> {
                String[] supportedTypes = spiOption.supportedTypes();
                String optionName = spiOption.name();
                return ArrayUtils.containsIgnoreCase(supportedTypes, name) || StringUtils.containsIgnoreCase(optionName, name);
            }).toList();
             return ReturnResult.success(collect);
        }

        return ReturnResult.success(options);
    }
    /**
    * 获取选项
    *
    * @param type 类型
    * @param name 名称，可为空
    * @return 获取选项
    */
    @Operation(summary = "获取选项")
    @GetMapping("list")
    @Cacheable(cacheManager = CACHE_MANAGER_FOR_SYSTEM, cacheNames = REDIS_CACHE_ALWAYS, key = "'OPTION:LIST' + #type + '' + #name")
    public ReturnResult<Map<String, List<SpiOption>>> list(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }
        String[] split = StringUtils.utf8Str(type).split(",");
        Map<String, List<SpiOption>> map = MapUtils.newHashMap();
        for (String s : split) {
            String[] types = getTypes(s);
            for (String item : types) {
                List<SpiOption> options = toSpiOptions(ServiceProvider.of(item).options());
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

        return ReturnResult.success(map);
    }

    /**
    * 把 ServiceProvider.options() 返回的 List<?> 安全转换为 List<SpiOption>（元素实际均为 SpiOption）。
    *
    * @param raw 原始选项列表（运行时元素均为 {@link SpiOption}）
    * @return SpiOption 列表；入参为 null 时返回空列表
    */
    @SuppressWarnings("unchecked")
    private static List<SpiOption> toSpiOptions(List<?> raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        return (List<SpiOption>) raw;
    }
}

