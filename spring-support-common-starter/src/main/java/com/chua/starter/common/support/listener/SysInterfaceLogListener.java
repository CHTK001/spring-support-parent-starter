package com.chua.starter.common.support.listener;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.logger.InterfaceLoggerInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;

/**
 * 接口日志监听
 *
 * @author CH
 */
@Slf4j
public class SysInterfaceLogListener implements ApplicationListener<InterfaceLoggerInfo> {
    @Override
    public void onApplicationEvent(InterfaceLoggerInfo event) {
        log.info("{}: {} {}?{}, 消息体:{}", event.getIp(), event.getMethod(), event.getUrl(), StringUtils.defaultString(event.getQueryParams(), CommonConstant.EMPTY), event.getBody().length);
    }
}
