// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.device.support.model;

import lombok.Data;

import java.util.List;

/**
 * 设备分页查询条件
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class DevicePageQuery {

    /** 页码，从 1 开始 */
    private long pageNo = 1;

    /** 每页条数，最大 200 */
    private long pageSize = 20;

    /** 关键字：名称/编码/IP/序列号/负责人 */
    private String keyword;

    /** 组织 ID */
    private String orgId;

    /**
     * 组织 ID 集合（左树选中组织时连同子孙组织一起导出，优先于 {@link #orgId}）。
     */
    private List<String> orgIds;

    /** 厂商 */
    private String vendor;

    /** 大类 */
    private String category;

    /** 小类 */
    private String subCategory;

    /** 在线状态；null 表示全部 */
    private Boolean online;

    /** 设备状态：normal/disabled/maintenance/scrapped */
    private String status;

    /** 所属分组 ID */
    private String groupId;

    /**
     * 分组 ID 集合（左树选中分组时连同子分组一起导出，优先于 {@link #groupId}）。
     */
    private List<String> groupIds;

    /**
     * 设备 ID 集合（左树定位到单台设备时只导出该设备）。
     */
    private List<String> ids;

    /** 仅看未分组设备（groupId 为空时生效） */
    private Boolean ungrouped;

    /** 负责人 ID */
    private String ownerId;

    /** 标签 */
    private String tag;

    /** 排序字段：updatedAt/createdAt/lastOnlineAt/name */
    private String sortBy = "updatedAt";

    /** 排序方向：asc/desc */
    private String sortOrder = "desc";

    /**
     * 规范化分页参数，避免前端传入 0 或超大页
     *
     * @return 当前对象
     */
    public DevicePageQuery normalize() {
        if (pageNo < 1) {
            pageNo = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }
        return this;
    }
}