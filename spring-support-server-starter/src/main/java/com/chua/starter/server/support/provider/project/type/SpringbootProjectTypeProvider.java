package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;

/**
 * SpringBoot 单体项目：{@code java -jar} 启动，并附带 {@code prod} 配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "springboot", order = 100)
public class SpringbootProjectTypeProvider extends AbstractJavaProjectTypeProvider {

    @Override
    public String type() {
        return "springboot";
    }

    @Override
    public String label() {
        return "SpringBoot 单体";
    }

    @Override
    protected boolean springProfile() {
        return true;
    }
}
