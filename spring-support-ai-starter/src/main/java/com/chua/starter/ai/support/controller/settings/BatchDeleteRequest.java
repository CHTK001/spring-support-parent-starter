package com.chua.starter.ai.support.controller.settings;

import java.util.List;

/**
 * 批量删除请求体（AppKey / 模型定义共用）。
 *
 * <p><b>为什么用强类型 DTO 而不是 {@code Map<String, ?>} + 强转。</b>
 * JSON 反序列化出的整数默认是 {@code Integer}，{@code (List<Long>) body.get("ids")}
 * 在泛型擦除下强转运行期不报错，等服务层增强 for 按 {@code Long} 拆箱遍历时才抛
 * {@code ClassCastException: Integer cannot be cast to Long}，接口 500（历史缺陷，
 * 批量删除 AppKey/模型均命中）。改用声明 {@code List<Long>} 的 record 后，
 * Jackson 按声明类型把元素规整为 {@code Long}，与 {@code SysAiSessionController}
 * batch-archive 的逐元素归一口径一致。</p>
 *
 * @param ids 待删除的主键列表
 * @since 2026-11-24
 * @author CH
 */
public record BatchDeleteRequest(List<Long> ids) {
}
