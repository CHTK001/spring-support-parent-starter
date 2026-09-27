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
     * 解析搜索命令输出为软件包列表
     *
     * @param output  命令标准输出
     * @param keyword 本次搜索关键词（用于过滤/兜底）
     * @return 软件包列表，无结果时为空列表
     */
    List<SoftwarePackage> parse(String output, String keyword);
}
