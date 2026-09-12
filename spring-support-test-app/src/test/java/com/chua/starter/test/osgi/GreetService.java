package com.chua.starter.test.osgi;

/**
   * 应用侧 osgi 服务接口（跨 bundle 桥接契约）。
 *
 * <p>bundle 内的实现类通过 {@code Import-Package: com.chua.starter.test.osgi}
 * 引用本接口（框架经 {@code org.osgi.framework.system.packages.extra} 导出，
 * 类身份与应用侧一致），实现类真正实现的是应用加载的这个接口，
 * 使 {@code OsgiLauncher.getService(type)} 的 {@code type.cast(service)} 可以成功。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public interface GreetService {

    /**
     * 问候。
     *
     * @param name 名称
     * @return 问候语
     */
    String greet(String name);
}