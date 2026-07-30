package com.chua.starter.project.pojo.email;

import com.chua.common.support.utils.group.SelectGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 同步请求
 * @author CH
 */
@Data
@Schema(description = "同步请求")
public class EmailSyncV1Request {

    /**
     * 项目ID
     */
    @Schema(description = "项目ID")
    @NotNull(message = "项目ID不能为空", groups = SelectGroup.class)
    private Integer sysProjectId;
}
