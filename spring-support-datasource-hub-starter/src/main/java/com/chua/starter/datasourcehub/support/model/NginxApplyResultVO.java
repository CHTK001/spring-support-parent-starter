package com.chua.starter.datasourcehub.support.model;

import java.util.List;
import java.util.Objects;

/**
 * 保存 Nginx 配置的执行结果。
 *
 * <p>把「落盘 → 备份 → 语法检查 → 重载」这条链路的每一步结果都摊开返回，
 * 让前端能明确告诉用户「文件已保存但语法检查失败、配置尚未生效」，
 * 而不是笼统地报一个「操作失败」。</p>
 *
 * @param saved        是否已落盘
 * @param message      面向用户的结果摘要
 * @param backupPath   备份文件路径，未备份为 null
 * @param validated    是否执行了语法检查
 * @param validateOk   语法检查是否通过
 * @param validateText 语法检查输出原文
 * @param reloaded     是否执行了重载
 * @param reloadOk     重载是否成功
 * @param content      落盘后的实际内容（UI 模式下与提交内容可能不同，用于确认渲染结果）
 * @param changedLineCount 相对原始内容变化的行数，用于给用户一个改动量感知
 * @param warnings     渲染过程告警
 * @param steps        逐步执行记录，按时间顺序
 * @author CH
 * @since 4.0.0.42
 */
public record NginxApplyResultVO(
        boolean saved,
        String message,
        String backupPath,
        boolean validated,
        boolean validateOk,
        String validateText,
        boolean reloaded,
        boolean reloadOk,
        String content,
        Integer changedLineCount,
        List<String> warnings,
        List<String> steps
) {
    /**
     * 规范构造器：告警与执行步骤做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。两个列表均由本次保存流程就地累加
     * 字符串后随结果返回，恒不为 null 且元素恒不为 null，故可使用拒绝 null 的
     * {@link List#copyOf}。</p>
     *
     * @param warnings 渲染过程告警
     * @param steps    逐步执行记录，按时间顺序
     */
    public NginxApplyResultVO {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings 不能为 null"));
        steps = List.copyOf(Objects.requireNonNull(steps, "steps 不能为 null"));
    }
}
