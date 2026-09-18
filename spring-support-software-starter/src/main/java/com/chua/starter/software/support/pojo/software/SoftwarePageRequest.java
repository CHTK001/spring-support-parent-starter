package com.chua.starter.software.support.pojo.software;

import com.chua.starter.software.support.entity.SoftSoftware;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

import java.util.Collection;

/**
* 软件分页查询请求
*
* @since 2026/7/25
* @author CH
 */
@Data
@Schema(description = "软件分页查询请求")
public class SoftwarePageRequest extends SoftSoftware {

    /**
    * 已安装的软件标识集合，用于排序（已安装的排在前面）
    */
    @Schema(description = "已安装的软件ID集合，用于排序（已安装的排前面）")
    /** installedsoftware标识 */
    private java.util.Collection<String> installedSoftwareIds;

    /**
    * 获取installedsoftware标识
    *
    * @return 获取installedsoftware标识的结果
    */
    public java.util.Collection<String> getInstalledSoftwareIds() {
        return installedSoftwareIds;
    }

    /**
    * 设置installedsoftware标识
    *
    * @param installedSoftwareIds installedsoftware标识
    */
    public void setInstalledSoftwareIds(java.util.Collection<String> installedSoftwareIds) {
        this.installedSoftwareIds = installedSoftwareIds;
    }
}
