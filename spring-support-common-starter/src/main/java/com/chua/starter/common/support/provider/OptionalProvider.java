package com.chua.starter.common.support.provider;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.SpiOption;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 获取选项
 *
 * @author CH
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
     * @param apiProperties ApiProperties
     * @param beanFactory BeanFactory
     */
    public OptionalProvider(ApiProperties apiProperties, BeanFactory beanFactory) {
        this.apiProperties = apiProperties;
        this.beanFactory = beanFactory;
    }


    /** API属性 */
    private final ApiProperties apiProperties;
    /** Bean工厂 */
    private final BeanFactory beanFactory;
    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @Operation(summary = "获取已经加载选项")
    @GetMapping("objects/get")
    @Cacheable(cacheManager = "system", cacheNames = "redis-cache--1", key = "'OPTION:OBJECT_GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> objects(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }

        Class<?> aClass = ClassUtils.toType(getType(type));
        Map<String, ?> beanOfTypes = new HashMap<>();
        if (beanFactory instanceof ListableBeanFactory listableBeanFactory) {
            beanOfTypes = listableBeanFactory.getBeansOfType(aClass);
        }
        List<SpiOption> options = MapUtils.mapToList(beanOfTypes, (key, value) -> {
            ServiceDefinition def = ServiceDefinitionUtils.buildDefinitionAlias(aClass, null, value, value.getClass(), null, key, 0);
            return SpiOption.of(def.getName(), "", new String[0]);
        });
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
    @Cacheable(cacheManager = "system", cacheNames = "redis-cache--1", key = "'OPTION:GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> get(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }

        type = StringUtils.utf8Str(type);
        List<?> rawOptions = ServiceProvider.of(getType(type)).options();
        List<SpiOption> options = rawOptions.stream().map(o -> (SpiOption) o).collect(Collectors.toList());
        if(!StringUtils.isBlank(name)) {
            List<SpiOption> collect = options.stream().filter(spiOption -> {
                String optionName = spiOption.name();
                return StringUtils.containsIgnoreCase(optionName, name);
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
    @Cacheable(cacheManager = "system", cacheNames = "redis-cache--1", key = "'OPTION:LIST' + #type + '' + #name")
    public ReturnResult<Map<String, List<SpiOption>>> list(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error("参数不能为空");
        }
        String[] split = StringUtils.utf8Str(type).split(",");
        Map<String, List<SpiOption>> map = MapUtils.newHashMap();
        for (String s : split) {
            String[] types = getTypes(s);
            for (String item : types) {
                List<?> rawOptions = ServiceProvider.of(item).options();
                List<SpiOption> options = rawOptions.stream().map(o -> (SpiOption) o).collect(Collectors.toList());
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
