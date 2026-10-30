package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.model.DevicePageQuery;
import com.chua.starter.device.support.model.DeviceStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备信息 映射器
 *
 * <p>简单单表 CRUD 走 MyBatis-Plus；分页、统计、候选设备这类多条件 SQL
 * 放在 {@code mapper/device/DeviceInfoMapper.xml}，避免在 Service 里堆
 * {@code LambdaQueryWrapper} 条件，便于 DBA 审核与执行计划调优。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface DeviceInfoMapper extends BaseMapper<DeviceInfo> {

    /**
     * 设备分页查询（XML）
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<DeviceInfo> selectDevicePage(IPage<DeviceInfo> page,
                                       @Param("q") DevicePageQuery query);

    /**
     * 设备台账统计（XML，一次查询算出总数/在线/离线/未定位）
     *
     * @param orgId  组织 ID，可选
     * @param vendor 厂商，可选
     * @return 统计结果
     */
    DeviceStats selectDeviceStats(@Param("orgId") String orgId,
                                  @Param("vendor") String vendor);

    /**
     * 在线检测候选设备（XML）
     *
     * <p>包含离线设备，否则设备掉线后永远不会再进入检测；排除停用/报废。</p>
     *
     * @return 候选设备
     */
    List<DeviceInfo> selectCheckableDevices();

    /**
     * 指定凭据维度的在线检测候选设备（XML）
     *
     * <p>只包含 {@code deviceInfoCredentialId = credentialId} 的设备，
     * 由按凭据切分的在离线检测任务使用。</p>
     *
     * @param credentialId 凭据 ID
     * @return 候选设备
     */
    List<DeviceInfo> selectCheckableDevicesByCredentialId(@Param("credentialId") String credentialId);

    /**
     * 按厂商 + 序列号/编码查询设备（厂商目录同步的幂等键）。
     *
     * @param vendor 厂商标识
     * @param serial 厂商侧设备标识/序列号
     * @return 已存在设备，未找到返回 null
     */
    DeviceInfo selectByVendorAndSerial(@Param("vendor") String vendor,
                                       @Param("serial") String serial);
}
