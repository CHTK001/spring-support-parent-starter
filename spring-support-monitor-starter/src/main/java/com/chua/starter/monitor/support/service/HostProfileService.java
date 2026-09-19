package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.HostProfileVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;

import java.util.List;

/**
 * 主机画像服务。
 *
 * <p>画像页的每一项数据都经 {@code SystemInfoProvider} 通道取得（Agent / SSH / WinRM / 本机 OSHI），
 * 概览与实时指标在拉取成功后回写主机台账，这样通道掉线时页面仍能显示最后一次已知状态，
 * 而不是整页空白。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface HostProfileService {

    /**
     * 画像分区：监听端口
     */
    String SECTION_PORTS = "ports";

    /**
     * 画像分区：进程
     */
    String SECTION_PROCESSES = "processes";

    /**
     * 画像分区：服务
     */
    String SECTION_SERVICES = "services";

    /**
     * 画像分区：磁盘
     */
    String SECTION_DISKS = "disks";

    /**
     * 画像分区：网卡
     */
    String SECTION_NETWORK = "network";

    /**
     * 画像分区：环境变量
     */
    String SECTION_ENV = "env";

    /**
     * 拉取概览 + 实时指标并回写主机台账。
     *
     * @param hostId  主机标识
     * @param persist 是否回写台账（false 时只做只读探测）
     * @return 概览视图，含 info / metrics / host
     */
    ReturnResult<HostProfileVO> refresh(Long hostId, boolean persist);

    /**
     * 监听端口分区。
     *
     * @param hostId 主机标识
     * @return 端口占用行
     */
    ReturnResult<List<NetProcessRowVO>> ports(Long hostId);

    /**
     * 进程分区。
     *
     * @param hostId 主机标识
     * @param page   页码，可为 null
     * @param size   每页条数，可为 null
     * @return 进程视图列表
     */
    ReturnResult<List<ProcessRowVO>> processes(Long hostId, Integer page, Integer size);

    /**
     * 服务分区。
     *
     * @param hostId 主机标识
     * @param page   页码，可为 null
     * @param size   每页条数，可为 null
     * @return 服务视图列表
     */
    ReturnResult<List<ServiceRowVO>> services(Long hostId, Integer page, Integer size);

    /**
     * 磁盘分区。
     *
     * @param hostId 主机标识
     * @return 磁盘视图
     */
    ReturnResult<List<DiskRowVO>> disks(Long hostId);

    /**
     * 网卡分区。
     *
     * @param hostId 主机标识
     * @return 网卡视图
     */
    ReturnResult<List<NetworkInterfaceRowVO>> network(Long hostId);

    /**
     * 环境变量分区。
     *
     * @param hostId 主机标识
     * @return 环境变量视图
     */
    ReturnResult<List<EnvVarVO>> env(Long hostId);
}
