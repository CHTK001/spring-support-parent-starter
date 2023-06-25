package com.chua.starter.common.support.provider;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.annotations.SpiOption;
import com.chua.common.support.lang.arrange.ArrangeHandler;
import com.chua.common.support.lang.arrange.ArrangeResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author CH
 */
@Spi("demo")
@Component
@SpiOption("测试例子")
public class DemoArrangeHandler implements ArrangeHandler {
    @Override
    public ArrangeResult execute(Map<String, Object> args) {
        return null;
    }
}
