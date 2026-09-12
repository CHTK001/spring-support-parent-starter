package com.chua.starter.mybatis.pojo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.chua.starter.common.support.api.annotations.ApiFieldIgnore;
import com.chua.common.support.base.validator.group.IgnoreGroup;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 基础信息
* @author CH
* @since 4.0.0
 */
@Data
public class SysBase implements Serializable {
    /** 串行版本UID */
    private static final long serialVersionUID = 1L;
    /**
    * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人姓名")
    @ApiFieldIgnore(IgnoreGroup.class)
    /** 创建名称 */
    protected String createName;
    /**
    * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人")
    @ApiFieldIgnore(IgnoreGroup.class)
    /** 创建by */
    protected Integer createBy;

    /**
    * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    /** 创建时间 */
    protected LocalDateTime createTime;

    /**
    * 更新时间
     */
    @TableField(fill = FieldFill.UPDATE)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    /** 更新时间 */
    protected LocalDateTime updateTime;


    /**
    * 更新人
     */
    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新人姓名")
    @ApiFieldIgnore(IgnoreGroup.class)
    /** 更新名称 */
    protected String updateName;

    /**
    * 更新人
     */
    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "更新人")
    @ApiFieldIgnore(IgnoreGroup.class)
    /** 更新by */
    protected Integer updateBy;
}
