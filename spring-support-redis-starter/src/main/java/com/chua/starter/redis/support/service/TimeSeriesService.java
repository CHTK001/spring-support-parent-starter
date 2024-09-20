package com.chua.starter.redis.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.indicator.DataIndicator;
import com.chua.common.support.session.indicator.TimeIndicator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间序列服务接口。
 * <p>
 * 该接口定义了对时间序列数据进行操作的一系列方法。
 * 时间序列数据是指按照特定时间顺序收集的数据点，通常用于监控、分析和预测系统状态或事件。
 * <p>
 * 时间序列服务的主要功能包括但不限于：
 * 1. 数据的存储和检索：支持高效地存储和查询大规模时间序列数据。
 * 2. 数据聚合和分析：提供对时间序列数据进行聚合、平均、最大值、最小值等统计分析的功能。
 * 3. 数据可视化：支持将时间序列数据映射到各种图表，以直观地展示数据趋势和模式。
 * 4. 数据报警：允许设置基于时间序列数据的阈值报警规则，及时发现和响应系统异常。
 * <p>
 * 时间序列服务的应用场景非常广泛，例如在物联网(IoT)领域，可以用于监控设备的运行状态和性能指标；
 * 在运维监控领域，可以用于收集和分析系统的日志和性能数据，以优化系统性能和可靠性。
 *
 * @author CH
 * @since 2024/7/4
 */
public interface TimeSeriesService {


    /**
     * 保存数据到指定的存储介质中。
     * <p>
     * 本函数旨在将给定的指标、值映射和时间戳保存到某种存储介质中，例如数据库或文件系统。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator       指标名称，用于标识要保存的数据类型或类别。
     * @param label           值的映射，包含了具体的数据，以键值对的形式组织。
     * @param value           要保存的值，可以是任何类型，但通常为数字或字符串。
     * @param timestamp       数据产生的时间戳，用于记录数据的时间属性。
     * @param retentionPeriod 数据的保留时间，以秒为单位。
     * @return 返回一个表示操作结果的对象，其中包含了操作是否成功的标志。
     */
    ReturnResult<Boolean> save(String indicator, long timestamp, double value, LinkedHashMap<String, String> label, long retentionPeriod);


    /**
     * 保存数据到指定的存储介质中。
     * <p>
     * 本函数旨在将给定的指标、值映射和时间戳保存到某种存储介质中，例如数据库或文件系统。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator       指标名称，用于标识要保存的数据类型或类别。
     * @param value           要保存的值，可以是任何类型，但通常为数字或字符串。
     * @param timestamp       数据产生的时间戳，用于记录数据的时间属性。
     * @param retentionPeriod 数据的保留时间，以秒为单位。
     * @return 返回一个表示操作结果的对象，其中包含了操作是否成功的标志。
     */
    ReturnResult<Boolean> save(String indicator, long timestamp, double value, long retentionPeriod);

    /**
     * 删除指定指标的所有数据。
     * <p>
     * 本函数旨在删除指定指标的所有数据，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator     指标名称，用于标识要删除的数据类型或类别。
     * @param fromTimestamp 数据删除的起始时间戳，用于指定删除数据的时间范围。
     * @param toTimestamp   数据删除的结束时间戳，用于指定删除数据的时间范围。
     * @return 返回一个表示操作结果的对象，其中包含了操作是否成功的标志。
     */
    ReturnResult<Boolean> delete(String indicator, long fromTimestamp, long toTimestamp);


    /**
     * 查询指定指标的数据范围。
     * <p>
     * 本函数旨在查询指定指标的数据范围，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator     指标名称，用于标识要查询的数据类型或类别。
     * @param fromTimestamp 数据查询的起始时间戳，用于指定查询数据的时间范围。
     * @param toTimestamp   数据查询的结束时间戳，用于指定查询数据的时间范围。
     * @param latest        是否最新的放在最前面
     * @param count         数据查询结果的数量限制，用于限制返回的数据数量。
     * @return 返回一个表示操作结果的对象，其中包含了操作是否成功的标志。
     */
    ReturnResult<List<TimeIndicator>> range(String indicator, long fromTimestamp, long toTimestamp, boolean latest, int count);

    /**
     * 查询指定指标的数据范围。
     * <p>
     * 本函数旨在查询指定指标的数据范围，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator     指标名称，用于标识要查询的数据类型或类别。
     * @param fromTimestamp 数据查询的起始时间戳，用于指定查询数据的时间范围。
     * @param toTimestamp   数据查询的结束时间戳，用于指定查询数据的时间范围。
     * @param latest        是否最新的放在最前面
     * @param count         数据查询结果的数量限制，用于限制返回的数据数量。
     */
    ReturnResult<Map<String, List<TimeIndicator>>> mRange(String indicator, long fromTimestamp, long toTimestamp, boolean latest, int count);

    /**
     * 查询指定指标的数据范围。
     * <p>
     * 本函数旨在查询指定指标的数据范围，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator     指标名称，用于标识要查询的数据类型或类别。
     * @param fromTimestamp 数据查询的起始时间戳，用于指定查询数据的时间范围。
     * @param toTimestamp   数据查询的结束时间戳，用于指定查询数据的时间范围。
     * @param count         数据查询结果的数量限制，用于限制返回的数据数量。
     */
    ReturnResult<DataIndicator> get(String indicator, long fromTimestamp, long toTimestamp, int count);

    /**
     * 设置指定指标的数据。
     * <p>
     * 本函数旨在设置指定指标的数据，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator 指标名称，用于标识要设置的数据类型或类别。
     * @param value     数据的JSON字符串表示形式，包含时间戳和值。
     */
    void put(String indicator, String value);

    /**
     * 设置指定指标的数据。
     * <p>
     * 本函数旨在设置指定指标的数据，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator 指标名称，用于标识要设置的数据类型或类别。
     * @param key       数据的JSON字符串表示形式，包含时间戳和值。
     * @param value     数据的JSON字符串表示形式，包含时间戳和值。
     */
    void hSet(String indicator, String key, String value);

    /**
     * 获取指定指标的所有数据。
     * <p>
     * 本函数旨在获取指定指标的所有数据，包括时间戳和值。
     * 它返回一个表示操作成功或失败的结果对象。
     *
     * @param indicator 指标名称，用于标识要获取的数据类型或类别。
     * @return
     */
    Map<String, String> hGet(String indicator);
}

