package com.chua.starter.spider.support.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 凭证池（加密存储）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("spider_credential")
public class SpiderCredential {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 凭证显示名称 */
    @TableField("credential_name")
    private String credentialName;

    /** 凭证类型（BASIC/COOKIE/TOKEN/SMS_CODE） */
    @TableField("credential_type")
    private String credentialType;

    /** AES 加密后的凭证内容 JSON */
    @TableField("encrypted_data")
    private String encryptedData;

    /** 适用域名（如 gitee.com） */
    private String domain;

    /** 备注说明 */
    private String description;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
