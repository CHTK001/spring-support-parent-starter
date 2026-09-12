package com.chua.starter.datasync.support.spi;

/**
   * 动态服务接口 - 由 osgi Bundle 实现并注册为服务。
 *
 * <p>任何实现该接口的类可以作为 OSGi Bundle 的一部分被打包成 jar，
   * 安装到 Felix 框架后会被自动注册为 osgi 服务。
 * Spring 应用侧可通过 {@code GET /test/integration/osgi/services?type=DynamicService} 查询。</p>
 *
 * <p>实现示例（Bundle 端）：</p>
 * <pre>{@code
 * public class HelloDynamicService implements DynamicService, org.osgi.framework.BundleActivator {
 *     @Override
 *     public String getName() {
 *         return "HelloService";
 *     }
 *
 *     @Override
 *     public String execute(String input) {
 *         return "Hello from OSGi bundle! input=" + input;
 *     }
 *
 *     @Override
 *     public void start(BundleContext ctx) throws Exception {
 *         ctx.registerService(DynamicService.class.getName(), this, null);
 *     }
 *
 *     @Override
 *     public void stop(BundleContext ctx) throws Exception {}
 * }
 * }</pre>x Linux 停止(bundle上下文 ctx) 抛出 异常 {}
 * }
 * }</pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DynamicService {

    /**
     * 获取服务名称。
     * @return 获取名称的结果
     */
    String getName();

    /**
     * 执行服务逻辑。
     *
     * @param input 输入参数
     * @return 执行结果
     */
    String execute(String input);
}
