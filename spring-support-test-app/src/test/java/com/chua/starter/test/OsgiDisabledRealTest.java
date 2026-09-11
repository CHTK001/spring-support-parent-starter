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
 * OSGi 真实测试（CD = enabled=false）：{@code plugin.osgi.enabled=false} 时
 * OSGi 自动装配应整体退避——Spring 容器中不应存在任何 OSGi 相关 Bean
 * （FelixLauncher / OsgiBeanDefinitionRegister / GogoSshShellServer），
 * 且 ObjectContext 不应查询到 OSGi 类型的服务。
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
class OsgiDisabledRealTest {

    @Autowired
    private ObjectProvider<FelixOsgiLauncher> osgiLauncherProvider;

    @Autowired
    private ObjectProvider<OsgiBeanDefinitionRegister> osgiRegisterProvider;

    @Autowired
    private ObjectProvider<GogoSshShellServer> sshShellProvider;

    @Autowired
    private ObjectContext objectContext;

    /** 真实测试：enabled=false 时各 OSGi Bean 均不应装配 */
    @Test
    void osgiBeansNotProvisionedWhenDisabled() {
        assertNull(osgiLauncherProvider.getIfAvailable(),
                "plugin.osgi.enabled=false 时不应装配 FelixOsgiLauncher");
        assertNull(osgiRegisterProvider.getIfAvailable(),
                "plugin.osgi.enabled=false 时不应装配 OsgiBeanDefinitionRegister");
        assertNull(sshShellProvider.getIfAvailable(),
                "plugin.osgi.enabled=false 时不应装配 GogoSshShellServer");
    }

    /** 真实测试：enabled=false 时 ObjectContext 不应桥接任何 OSGi 类型服务 */
    @Test
    void noOsgiBeanBridgedInObjectContext() {
        assertTrue(objectContext.getBeanOfTypeCollection(FelixOsgiLauncher.class).isEmpty(),
                "ObjectContext 不应桥接 FelixOsgiLauncher");
        assertNull(objectContext.getBeanOfTypeSafely(GogoSshShellServer.class),
                "ObjectContext 不应查到 GogoSshShellServer");
    }
}