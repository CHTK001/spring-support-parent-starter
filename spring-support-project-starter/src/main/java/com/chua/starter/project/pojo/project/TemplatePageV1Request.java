package com.chua.starter.project.pojo.project;

import com.chua.common.support.utils.group.SelectGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.entity.project.SysSmsTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模板分页请求
 *
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "模板查询请求")
public class TemplatePageV1Request extends Query<SysSmsTemplate> {

    /**
     * 项目ID
     */
    @Schema(description = "项目ID")
    @NotNull(message = "项目ID不能为空", groups = SelectGroup.class)
    private Integer sysProjectId;

    /**
     * 设备名称
     */
    @Schema(description = "设备名称(支持模糊查询)")
    private String sysDeviceName;

    /**
     * 设备序列号
     */
    @Schema(description = "设备序列号(支持模糊查询)")
    private String sysDeviceSerialNumber;

    /**
     * 设备状态
     */
    @Schema(description = "设备是否禁用;0:启用")
    private Integer sysDeviceStatus;


    /**
     * 设备是否在线
     */
    @Schema(description = "设备是否在线;0:离线")
    private Integer sysDeviceOnline;

}
