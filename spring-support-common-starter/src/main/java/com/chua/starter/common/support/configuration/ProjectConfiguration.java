package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.project.Project;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
* 项目配置
*
* @since 2024/9/6
* @author CH
 */
@Configuration
public class ProjectConfiguration implements EnvironmentAware {

    @Override
    /** 设置环境 */
    public void setEnvironment(Environment environment) {
        Project.getInstance().setEnvironment(environment);
    }
}