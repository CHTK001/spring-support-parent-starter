package com.chua.starter.common.support.provider;

import com.chua.starter.common.support.properties.OptionProperties;
import com.chua.starter.common.support.result.Result;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置服务
 * @author CH
 */
@RestController
@RequestMapping("/options")
public class OptionsProvider {
    private final Map<String, Object> config = new LinkedHashMap<>();

    public OptionsProvider(OptionProperties optionProperties, Environment environment) {
        initial(optionProperties, environment);
    }

    private void initial(OptionProperties optionProperties, Environment environment) {
        Map<String, Object> options = optionProperties.getOptions();
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            config.put(entry.getKey(), environment.resolvePlaceholders(entry.getValue() + ""));
        }
    }

    /**
     * 配置接口
     * @return 配置接口
     */
    @RequestMapping(value = "s2d3ff", method = RequestMethod.OPTIONS)
    public Result<Map<String, Object>> options() {
        return Result.success(config);
    }
}
