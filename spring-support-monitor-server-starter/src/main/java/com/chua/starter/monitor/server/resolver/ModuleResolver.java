package com.chua.starter.monitor.server.resolver;

import com.chua.common.support.protocol.request.Response;
import com.chua.starter.monitor.server.request.ReportQuery;

/**
 * 命令解析器
 *
 * @author CH
 */
public interface ModuleResolver {
    /**
     * 根据报告查询条件解析报告。
     *
     * 本方法接收一个ReportQuery对象作为参数，该对象包含了查询报告所需的所有条件，
     * 如报告的类型、时间范围、特定的指标等。方法通过对这个对象的分析和处理，来生成
     * 对应的报告响应。
     *
     * @param reportQuery 报告查询条件对象，包含了所有用于查询报告的条件。
     * @return 返回一个Response对象，该对象包含了查询报告的结果以及可能的错误信息。
     */
    Response resolve(ReportQuery reportQuery);

}
