package com.chua.starter.device.support.model;

import com.chua.starter.device.support.entity.DeviceInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备轻索引行
 *
 * <p>{@code GET /api/device/index} 的返回行：只带前端左树 / 计数 / 快速筛选 /
 * 标签池需要的字段，不携带连接参数、凭据等冗余信息。台账、考勤、过车、传感器
 * 历史等页面的设备下拉与过滤池都以这份轻索引为唯一来源。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class DeviceIndexItem implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 设备 标识
     */
    private String deviceInfoId;

    /**
     * 设备名称
     */
    private String deviceInfoName;

    /**
     * 设备编码
     */
    private String deviceInfoCode;

    /**
     * 所属组织 标识
     */
    private String deviceInfoOrgId;

    /**
     * 厂商
     */
    private String deviceInfoVendor;

    /**
     * 设备大类
     */
    private String deviceInfoCategory;

    /**
     * 设备子类
     */
    private String deviceInfoSubCategory;

    /**
     * 是否在线
     */
    private Boolean deviceInfoOnline;

    /**
     * 资产状态：normal / disabled / scrapped
     */
    private String deviceInfoStatus;

    /**
     * 区域/楼层
     */
    private String deviceInfoArea;

    /**
     * 标签（逗号分隔）
     */
    private String deviceInfoTags;

    /**
     * 维保到期日
     */
    private LocalDate deviceInfoWarrantyEnd;

    /**
     * 纬度
     */
    private BigDecimal deviceInfoLatitude;

    /**
     * 经度
     */
    private BigDecimal deviceInfoLongitude;

    /**
     * 安装日期
     */
    private LocalDate deviceInfoInstallAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime updateTime;

    /**
     * 由完整设备实体裁剪出轻索引行
     *
     * @param d 设备实体
     * @return 轻索引行
     */
    public static DeviceIndexItem from(DeviceInfo d) {
        DeviceIndexItem item = new DeviceIndexItem();
        item.setDeviceInfoId(d.getDeviceInfoId());
        item.setDeviceInfoName(d.getDeviceInfoName());
        item.setDeviceInfoCode(d.getDeviceInfoCode());
        item.setDeviceInfoOrgId(d.getDeviceInfoOrgId());
        item.setDeviceInfoVendor(d.getDeviceInfoVendor());
        item.setDeviceInfoCategory(d.getDeviceInfoCategory());
        item.setDeviceInfoSubCategory(d.getDeviceInfoSubCategory());
        item.setDeviceInfoOnline(d.getDeviceInfoOnline());
        item.setDeviceInfoStatus(d.getDeviceInfoStatus());
        item.setDeviceInfoArea(d.getDeviceInfoArea());
        item.setDeviceInfoTags(d.getDeviceInfoTags());
        item.setDeviceInfoWarrantyEnd(d.getDeviceInfoWarrantyEnd());
        item.setDeviceInfoLatitude(d.getDeviceInfoLatitude());
        item.setDeviceInfoLongitude(d.getDeviceInfoLongitude());
        item.setDeviceInfoInstallAt(d.getDeviceInfoInstallAt());
        item.setUpdateTime(d.getUpdateTime());
        return item;
    }
}
