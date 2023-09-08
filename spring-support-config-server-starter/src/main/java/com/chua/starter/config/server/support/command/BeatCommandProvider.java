package com.chua.starter.config.server.support.command;

import com.chua.common.support.annotations.Spi;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.config.server.support.manager.DataManager;
import com.google.common.base.Strings;

import javax.servlet.http.HttpServletRequest;

/**
 * 心跳
 *
 * @author CH
 * @since 2022/8/1 9:20
 */
@Spi("beat")
public class BeatCommandProvider implements CommandProvider {
    @Override
    public ReturnResult<String> command(String binder, String data, String dataType, String applicationProfile, DataManager dataManager, HttpServletRequest request) {
        if (Strings.isNullOrEmpty(binder)) {
            return ReturnResult.illegal();
        }

        return ReturnResult.ok();
    }
}
