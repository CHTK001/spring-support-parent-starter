package com.chua.starter.template.support.service;

import com.chua.starter.template.support.entity.TemplateInfo;
import com.chua.starter.template.support.model.TemplateSourceRequest;

/**
 * 来源模板同步器接口
 *
 * <p>负责在上下文启动后（或收到刷新请求时），把来源模块的模板行完整同步到
 * 统一模板库 {@code template_info}。同步方向始终是「来源模块 → 模板库」，
 * 库中的行通过 {@code template_info_source} + {@code template_info_source_id}
 * 与来源行建立映射。</p>
 *
 * <h3>同步语义</h3>
 * <ol>
 *   <li>存在（source + sourceId）对应的 {@code template_info} 行：按请求更新</li>
 *   <li>来源行已不存在（逻辑删除 / 手动删掉）：库中对应行软删除</li>
 *   <li>来源行不存在：库中不创建</li>
 * </ol>
 *
 * @see TemplateService#registerTemplate(TemplateSourceRequest)
 * @since 4.0.0.52
 * @author CH
 */
public interface SourceSyncer {

    /**
     * 来源标识：message / paper
     *
     * @return 来源标识，用于请求路径与筛选条件
     */
    String source();

    /**
     * 全量同步来源模块的模板行到统一模板库，并返回源行最终在库中的数量
     *
     * @return 库中该来源的模板数量
     * @throws Exception 同步异常（由上层记录日志，不中断应用启动）
     */
    int sync() throws Exception;

    /**
     * 把来源行注册为模板库行（幂等：存在就更新，不存在就新建）
     *
     * @param request 来源模板注册请求
     * @return 注册后的模板信息实体
     */
    TemplateInfo register(TemplateSourceRequest request);
}
