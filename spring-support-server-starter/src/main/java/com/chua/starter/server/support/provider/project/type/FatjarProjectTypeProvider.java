package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;

/**
 * FatJar 项目：{@code java -jar} 启动，不附加任何 profile。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "fatjar", order = 100)
public class FatjarProjectTypeProvider extends AbstractJavaProjectTypeProvider {

    @Override
    public String type() {
        return "fatjar";
    }

    @Override
    public String label() {
        return "FatJar";
    }

    @Override
    protected boolean springProfile() {
        return false;
    }
}
