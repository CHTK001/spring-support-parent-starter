package com.chua.starter.device.support.model;

import lombok.Data;

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
