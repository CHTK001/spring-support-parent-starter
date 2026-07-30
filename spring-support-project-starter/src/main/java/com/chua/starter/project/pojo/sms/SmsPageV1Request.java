package com.chua.starter.project.pojo.sms;

import com.chua.common.support.utils.group.SelectGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.entity.project.SysSmsTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询sms
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "查询sms")
public class SmsPageV1Request extends Query<SysSmsTemplate> {

    /**
     * 项目ID
     */
    @Schema(description = "项目ID")
    @NotNull(message = "项目ID不能为空", groups = SelectGroup.class)
    private Integer sysProjectId;

    /**
     * 模板内容
     */
    @Schema(description = "模板内容(支持模糊查询)")
    private String sysSmsTemplateContent;
}
