package com.chua.starter.mybatis.pojo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SysBase implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private String createName;
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;


    @TableField(fill = FieldFill.UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @Schema(description = "更新人")
    private String updateName;

    @TableField(fill = FieldFill.UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @Schema(description = "更新人")
    private String updateBy;
}
