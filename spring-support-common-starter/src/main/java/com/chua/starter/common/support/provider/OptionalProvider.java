package com.chua.starter.common.support.provider;

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
import com.chua.starter.common.support.properties.OptionalProperties;
import com.chua.starter.common.support.properties.SpiProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 获取选项
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("/v1/option")
@ConditionalOnProperty(prefix = OptionalProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Tag(name = "选项")
public class OptionalProvider {

    private final SpiProperties spiProperties;
    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @Operation(summary = "获取已经加载选项")
    @GetMapping("objects/get")
//    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "'OPTION:OBJECT_GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> objects(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }

        type = StringUtils.utf8Str(Base64.getDecoder().decode(type));
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
        if(spiProperties.isEnable()) {
            return MapUtils.getStringArray(spiProperties.getMapping(), type);
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
        if(spiProperties.isEnable()) {
            return MapUtils.getString(spiProperties.getMapping(), type);
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
//    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "'OPTION:GET' + #type + '' + #name")
    public ReturnResult<List<SpiOption>> get(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }

        type = StringUtils.utf8Str(Base64.getDecoder().decode(type));
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
//    @Cacheable(cacheManager = SYSTEM, cacheNames = SYSTEM, key = "'OPTION:LIST' + #type + '' + #name")
    public ReturnResult<Map<String, List<SpiOption>>> list(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }

        String[] split = StringUtils.utf8Str(Base64.getDecoder().decode(type)).split(",");
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
                map.put(s, options);
            }
        }

        return ReturnResult.success(map);
    }
}
