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
public class OptionalProvider {

    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @GetMapping("objects/get")
    public ReturnResult<List<SpiOption>> objects(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }

        type = StringUtils.utf8Str(Base64.getDecoder().decode(type));
        Class<?> aClass = ClassUtils.toType(type);
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
    @GetMapping("get")
    public ReturnResult<List<SpiOption>> get(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }

        type = StringUtils.utf8Str(Base64.getDecoder().decode(type));
        List<SpiOption> options = ServiceProvider.of(type).options();
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
    @GetMapping("list")
    public ReturnResult<Map<String, List<SpiOption>>> list(String type, @RequestParam(required =false) String name) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }

        String[] split = type.split(",");
        Map<String, List<SpiOption>> map = MapUtils.newHashMap();
        for (String s : split) {
            s = StringUtils.utf8Str(Base64.getDecoder().decode(s));
            List<SpiOption> options = ServiceProvider.of(s).options();
            if(!StringUtils.isBlank(name)) {
                loop: for (SpiOption option : options) {
                    if(StringUtils.equalsIgnoreCase(option.getName(), name)) {
                        map.put(s, Collections.singletonList(option));
                        break loop;
                    }
                }
                continue;
            }
            map.put(s, options);
        }

        return ReturnResult.success(map);
    }
}
