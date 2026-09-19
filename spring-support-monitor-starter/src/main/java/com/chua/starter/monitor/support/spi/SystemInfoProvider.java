package com.chua.starter.monitor.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.server.support.entity.MsServerHost;

import java.io.InputStream;
import java.util.List;

/**
 * 系统信息提供者 SPI 扩展点。
 *
 * <p>按主机接入方式提供统一的系统信息采集能力，内置 4 种实现：
 * Local(OSHI) / Agent-HTTP / Linux-Mac SSH / Windows WinRM。返回值一律为强类型视图，
 * 通道间的键名与量纲差异在实现内部消化，调用方不再接触散列结构。</p>
 *
 * <p>扩展方式：实现本接口并标注 {@code @Spi(value = "唯一别名", order = N)}，把实现类全限定名追加到
 * {@code META-INF/extensions/com.chua.starter.monitor.support.spi.SystemInfoProvider}（同包及子包内的
 * 实现也会被自动扫描到）。注意三点：</p>
 * <ul>
 *   <li>实例由 SPI 框架自行创建，不进 Spring 容器，因此必须保留无参构造，依赖用
 *       {@code SpringBeanUtils.getBean(...)} 懒查找；</li>
 *   <li>定义按别名归并，别名重复会相互覆盖，每个通道必须用不同别名；</li>
 *   <li>{@code order} 决定路由优先级（大者优先），路由器按降序取第一个 {@code supports} 的实现。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(SystemInfoProvider.SPI_NAME)
public interface SystemInfoProvider {

    /**
     * 系统信息采集 SPI 扩展名
     */
    String SPI_NAME = "system-info-provider";

    /**
     * 是否支持采集该主机。
     *
     * @param host 目标主机
     * @return 支持返回 true
     */
    boolean supports(MsServerHost host);

    /**
     * 目标主机的真实操作系统平台。
     *
     * <p>默认按 {@code serverHostOsType} 解析；当该字段承载的是接入方式（AGENT / LOCAL）
     * 而非平台时，由具体 Provider 用自己的采集结果覆写。</p>
     *
     * @param host 目标主机
     * @return WINDOWS / LINUX / MAC，无法判定时返回空串
     */
    default String platform(MsServerHost host) {
        return Platforms.normalize(host == null ? null : host.getServerHostOsType());
    }

    /**
     * 主机基础信息（CPU / 内存 / 操作系统等）。
     *
     * @param host 目标主机
     * @return 基础信息视图，不为 null
     */
    HostInfoVO hostInfo(MsServerHost host);

    /**
     * 分页查询进程列表。
     *
     * @param host     目标主机
     * @param page     页码
     * @param pageSize 每页条数
     * @return 进程视图列表，永不为 null
     */
    List<ProcessRowVO> processes(MsServerHost host, int page, int pageSize);

    /**
     * 分页查询服务列表。
     *
     * @param host     目标主机
     * @param page     页码
     * @param pageSize 每页条数
     * @return 服务视图列表，永不为 null
     */
    List<ServiceRowVO> services(MsServerHost host, int page, int pageSize);

    /**
     * 磁盘列表。
     *
     * @param host 目标主机
     * @return 磁盘视图列表，永不为 null
     */
    List<DiskRowVO> disks(MsServerHost host);

    /**
     * 网络接口列表。
     *
     * @param host 目标主机
     * @return 网卡视图列表，永不为 null
     */
    List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host);

    /**
     * 网络端口占用列表。
     *
     * @param host 目标主机
     * @return 端口占用视图列表，永不为 null
     */
    List<NetProcessRowVO> netProcesses(MsServerHost host);

    /**
     * 目录文件列表。
     *
     * @param host 目标主机
     * @param path 目录路径
     * @return 文件条目列表，永不为 null
     */
    List<FileEntryVO> files(MsServerHost host, String path);

    /**
     * 上传文件到目标主机。
     *
     * @param host       目标主机
     * @param remotePath 远端路径
     * @param data       数据流
     * @param size       数据长度
     * @return 成功返回 true
     */
    boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size);

    /**
     * 下载目标主机文件。
     *
     * @param host       目标主机
     * @param remotePath 远端路径
     * @return 文件输入流，失败返回 null
     */
    InputStream downloadFile(MsServerHost host, String remotePath);

    /**
     * 在目标主机执行命令。
     *
     * @param host 目标主机
     * @param cmd  命令
     * @return 标准输出内容
     */
    String exec(MsServerHost host, String cmd);

    /**
     * 环境变量列表。
     *
     * @param host 目标主机
     * @return 环境变量条目列表，永不为 null
     */
    List<EnvVarVO> env(MsServerHost host);
}
