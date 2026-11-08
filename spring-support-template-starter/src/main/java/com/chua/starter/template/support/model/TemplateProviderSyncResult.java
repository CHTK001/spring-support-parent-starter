package com.chua.starter.template.support.model;

import lombok.Data;

/**
 * 提供商同步结果
 *
 * <p>与前端 {@code ProviderSyncResult} 一一对应：一次同步后新增、更新、失败各多少条。</p>
 *
 * @since 4.0.0.80
 * @author CH
 */
@Data
public class TemplateProviderSyncResult {

    /** 新增模板数 */
    private Integer added = 0;

    /** 更新模板数 */
    private Integer updated = 0;

    /** 失败数 */
    private Integer failed = 0;

    /**
     * 累加一次同步结果
     *
     * @param other 另一个同步结果
     * @return 当前对象
     */
    public TemplateProviderSyncResult plus(TemplateProviderSyncResult other) {
        if (other == null) {
            return this;
        }
        this.added += other.added == null ? 0 : other.added;
        this.updated += other.updated == null ? 0 : other.updated;
        this.failed += other.failed == null ? 0 : other.failed;
        return this;
    }
}
