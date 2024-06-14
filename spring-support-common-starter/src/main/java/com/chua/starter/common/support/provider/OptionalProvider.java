package com.chua.starter.common.support.provider;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.SpiOption;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.properties.OptionalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 获取选项
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("/option/v1/")
@ConditionalOnProperty(prefix = OptionalProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class OptionalProvider {

    /**
     * 获取选项
     *
     * @param type 类型
     * @return 获取选项
     */
    @GetMapping("get")
    public ReturnResult<List<SpiOption>> captcha(String type) {
        if(StringUtils.isBlank(type)) {
            return ReturnResult.error();
        }
        List<SpiOption> options = ServiceProvider.of(type).options();
        return ReturnResult.success(options);
    }
}
