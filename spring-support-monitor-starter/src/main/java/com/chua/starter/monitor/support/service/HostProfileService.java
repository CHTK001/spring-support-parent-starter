package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;

import java.util.List;
import java.util.Map;

/**
 * 主机画像服务（Agent 驱动）
 * <p>
 * 画像页的每一项数据都以 agent HTTP 为准：概览与实时指标在拉取成功后回写主机台账，
 * 这样 agent 掉线时页面仍能显示最后一次已知状态，而不是整页空白。
 * </p>
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
     * @param hostId 主机标识
     * @param persist 是否回写台账（false 时只做只读探测）
     * @return 结果，含 info / metrics / host 三段
     */
    ReturnResult<Map<String, Object>> refresh(Long hostId, boolean persist);

    /**
     * 拉取画像某个列表分区。
     *
     * @param hostId  主机标识
     * @param section 分区名（ports/processes/services/disks/network/env）
     * @param page    页码（仅进程与服务生效），可为 null
     * @param size    每页条数（仅进程与服务生效），可为 null
     * @return 结果列表
     */
    ReturnResult<List<Map<String, Object>>> section(Long hostId, String section, Integer page, Integer size);
}
