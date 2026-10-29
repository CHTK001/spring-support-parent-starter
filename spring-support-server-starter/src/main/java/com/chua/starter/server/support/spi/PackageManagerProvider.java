package com.chua.starter.server.support.spi;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.List;

/**
 * 软件仓库包管理器 提供者 SPI
 * <p>
 * 把"该用什么包管理器、搜索/安装命令怎么拼、输出如何解析"从业务层下沉为可插拔扩展点：
 * 新增包管理器只需实现本接口、标注 {@code @Spi}，并在
 * {@code META-INF/extensions/com.chua.starter.server.support.spi.PackageManagerProvider}
 * 中登记，{@link PackageManagerRouter} 按 {@code @Spi} 的 order 降序取第一个
 * {@link #supports(MsServerHost)} 为真的实现，业务代码零改动。
 * </p>
 * <p>
 * 命令的实际执行仍复用现有通道：搜索走同步执行（{@code ExecService}），
 * 安装走异步任务（{@code ProjectTaskService}）并经 socket 实时推送进度。
 * 与监控模块 {@code SystemInfoProvider} 的路由设计保持一致。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface PackageManagerProvider {

    /**
     * 包管理器标识（如 apt / yum / dnf / winget）
     *
     * @return 标识，不可为空
     */
    String manager();

    /**
     * 判断该包管理器是否适用于目标主机（依据操作系统与已探测的包管理器字段）
     *
     * @param host 目标主机
     * @return true 表示由本实现处理
     */
    boolean supports(MsServerHost host);

    /**
     * 构造软件搜索命令
     *
     * @param keyword 关键词
     * @return 可在目标主机执行的命令
     */
    String searchCommand(String keyword);

    /**
     * 构造软件安装命令
     *
     * @param name 软件包名
     * @return 可在目标主机执行的命令
     */
    String installCommand(String name);

    /**
     * 构造「已安装软件列表」命令（同步执行，输出由 {@link #parseInstalled(String)} 解析）
     *
     * <p>命令需在目标主机直接可执行且非交互；实现若不支持返回 {@code null}，
     * 业务层将给出「该包管理器暂不支持查看已安装列表」的错误。</p>
     *
     * @return 可在目标主机执行的命令
     */
    String installedCommand();

    /**
     * 构造软件卸载命令
     *
     * @param name 软件包名
     * @return 可在目标主机执行的命令
     */
    String uninstallCommand(String name);

    /**
     * 构造软件升级命令（把指定包升到仓库最新版）
     *
     * @param name 软件包名
     * @return 可在目标主机执行的命令
     */
    String upgradeCommand(String name);

    /**
     * 构造「有可用更新的软件列表」命令（同步执行，输出由 {@link #parseUpgradable(String)} 解析）
     *
     * <p>实现若不支持返回 {@code null}，业务层将以空列表作答 —— 前端据此隐藏
     * 全部「升级」按钮，而不是给一个点了没反应的按钮。这条命令的退出码通常
     * 非 0（如 yum/dnf check-update 有更新时返回 100），因此解析不看退出码，
     * 只看输出内容。</p>
     *
     * @return 可在目标主机执行的命令，不支持时为 null
     */
    default String upgradableCommand() {
        return null;
    }

    /**
     * 构造「软件依赖列表」命令（同步执行，输出由 {@link #parseDependencies(String)} 解析）
     *
     * @param name 软件包名
     * @return 可在目标主机执行的命令，不支持时为 null
     */
    default String dependenciesCommand(String name) {
        return null;
    }

    /**
     * 解析「有可用更新」命令输出。
     *
     * <p>返回值里 {@code version} 一律填「仓库中的候选版本」，{@code name} 是包名；
     * 业务层再与已安装列表按包名合并，得出「当前版本 → 候选版本」。</p>
     *
     * @param output 命令标准输出
     * @return 可升级软件包列表，无更新或无法解析时为空列表
     */
    default List<SoftwarePackage> parseUpgradable(String output) {
        return java.util.Collections.emptyList();
    }

    /**
     * 解析「软件依赖」命令输出为依赖名列表。
     *
     * @param output 命令标准输出
     * @return 依赖包名列表（去重、去版本约束），无法解析时为空列表
     */
    default List<String> parseDependencies(String output) {
        return java.util.Collections.emptyList();
    }

    /**
     * 解析搜索命令输出为软件包列表
     *
     * @param output  命令标准输出
     * @param keyword 本次搜索关键词（用于过滤/兜底）
     * @return 软件包列表，无结果时为空列表
     */
    List<SoftwarePackage> parse(String output, String keyword);

    /**
     * 解析「已安装软件列表」命令输出为软件包列表
     *
     * @param output 命令标准输出
     * @return 软件包列表，无结果时为空列表
     */
    List<SoftwarePackage> parseInstalled(String output);
}
