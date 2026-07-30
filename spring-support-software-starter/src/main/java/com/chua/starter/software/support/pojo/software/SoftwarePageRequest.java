package com.chua.starter.software.support.pojo.software;

import com.chua.starter.software.support.entity.SoftSoftware;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

import java.util.Collection;

/**
 * 软件分页查询请求
 *
 * @author CH
 * @since 2026/7/25
 */
@Data
@Schema(description = "软件分页查询请求")
public class SoftwarePageRequest extends SoftSoftware {

    /**
     * 已安装的软件ID集合，用于排序（已安装的排在前面）
     */
    @Schema(description = "已安装的软件ID集合，用于排序（已安装的排前面）")
    private java.util.Collection<String> installedSoftwareIds;

    public java.util.Collection<String> getInstalledSoftwareIds() {
        return installedSoftwareIds;
    }

    public void setInstalledSoftwareIds(java.util.Collection<String> installedSoftwareIds) {
        this.installedSoftwareIds = installedSoftwareIds;
    }
}