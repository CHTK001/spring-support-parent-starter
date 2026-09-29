package com.chua.starter.eduai.support.credential;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.eduai.dimension.mapper.EduStudentMapper;
import com.chua.starter.oauth.client.support.credential.CredentialProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把教育域的凭据校验实现注册进 oauth 的扩展点。
 *
 * <p>为什么用显式 {@code ServiceProvider.register} 而不是 {@code @Spi} 注解：
 * {@code @Spi} 依赖 SPI 容器扫描，而本仓的默认扫描器是
 * {@code SamePackageServiceResolver}（只扫接口自身所在包）加
 * {@code ServiceLoaderServiceResolver}（只认 {@code META-INF/services}）。
 * eduai 既是另一个模块、又是另一个包，两条路都扫不到。
 * 显式注册把「谁提供学生登录」写在代码里，读代码就能找到，不依赖构建期资源文件。</p>
 *
 * <p>注册名 {@code student} 与登录请求里的 {@code accountType=student} 对应。
 * oauth 侧解析顺序是「自定义同名实现优先、内置 builtin- 实现其次」，
 * 因此这里注册的名字与内置的 {@code builtin-*} 不冲突，不会互相覆盖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Configuration
public class EduCredentialConfiguration {

    /**
     * 注册学生凭据校验实现。
     *
     * @param studentMapper 学生档案 Mapper，用于按学号 / 家长手机号反查
     * @return 已注册的凭据实现，同时交由 Spring 管理其生命周期
     */
    @Bean
    public CredentialProvider studentCredentialProvider(EduStudentMapper studentMapper) {
        var provider = new StudentCredentialProvider(studentMapper);
        ServiceProvider.of(CredentialProvider.class).register("student", provider);
        log.info("[EduCredential] 已注册学生凭据校验实现 - 注册名: student");
        return provider;
    }
}
