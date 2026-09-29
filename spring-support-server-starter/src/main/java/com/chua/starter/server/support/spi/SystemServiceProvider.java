package com.chua.starter.server.support.spi;

/**
 * 目标平台系统服务挂载提供者 SPI（按系统类型扩展）。
 *
 * <p>把「项目 → 操作系统服务」的落地能力按平台拆开：实现类负责本平台的服务名规则、
 * 前台启动器、挂载命令与卸载命令，调用方（{@code ReleaseServiceImpl.installService} /
 * {@code uninstallService}）只负责按平台取实现并发起异步任务，自身不再含任何平台分支。</p>
 *
 * <p>新增平台只需：实现本接口、标注 {@code @Spi}、登记到
 * {@code META-INF/extensions/com.chua.starter.server.support.spi.SystemServiceProvider}，
 * 挂载入口零改动；未登记的平台会得到明确的「暂不支持」错误，而不是被静默降级成
 * 别的平台命令（在 Linux 上生成 systemd 命令去 macOS 上执行只会失败）。</p>
 *
 * <p><b>实现约定：</b></p>
 * <ul>
 *     <li>{@link #install} 生成的脚本必须「失败即失败」：命令失败必须让整段脚本非零退出，
 *     不得用 {@code || true} / {@code exit 0} 吞掉错误，否则调用方会拿到假成功；</li>
 *     <li>{@link #uninstall} 必须以「校验终态」收尾（服务已停、定义文件已删），
 *     校验不通过时返回非零，避免出现「报成功但服务还在跑」；</li>
 *     <li>需要提权时统一用 {@code sudo -n}：无免密 sudo 的主机应立即失败并给出可读原因，
 *     而不是挂在密码提示上直到超时。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface SystemServiceProvider {

    /**
     * 支持的操作系统类型。
     *
     * <p>取值域与 {@code HostPlatforms} 一致（LINUX/MACOS/WINDOWS/FREEBSD/AIX/SOLARIS），
     * 注册中心按本值建索引。</p>
     *
     * @return 系统类型标识（全大写）
     */
    String osType();

    /**
     * 生成默认服务名（用户未指定服务名时使用）。
     *
     * @param ctx 挂载上下文
     * @return 服务名（需满足本平台命名约束）
     */
    String defaultServiceName(SystemServiceContext ctx);

    /**
     * 生成前台启动器内容：常驻进程，由服务管理器直接拉起并跟踪。
     *
     * <p>启动器负责把 {@link SystemServiceContext#env()} 导出为环境变量、把
     * {@code JVM_ARGS}/{@code APP_ARGS} 装配好，再 exec 真实进程，使服务管理器
     * 跟踪到的是业务进程本身（崩溃即退出，守护策略才生效）。</p>
     *
     * @param ctx 挂载上下文
     * @return 启动器脚本文本
     */
    String launcher(SystemServiceContext ctx);

    /**
     * 生成挂载命令（写服务定义 + 注册 + 启动 + 校验）。
     *
     * @param ctx         挂载上下文
     * @param serviceName 服务名
     * @return 可直接交给远端执行器运行的脚本
     */
    String install(SystemServiceContext ctx, String serviceName);

    /**
     * 生成卸载命令（停止 + 注销 + 删除定义 + 校验终态）。
     *
     * @param ctx         挂载上下文
     * @param serviceName 服务名
     * @return 可直接交给远端执行器运行的脚本
     */
    String uninstall(SystemServiceContext ctx, String serviceName);
}
