package com.chua.starter.server.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.common.support.annotations.PrivacyEncrypt;
import com.chua.starter.common.support.rule.PrivacyTypeEnum;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * @author CH
 */
@Data
@TableName(value = "doorplate")
public class Doorplate {
    @TableField(value = "id")
    @Size(max = 255, message = "最大长度要小于 255")
    private String id;

    @TableField(value = "create_by")
    @Size(max = 255, message = "最大长度要小于 255")
    private String createBy;

    @TableField(value = "create_time")
    @Size(max = 255, message = "最大长度要小于 255")
    private String createTime;

    @TableField(value = "update_by")
    @Size(max = 255, message = "最大长度要小于 255")
    private String updateBy;

    @TableField(value = "update_time")
    @Size(max = 255, message = "最大长度要小于 255")
    private String updateTime;

    @TableField(value = "sys_org_code")
    @Size(max = 255, message = "最大长度要小于 255")
    private String sysOrgCode;

    @TableField(value = "code")
    @Size(max = 255, message = "最大长度要小于 255")
    @PrivacyEncrypt(type = PrivacyTypeEnum.NAME)
    private String code;

    @TableField(value = "county")
    @Size(max = 255, message = "最大长度要小于 255")
    private String county;

    @TableField(value = "street")
    @Size(max = 255, message = "最大长度要小于 255")
    private String street;

    @TableField(value = "village")
    @Size(max = 255, message = "最大长度要小于 255")
    private String village;

    @TableField(value = "`owner`")
    @Size(max = 255, message = "最大长度要小于 255")
    private String owner;

    @TableField(value = "document_type")
    @Size(max = 255, message = "最大长度要小于 255")
    private String documentType;

    @TableField(value = "document_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String documentNum;

    @TableField(value = "document_user")
    @Size(max = 255, message = "最大长度要小于 255")
    private String documentUser;

    @TableField(value = "`type`")
    @Size(max = 255, message = "最大长度要小于 255")
    private String type;

    @TableField(value = "address")
    @Size(max = 255, message = "最大长度要小于 255")
    private String address;

    @TableField(value = "open_room_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String openRoomNum;

    @TableField(value = "floor_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String floorNum;

    @TableField(value = "span")
    @Size(max = 255, message = "最大长度要小于 255")
    private String span;

    @TableField(value = "property_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String propertyNum;

    @TableField(value = "latitude")
    @Size(max = 255, message = "最大长度要小于 255")
    private String latitude;

    @TableField(value = "longitude")
    @Size(max = 255, message = "最大长度要小于 255")
    private String longitude;

    @TableField(value = "declarant")
    @Size(max = 255, message = "最大长度要小于 255")
    private String declarant;

    @TableField(value = "declarant_phone")
    @Size(max = 255, message = "最大长度要小于 255")
    private String declarantPhone;

    @TableField(value = "declared_time")
    @Size(max = 255, message = "最大长度要小于 255")
    private String declaredTime;

    @TableField(value = "community_police")
    @Size(max = 255, message = "最大长度要小于 255")
    private String communityPolice;

    @TableField(value = "social_worker")
    @Size(max = 255, message = "最大长度要小于 255")
    private String socialWorker;

    @TableField(value = "`state`")
    @Size(max = 255, message = "最大长度要小于 255")
    private String state;

    @TableField(value = "is_del")
    @Size(max = 255, message = "最大长度要小于 255")
    private String isDel;

    @TableField(value = "map_name")
    @Size(max = 255, message = "最大长度要小于 255")
    private String mapName;

    @TableField(value = "old_address")
    @Size(max = 255, message = "最大长度要小于 255")
    private String oldAddress;

    @TableField(value = "registration_time")
    @Size(max = 255, message = "最大长度要小于 255")
    private String registrationTime;

    @TableField(value = "unicode")
    @Size(max = 255, message = "最大长度要小于 255")
    private String unicode;

    @TableField(value = "source_id")
    @Size(max = 255, message = "最大长度要小于 255")
    private String sourceId;

    @TableField(value = "source_primary")
    @Size(max = 255, message = "最大长度要小于 255")
    private String sourcePrimary;

    @TableField(value = "click_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String clickNum;

    @TableField(value = "doorplate")
    @Size(max = 255, message = "最大长度要小于 255")
    private String doorplate;

    @TableField(value = "licensee_name")
    @Size(max = 255, message = "最大长度要小于 255")
    private String licenseeName;

    @TableField(value = "licensee_phone")
    @Size(max = 255, message = "最大长度要小于 255")
    private String licenseePhone;

    @TableField(value = "licensee_id_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String licenseeIdNum;

    @TableField(value = "authorizer_name")
    @Size(max = 255, message = "最大长度要小于 255")
    private String authorizerName;

    @TableField(value = "authorizer_phone")
    @Size(max = 255, message = "最大长度要小于 255")
    private String authorizerPhone;

    @TableField(value = "authorizer_id_num")
    @Size(max = 255, message = "最大长度要小于 255")
    private String authorizerIdNum;

    @TableField(value = "licensee_time")
    @Size(max = 255, message = "最大长度要小于 255")
    private String licenseeTime;

    @TableField(value = "delicensee_time")
    @Size(max = 255, message = "最大长度要小于 255")
    private String delicenseeTime;

    @TableField(value = "licensee_state")
    @Size(max = 255, message = "最大长度要小于 255")
    private String licenseeState;

    @TableField(value = "story")
    @Size(max = 255, message = "最大长度要小于 255")
    private String story;

    @TableField(value = "merchant_file")
    @Size(max = 255, message = "最大长度要小于 255")
    private String merchantFile;

    @TableField(value = "family")
    @Size(max = 255, message = "最大长度要小于 255")
    private String family;

    @TableField(value = "family_honor")
    @Size(max = 255, message = "最大长度要小于 255")
    private String familyHonor;

    @TableField(value = "merchant_name")
    @Size(max = 255, message = "最大长度要小于 255")
    private String merchantName;
}