package com.chua.starter.test;

import com.chua.common.support.objects.ObjectContext;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import com.chua.starter.osgi.shell.GogoSshShellServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * osgi 真实测试（CD = 已启用=false）：{@code plugin.osgi.enabled=false} 时
 * osgi 自动装配应整体退避——Spring 容器中不应存在任何 osgi 相关 Bean
 * （felixlauncher / osgibeandefinition注册 / gogosshshell服务端），
 * 且 对象上下文 不应查询到 osgi 类型的服务。
 *
 * <p>独立 context（区别于其他 OSGi 测试的 enabled=true 上下文），
 * 专门验证退避门控 {@code @ConditionalOnProperty(prefix="plugin.osgi", name="enabled", havingValue="true")}。</p>
 *
 * @author CH
 * @since 2026/09/11
 */
@SpringBootTest(properties = {
        "plugin.osgi.enabled=false"
})
/**
 * osgi已禁用real测试类。
 *
 * @author CH
 * @since 4.0.0
 */
class OsgiDisabledRealTest {

    @Autowired
    private ObjectProvider<FelixOsgiLauncher> osgiLauncherProvider; // osgilauncher提供者

    @Autowired
    private ObjectProvider<OsgiBeanDefinitionRegister> osgiRegisterProvider; // osgi注册提供者

    @Autowired
    private ObjectProvider<GogoSshShellServer> sshShellProvider; // sshshell提供者

    @Autowired
    private ObjectContext objectContext; // 对象上下文

    /** 真实测试：已启用=false 时各 osgi Bean 均不应装配 */
    @Test
    void osgiBeansNotProvisionedWhenDisabled() {
        /**
        * 断言空。
        * @param osgiLauncherProvider.getIfAvailable( osgilauncher提供者.获取if可用(
         */
        assertNull(osgiLauncherProvider.getIfAvailable(),
                "plugin.osgi.enabled=false 时不应装配 FelixOsgiLauncher");
        /**
         * 断言空。
         * @param osgiRegisterProvider.getIfAvailable( osgi注册提供者.getifavailable(
         */
        assertNull(osgiRegisterProvider.getIfAvailable(),
                "plugin.osgi.enabled=false 时不应装配 OsgiBeanDefinitionRegister");
        /**
         * 断言空。
         * @param sshShellProvider.getIfAvailable( sshshell提供者.获取if可用(
         */
        assertNull(sshShellProvider.getIfAvailable(),
                "plugin.osgi.enabled=false 时不应装配 GogoSshShellServer");
    }

    /** 真实测试：已启用=false 时 对象上下文 不应桥接任何 osgi 类型服务 */
    @Test
    void noOsgiBeanBridgedInObjectContext() {
        /**
        * 断言true。
        * @param objectContext.getBeanOfTypeCollection(FelixOsgiLauncher.class 对象上下文.获取Bean的类型集合(felixosgilauncher.类
         */
        assertTrue(objectContext.getBeanOfTypeCollection(FelixOsgiLauncher.class).isEmpty(),
                "ObjectContext 不应桥接 FelixOsgiLauncher");
        /**
         * 断言空。
         * @param objectContext.getBeanOfTypeSafely(GogoSshShellServer.class 对象上下文.获取Bean的类型safely(gogosshshellserver.类
         */
        assertNull(objectContext.getBeanOfTypeSafely(GogoSshShellServer.class),
                "ObjectContext 不应查到 GogoSshShellServer");
    }
}