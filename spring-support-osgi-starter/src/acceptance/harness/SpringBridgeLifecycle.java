package harness;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.starter.osgi.properties.OsgiProperties;
import com.chua.starter.osgi.support.publish.BundleSpiAutoPublisher;
import com.chua.starter.osgi.support.publish.DefaultOsgiServicePublisher;
import com.example.spi.GreetingApi;
import org.osgi.framework.Bundle;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Real Spring registry, Felix bundles and production publishers; no listener. */
public final class SpringBridgeLifecycle {
    private static int total;
    private static int failed;

    private SpringBridgeLifecycle() {
    }

    private static void check(String name, boolean success) {
        total++;
        if (!success) {
            failed++;
        }
        System.out.println((success ? "PASS " : "FAIL ") + name);
    }

    private static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Felix lifecycle event did not settle");
            }
            Thread.sleep(10);
        }
    }

    public static void main(String[] args) throws Exception {
        Path work = Path.of(args[0]);
        FelixOsgiLauncher launcher = new FelixOsgiLauncher();
        launcher.start(Map.of("org.osgi.framework.storage", Files.createTempDirectory(work, "spring-felix-").toString(),
                "org.osgi.framework.system.packages.extra",
                "com.example.spi,com.chua.common.support.spi.annotations"));
        GenericApplicationContext spring = new GenericApplicationContext();
        BundleSpiAutoPublisher auto = null;
        Bundle bundle = null;
        try {
            spring.getBeanFactory().registerSingleton("osgiLauncher", launcher);
            DefaultOsgiServicePublisher publisher = new DefaultOsgiServicePublisher(
                    spring.getBeanFactory().getBeanProvider(FelixOsgiLauncher.class));
            spring.getBeanFactory().registerSingleton("osgiPublisher", publisher);
            auto = new BundleSpiAutoPublisher(launcher, publisher, new OsgiProperties.Spi());
            spring.getBeanFactory().registerSingleton("bundleSpiPublisher", auto);
            spring.refresh();
            auto.init();
            check("publisher is a real active Spring singleton", spring.getBean(DefaultOsgiServicePublisher.class) == publisher
                    && publisher.isActive());
            ServiceProvider<GreetingApi> provider = ServiceProvider.of(GreetingApi.class);
            GreetingApi proxy = provider.getValidProvider();
            check("host starts without plugin", provider.isEmpty());
            bundle = launcher.getFrameworkBundleContext().installBundle(Path.of(args[1]).toUri().toString());
            check("installation alone does not publish SPI", provider.isEmpty());
            bundle.start();
            await(() -> provider.getExtension((String) null) != null);
            GreetingApi first = provider.getExtension((String) null);
            check("Spring publisher exposes plugin to host", first != null && "v1:host".equals(first.greet("host")));
            check("existing host proxy calls plugin", "v1:proxy".equals(proxy.greet("proxy")));
            check("host definition retains annotation order", !provider.getDefinitions(null).isEmpty()
                    && provider.getDefinitions(null).stream().allMatch(it -> it.getOrder() == 77));
            check("host priority preserves registered plugin instance", provider.getPriority() == first);
            check("host bridge does not persist plugin as a static default", provider.getDefault() == null);
            bundle.stop();
            await(provider::isEmpty);
            check("stopping plugin clears host SPI", provider.isEmpty() && provider.getExtension((String) null) == null);
            check("stopping plugin clears priority", provider.getPriority() == null);
            bundle.start();
            await(() -> provider.getExtension((String) null) != null);
            GreetingApi restarted = provider.getExtension((String) null);
            check("restart publishes a fresh instance", restarted != null && restarted != first
                    && "v1:restart".equals(restarted.greet("restart")));
            try (var input = Files.newInputStream(Path.of(args[2]))) {
                bundle.update(input);
            }
            if (bundle.getState() != Bundle.ACTIVE) {
                bundle.start();
            }
            await(() -> {
                GreetingApi current = provider.getExtension((String) null);
                return current != null && "v2:ready".equals(current.greet("ready"));
            });
            GreetingApi upgraded = provider.getExtension((String) null);
            check("update replaces implementation without host restart", upgraded != null && upgraded != restarted
                    && "v2:host".equals(upgraded.greet("host")));
            check("existing host proxy calls updated plugin", "v2:proxy".equals(proxy.greet("proxy")));
            check("update leaves one host-visible implementation", provider.getNewExtensions(null).size() == 1);
            bundle.uninstall();
            await(provider::isEmpty);
            bundle = null;
            check("uninstall clears definitions and cached default", provider.isEmpty() && provider.getDefault() == null
                    && provider.getExtension((String) null) == null);
        } finally {
            if (bundle != null && bundle.getState() != Bundle.UNINSTALLED) {
                bundle.uninstall();
            }
            if (auto != null) {
                auto.destroy();
            }
            spring.close();
            launcher.stop();
        }
        System.out.println("RESULT springBridgeTotal=" + total + " fail=" + failed);
        if (failed != 0) {
            throw new AssertionError("Spring bridge failures=" + failed);
        }
    }
}
