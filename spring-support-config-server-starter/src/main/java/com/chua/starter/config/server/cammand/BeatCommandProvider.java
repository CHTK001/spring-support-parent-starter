package com.chua.starter.config.server.cammand;

import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.properties.ConfigServerProperties;
import com.google.common.base.Strings;

import javax.servlet.http.HttpServletRequest;

/**
 * 心跳
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
public class BeatCommandProvider implements CommandProvider {
    @Override
    public ReturnResult<String> command(String binder, String data, ConfigServerProperties configServerProperties, HttpServletRequest request) {
        if(Strings.isNullOrEmpty(binder)) {
            return ReturnResult.illegal();
        }

        return ReturnResult.ok();
    }

    @Override
    public String[] named() {
        return new String[]{"beat"};
    }
}
