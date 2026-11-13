package com.chua.starter.device.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 台账导入结果（干模拟与写库共用同一结构）。
 *
 * <p>前端导入弹窗先按 {@code dryRun=true} 拿一份预览，确认无误后再按
 * {@code dryRun=false} 真正写库；两次返回的结构一致，弹窗只切换按钮文案。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class DeviceImportResult {

    /**
     * 读取到的数据行数（不含表头）
     */
    private int total;

    /**
     * 新增行数
     */
    private int createCount;

    /**
     * 更新行数
     */
    private int updateCount;

    /**
     * 跳过行数
     */
    private int skipCount;

    /**
     * 错误行数
     */
    private int errorCount;

    /**
     * 逐行结果
     */
    private List<DeviceImportRow> rows = new ArrayList<>();

    /**
     * 追加一行结果并同步计数。
     *
     * @param row 单行结果
     */
    public void add(DeviceImportRow row) {
        rows.add(row);
        switch (row.getAction()) {
            case "create" -> createCount++;
            case "update" -> updateCount++;
            case "skip" -> skipCount++;
            default -> errorCount++;
        }
    }
}
