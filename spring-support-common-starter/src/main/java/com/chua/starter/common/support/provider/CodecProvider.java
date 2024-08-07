package com.chua.starter.common.support.provider;

import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.utils.IoUtils;
import com.chua.starter.common.support.application.GlobalFactory;
import com.chua.starter.common.support.application.Sign;
import com.chua.starter.common.support.properties.CodecProperties;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 编码
 * @author CH
 * @since 2024/8/6
 */
@RestController
@Slf4j
@Api(tags = "获取脚本")
@Tag(name = "获取脚本")
@RequestMapping("/v1/script")
@ConditionalOnProperty(prefix = CodecProperties.PRE, name = "enable", havingValue = "true", matchIfMissing = true)
public class CodecProvider {

    @Autowired
    private CodecProperties codecProperties;

    private static String SCRIPT;
    static  {
        {
            try {
                String script = IoUtils.toString(CodecProvider.class.getResourceAsStream("/uu.js"), StandardCharsets.UTF_8);
                SCRIPT = script.replace("{{sign1}}",  GlobalFactory.getInstance().get(Sign.class).getSign1());
            } catch (Exception ignored) {
            }
        }
    }
    @Ignore
    @Operation(summary = "获取uu.js")
    @GetMapping(value = "uu.js", produces = "application/javascript")
    public String getUu() {
        return SCRIPT;
    }

}
