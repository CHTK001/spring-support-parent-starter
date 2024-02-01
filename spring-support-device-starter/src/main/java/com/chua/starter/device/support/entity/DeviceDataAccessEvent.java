package com.chua.starter.device.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 门禁设备数据
 *
 * @author CH
 * @since 2023/10/30
 */
@Data
@TableName(value = "device_data_access_event")
public class DeviceDataAccessEvent extends DeviceDataEvent implements Serializable {
    @TableId(value = "device_data_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer deviceDataId;

    /**
     * 进出方向;0未知，1:进,2:出
     */
    @TableField(value = "device_data_event_in_or_out")
    @Size(max = 255, message = "进出方向;0未知，1:进,2:出最大长度要小于 255")
    private String deviceDataEventInOrOut;

    /**
     * 卡号
     */
    @TableField(value = "device_data_card")
    @Size(max = 255, message = "卡号最大长度要小于 255")
    private String deviceDataCard;

    /**
     * 人员id
     */
    @TableField(value = "device_data_person_id")
    @Size(max = 255, message = "人员id最大长度要小于 255")
    private String deviceDataPersonId;

    /**
     * 人员编号
     */
    @TableField(value = "device_data_person_num")
    @Size(max = 255, message = "人员编号最大长度要小于 255")
    private String deviceDataPersonNum;

    /**
     * 姓名
     */
    @TableField(value = "device_data_persion_name")
    @Size(max = 255, message = "姓名最大长度要小于 255")
    private String deviceDataPersionName;

    /**
     * 人员类型
     */
    @TableField(value = "device_data_persion_type")
    @Size(max = 255, message = "人员类型最大长度要小于 255")
    private String deviceDataPersionType;

    /**
     * 证件号码
     */
    @TableField(value = "device_data_cert")
    @Size(max = 255, message = "证件号码最大长度要小于 255")
    private String deviceDataCert;

    /**
     * 手机号码
     */
    @TableField(value = "device_data_phone")
    @Size(max = 255, message = "手机号码最大长度要小于 255")
    private String deviceDataPhone;

    /**
     * 人员组织
     */
    @TableField(value = "device_data_org_id")
    @Size(max = 255, message = "人员组织最大长度要小于 255")
    private String deviceDataOrgId;

    /**
     * 人员组织路径
     */
    @TableField(value = "device_data_org_path_name")
    @Size(max = 255, message = "人员组织路径最大长度要小于 255")
    private String deviceDataOrgPathName;

    /**
     * 人员分组
     */
    @TableField(value = "device_data_persion_group_name")
    @Size(max = 255, message = "人员分组最大长度要小于 255")
    private String deviceDataPersionGroupName;

    /**
     * 人脸照片
     */
    @TableField(value = "device_data_face_url")
    @Size(max = 255, message = "人脸照片最大长度要小于 255")
    private String deviceDataFaceUrl;

    /**
     * 抓拍照片
     */
    @TableField(value = "device_data_pic_url")
    @Size(max = 255, message = "抓拍照片最大长度要小于 255")
    private String deviceDataPicUrl;

    /**
     * 人体温度
     */
    @TableField(value = "device_data_temperature")
    @Size(max = 255, message = "人体温度最大长度要小于 255")
    private String deviceDataTemperature;

    private static final long serialVersionUID = 1L;
}