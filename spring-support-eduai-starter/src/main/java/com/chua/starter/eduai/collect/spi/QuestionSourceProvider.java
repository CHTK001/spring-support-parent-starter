package com.chua.starter.eduai.collect.spi;

import java.util.List;

/**
 * 题库采集数据源 SPI。
 * <p>
 * 第三方扩展方式（零侵入 eduai）：实现本接口，实现类标注唯一名称的
 * {@code @Spi(value = "MY_TYPE", order = ...)}，并在自己的 jar 内写入
 * {@code META-INF/extensions/com.chua.starter.eduai.collect.spi.QuestionSourceProvider}
 * （每行一个实现类 FQCN）。注册表按 {@link #type()} 索引，与采集任务表
 * collect_task_source_type 匹配（忽略大小写）。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface QuestionSourceProvider {

    /**
     * 数据源类型标识，如 TEXT/URL/API/FILE
     *
     * @return 类型标识
     */
    String type();

    /**
     * 拉取题目。返回的每题至少要有题干与答案；学科/学段/年级为空时由任务默认值补齐。
     *
     * @param context 采集上下文（任务配置快照）
     * @return 采集到的题目列表
     */
    List<CollectedQuestion> fetch(QuestionSourceContext context);

    /**
     * 是否由 Provider 自行落库（成语等非题目类数据源使用）。
     * <p>返回 true 时，采集流程不再按题目落库，改调 {@link #persist(QuestionSourceContext)} 计数。</p>
     *
     * @return true = 自行落库
     */
    default boolean selfPersisted() {
        return false;
    }

    /**
     * 自行落库并返回写入条数（{@link #selfPersisted()} 为 true 时由采集流程调用）。
     *
     * @param context 采集上下文（任务配置快照）
     * @return 写入条数
     */
    default int persist(QuestionSourceContext context) {
        return 0;
    }

    /**
     * 取原始数据（JSON 文本 / 已解析对象），供「前置处理器 → 入库处理器」统一入库管线使用。
     * <p>返回 null 时采集流程会退化为按任务配置的 url/apiUrl 直接拉取。</p>
     *
     * @param context 采集上下文（任务配置快照）
     * @return 原始数据，未实现返回 null
     */
    default Object fetchRaw(QuestionSourceContext context) {
        return null;
    }
}
