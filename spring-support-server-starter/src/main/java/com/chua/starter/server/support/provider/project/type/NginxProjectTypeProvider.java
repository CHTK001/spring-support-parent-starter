package com.chua.starter.server.support.provider.project.type;

import com.chua.common.support.spi.annotations.Spi;

/**
 * Nginx 项目：与静态站点共用同一套脚本与投放规则，单独登记类型标识便于前端区分。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "nginx", order = 90)
public class NginxProjectTypeProvider extends StaticProjectTypeProvider {

    @Override
    public String type() {
        return "nginx";
    }

    @Override
    public String label() {
        return "Nginx";
    }
}
