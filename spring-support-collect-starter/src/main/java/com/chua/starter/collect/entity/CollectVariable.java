package com.chua.starter.collect.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采集全局变量。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("collect_variable")
@Schema(description = "数据采集-全局变量")
public class CollectVariable extends SysBase {

    /**
     * 主键ID。
     */
    @TableId(value = "collect_variable_id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Integer collectVariableId;

    /**
     * 变量名。
     */
    @Schema(description = "变量名")
    private String collectVariableName;

    /**
     * 变量值。
     */
    @Schema(description = "变量值")
    private String collectVariableValue;

    /**
     * 是否加密，0 明文 1 密文。
     */
    @Schema(description = "是否加密 0明文 1密文")
    private Integer collectVariableEncrypted;

    /**
     * 说明。
     */
    @Schema(description = "说明")
    private String collectVariableDescription;
}
