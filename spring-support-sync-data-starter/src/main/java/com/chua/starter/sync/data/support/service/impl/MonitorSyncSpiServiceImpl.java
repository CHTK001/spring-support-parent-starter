package com.chua.starter.sync.data.support.service.impl;

import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.adapter.SpiConfigAdapterManager;
import com.chua.starter.sync.data.support.sync.SpiInfo;
import com.chua.starter.sync.data.support.sync.SpiParameter;
import com.chua.starter.sync.data.support.sync.SpiTypeList;
import com.chua.starter.sync.data.support.service.sync.MonitorSyncSpiService;
import com.chua.starter.sync.data.support.util.SpiParameterResolver;
import com.chua.starter.sync.data.support.util.SyncDataSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 同步任务 SPI 服务实现
 *
 * @author CH
 * @since 2024/12/19
 */
@Slf4j
@Service
public class MonitorSyncSpiServiceImpl implements MonitorSyncSpiService {

    private static final String TYPE_INPUT = "INPUT";
    private static final String TYPE_OUTPUT = "OUTPUT";
    private static final String TYPE_DATA_CENTER = "DATA_CENTER";
    private static final String TYPE_FILTER = "FILTER";
    private static final String INPUT_CLASS = "com.chua.sync.support.input.Input";
    private static final String OUTPUT_CLASS = "com.chua.sync.support.output.Output";
    private static final String DATA_CENTER_CLASS = "com.chua.common.support.spi.data.Sink";
    private static final String FILTER_CLASS = "com.chua.sync.support.filter.Filter";

    @Autowired(required = false)
    private SpiConfigAdapterManager adapterManager;

    @Override
    public ReturnResult<List<SpiInfo>> getInputList() {
        return getSpiList(resolveSpiClass(INPUT_CLASS), TYPE_INPUT);
    }

    @Override
    public ReturnResult<List<SpiInfo>> getOutputList() {
        return getSpiList(resolveSpiClass(OUTPUT_CLASS), TYPE_OUTPUT);
    }

    @Override
    public ReturnResult<List<SpiInfo>> getDataCenterList() {
        return getSpiList(resolveSpiClass(DATA_CENTER_CLASS), TYPE_DATA_CENTER);
    }

    @Override
    public ReturnResult<List<SpiInfo>> getDataFilterList() {
        return getSpiList(resolveSpiClass(FILTER_CLASS), TYPE_FILTER);
    }

    @Override
    public ReturnResult<List<SpiParameter>> getParameters(String type, String name) {
        if (type == null || name == null) {
            return ReturnResult.error("参数类型或名称不能为空");
        }

        try {
            type = type.toUpperCase();

            List<SpiParameter> params;
            switch (type) {
                case TYPE_INPUT:
                    params = getInputParameters(name);
                    break;
                case TYPE_OUTPUT:
                    params = getOutputParameters(name);
                    break;
                case TYPE_DATA_CENTER:
                    params = getDataCenterParameters(name);
                    break;
                case TYPE_FILTER:
                    params = getFilterParameters(name);
                    break;
                default:
                    return ReturnResult.error("不支持的类型: " + type);
            }

            if (params.isEmpty()) {
                params = getParametersFromSpi(type, name);
            }

            return ReturnResult.ok(params);
        } catch (Exception e) {
            log.error("获取参数列表失败, type: {}, name: {}", type, name, e);
            return ReturnResult.error("获取参数失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Boolean> validateConfig(String type, String name, Map<String, Object> config) {
        if (type == null || name == null) {
            return ReturnResult.error("参数类型或名称不能为空");
        }

        try {
            List<SpiParameter> parameters = getParameters(type, name).getData();
            if (parameters == null) {
                parameters = new ArrayList<>();
            }

            for (SpiParameter param : parameters) {
                if (Boolean.TRUE.equals(param.getRequired())) {
                    Object value = config.get(param.getName());
                    if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                        return ReturnResult.error("参数 " + param.getLabel() + " 不能为空");
                    }
                }
            }

            return ReturnResult.ok(true);
        } catch (Exception e) {
            log.error("验证配置失败, type: {}, name: {}", type, name, e);
            return ReturnResult.error("验证失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<String> testConnection(String type, String name, Map<String, Object> config) {
        if (type == null || name == null) {
            return ReturnResult.error("参数类型或名称不能为空");
        }

        try {
            type = type.toUpperCase();

            switch (type) {
                case TYPE_INPUT:
                    return testInputConnection(name, config);
                case TYPE_OUTPUT:
                    return testOutputConnection(name, config);
                case TYPE_DATA_CENTER:
                    return testDataCenterConnection(name, config);
                default:
                    return ReturnResult.error("不支持的类型: " + type);
            }
        } catch (Exception e) {
            log.error("测试连接失败, type: {}, name: {}", type, name, e);
            return ReturnResult.error("测试失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<SpiTypeList> getAllSpiTypes() {
        SpiTypeList list = new SpiTypeList();
        list.setInput(getInputList().getData());
        list.setOutput(getOutputList().getData());
        list.setDataCenter(getDataCenterList().getData());
        list.setFilter(getDataFilterList().getData());
        return ReturnResult.ok(list);
    }

    /**
     * 获取 SPI 列表
     */
    private ReturnResult<List<SpiInfo>> getSpiList(Class<?> spiClass, String type) {
        try {
            if (spiClass == null) {
                return ReturnResult.ok(buildDefaultSpiInfos(type));
            }

            ServiceProvider<?> provider = ServiceProvider.of(spiClass);
            List<String> names = new ArrayList<>(provider.getExtensions());

            List<SpiInfo> infos = new ArrayList<>();
            for (String name : names) {
                SpiInfo info = new SpiInfo();
                info.setName(name);
                info.setType(type);
                info.setAvailable(true);

                SpiInfo metaInfo = SpiParameterResolver.resolveSpiInfo(spiClass, name, type);
                mergeInfo(info, metaInfo);
                enrichInfo(info, name, type);
                infos.add(info);
            }

            infos = infos.stream()
                    .sorted(Comparator.comparing(SpiInfo::getOrder, Comparator.nullsLast(Integer::compareTo)))
                    .collect(Collectors.toList());

            return ReturnResult.ok(infos);
        } catch (Exception e) {
            log.error("获取 SPI 列表失败, type: {}", type, e);
            return ReturnResult.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 合并信息
     */
    private void mergeInfo(SpiInfo target, SpiInfo source) {
        if (source == null) {
            return;
        }

        if (source.getDisplayName() != null && !source.getDisplayName().isEmpty()) {
            target.setDisplayName(source.getDisplayName());
        }
        if (source.getDescription() != null && !source.getDescription().isEmpty()) {
            target.setDescription(source.getDescription());
        }
        if (source.getIcon() != null && !source.getIcon().isEmpty()) {
            target.setIcon(source.getIcon());
        }
        if (source.getColor() != null && !source.getColor().isEmpty()) {
            target.setColor(source.getColor());
        }
        if (source.getOrder() != null) {
            target.setOrder(source.getOrder());
        }
        if (source.getClassName() != null) {
            target.setClassName(source.getClassName());
        }
    }

    /**
     * 补齐默认展示信息
     */
    private void enrichInfo(SpiInfo info, String name, String type) {
        if (info.getDisplayName() == null || info.getDisplayName().isEmpty()) {
            info.setDisplayName(getDisplayName(name, type));
        }
        if (info.getDescription() == null || info.getDescription().isEmpty()) {
            info.setDescription(getDescription(name, type));
        }
        if (info.getIcon() == null || info.getIcon().isEmpty()) {
            info.setIcon(getIcon(name, type));
        }
        if (info.getColor() == null || info.getColor().isEmpty()) {
            info.setColor(getColor(name, type));
        }
    }

    /**
     * 获取参数列表
     */
    private List<SpiParameter> getParametersFromSpi(String type, String name) {
        switch (type) {
            case TYPE_INPUT:
                return resolveSpiParameters(INPUT_CLASS, name);
            case TYPE_OUTPUT:
                return resolveSpiParameters(OUTPUT_CLASS, name);
            case TYPE_DATA_CENTER:
                return resolveSpiParameters(DATA_CENTER_CLASS, name);
            case TYPE_FILTER:
                return resolveSpiParameters(FILTER_CLASS, name);
            default:
                return Collections.emptyList();
        }
    }

    /**
     * 获取 Input 参数定义
     */
    private List<SpiParameter> getInputParameters(String spiName) {
        List<SpiParameter> params = new ArrayList<>();

        switch (spiName.toLowerCase()) {
            case "jdbc":
                params.add(createParameter("url", "数据库URL", "string", true, "jdbc:mysql://localhost:3306/test", "数据库连接URL"));
                params.add(createParameter("driverClassName", "驱动类名", "string", false, "com.mysql.cj.jdbc.Driver", "JDBC驱动类名"));
                params.add(createParameter("username", "用户名", "string", true, null, "数据库用户名"));
                params.add(createParameter("password", "密码", "password", true, null, "数据库密码"));
                params.add(createParameter("sql", "查询SQL", "textarea", true, null, "SELECT语句"));
                params.add(createParameter("fetchSize", "批次大小", "number", false, 1000, "每次获取的记录数"));
                params.add(createParameterWithOptions("connectionPool", "连接池类型", "select", false, "hikari", "连接池实现", "HikariCP:hikari,Druid:druid,无:none"));
                break;
            case "csv":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "CSV文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK,GB2312:GB2312,ISO-8859-1:ISO-8859-1"));
                params.add(createParameter("delimiter", "分隔符", "string", false, ",", "字段分隔符"));
                params.add(createParameter("hasHeader", "包含表头", "boolean", false, true, "是否包含表头"));
                params.add(createParameter("quoteChar", "引号字符", "string", false, "\"", "字段包围字符"));
                params.add(createParameter("linesToSkip", "跳过行数", "number", false, 0, "跳过文件开头的行数"));
                params.add(createParameter("trimValues", "去除空白", "boolean", false, true, "是否去除字段值首尾空白"));
                break;
            case "tsv":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "TSV文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK,GB2312:GB2312,ISO-8859-1:ISO-8859-1"));
                params.add(createParameter("hasHeader", "包含表头", "boolean", false, true, "是否包含表头"));
                params.add(createParameter("linesToSkip", "跳过行数", "number", false, 0, "跳过文件开头的行数"));
                break;
            case "json":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "JSON文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK"));
                params.add(createParameter("jsonPath", "JSON路径", "string", false, "$", "数据提取路径(JSONPath表达式)"));
                params.add(createParameter("isArray", "数组格式", "boolean", false, true, "JSON是否为数组格式"));
                break;
            case "xml":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "XML文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK"));
                params.add(createParameter("rootPath", "根路径", "string", true, null, "数据节点XPath表达式"));
                params.add(createParameter("rowPath", "行路径", "string", true, null, "单条数据的XPath表达式"));
                break;
            case "http":
                params.add(createParameter("url", "接口URL", "string", true, null, "HTTP接口地址"));
                params.add(createParameterWithOptions("method", "请求方法", "select", false, "GET", "HTTP请求方法", "GET:GET,POST:POST,PUT:PUT,DELETE:DELETE"));
                params.add(createParameter("headers", "请求头", "keyvalue", false, null, "HTTP请求头"));
                params.add(createParameter("params", "请求参数", "keyvalue", false, null, "URL查询参数"));
                params.add(createParameter("body", "请求体", "textarea", false, null, "POST/PUT请求体内容"));
                params.add(createParameterWithOptions("bodyType", "请求体类型", "select", false, "json", "请求体格式", "JSON:json,表单:form,文本:text"));
                params.add(createParameter("jsonPath", "数据路径", "string", false, "$.data", "响应数据提取路径"));
                params.add(createParameter("timeout", "超时时间(ms)", "number", false, 30000, "请求超时时间"));
                params.add(createParameter("retryCount", "重试次数", "number", false, 3, "请求失败重试次数"));
                break;
            case "mock":
                params.add(createParameter("count", "数据量", "number", false, 100, "生成的数据条数"));
                params.add(createParameter("interval", "间隔(ms)", "number", false, 0, "每条数据生成间隔"));
                params.add(createParameter("template", "数据模板", "json", false, null, "模拟数据模板(JSON格式)"));
                break;
            default:
                break;
        }

        return params;
    }

    /**
     * 获取 Output 参数定义
     */
    private List<SpiParameter> getOutputParameters(String spiName) {
        List<SpiParameter> params = new ArrayList<>();

        switch (spiName.toLowerCase()) {
            case "jdbc":
                params.add(createParameter("url", "数据库URL", "string", true, "jdbc:mysql://localhost:3306/test", "数据库连接URL"));
                params.add(createParameter("driverClassName", "驱动类名", "string", false, "com.mysql.cj.jdbc.Driver", "JDBC驱动类名"));
                params.add(createParameter("username", "用户名", "string", true, null, "数据库用户名"));
                params.add(createParameter("password", "密码", "password", true, null, "数据库密码"));
                params.add(createParameter("table", "目标表", "string", true, null, "目标表名"));
                params.add(createParameter("insertSql", "自定义SQL", "textarea", false, null, "自定义INSERT语句(可选，不填则自动生成)"));
                params.add(createParameter("batchSize", "批次大小", "number", false, 1000, "批量写入大小"));
                params.add(createParameterWithOptions("writeMode", "写入模式", "select", false, "INSERT", "写入模式", "插入:INSERT,更新:UPDATE,插入或更新:UPSERT"));
                params.add(createParameter("autoCommit", "自动提交", "boolean", false, false, "是否自动提交事务"));
                params.add(createParameterWithOptions("connectionPool", "连接池类型", "select", false, "hikari", "连接池实现", "HikariCP:hikari,Druid:druid,无:none"));
                break;
            case "csv":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "CSV输出文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK,GB2312:GB2312"));
                params.add(createParameter("delimiter", "分隔符", "string", false, ",", "字段分隔符"));
                params.add(createParameter("writeHeader", "写入表头", "boolean", false, true, "是否写入表头"));
                params.add(createParameter("quoteChar", "引号字符", "string", false, "\"", "字段包围字符"));
                params.add(createParameterWithOptions("fileMode", "文件模式", "select", false, "overwrite", "文件写入模式", "覆盖:overwrite,追加:append"));
                break;
            case "tsv":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "TSV输出文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK,GB2312:GB2312"));
                params.add(createParameter("writeHeader", "写入表头", "boolean", false, true, "是否写入表头"));
                params.add(createParameterWithOptions("fileMode", "文件模式", "select", false, "overwrite", "文件写入模式", "覆盖:overwrite,追加:append"));
                break;
            case "json":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "JSON输出文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK"));
                params.add(createParameter("prettyPrint", "格式化输出", "boolean", false, true, "是否格式化JSON"));
                params.add(createParameterWithOptions("arrayMode", "输出模式", "select", false, "array", "JSON输出格式", "数组:array,行分隔:ndjson"));
                break;
            case "xml":
                params.add(createParameter("path", "文件路径", "filepath", true, null, "XML输出文件路径"));
                params.add(createParameterWithOptions("encoding", "编码", "select", false, "UTF-8", "文件编码", "UTF-8:UTF-8,GBK:GBK"));
                params.add(createParameter("rootElement", "根元素", "string", false, "data", "XML根元素名称"));
                params.add(createParameter("rowElement", "行元素", "string", false, "row", "单条数据元素名称"));
                params.add(createParameter("prettyPrint", "格式化输出", "boolean", false, true, "是否格式化XML"));
                break;
            case "http":
                params.add(createParameter("url", "接口URL", "string", true, null, "HTTP接口地址"));
                params.add(createParameterWithOptions("method", "请求方法", "select", false, "POST", "HTTP请求方法", "POST:POST,PUT:PUT,PATCH:PATCH"));
                params.add(createParameter("headers", "请求头", "keyvalue", false, null, "HTTP请求头"));
                params.add(createParameterWithOptions("bodyType", "请求体类型", "select", false, "json", "数据发送格式", "JSON:json,表单:form"));
                params.add(createParameter("batchSize", "批次大小", "number", false, 100, "每次批量发送的数据条数"));
                params.add(createParameter("timeout", "超时时间(ms)", "number", false, 30000, "请求超时时间"));
                params.add(createParameter("retryCount", "重试次数", "number", false, 3, "请求失败重试次数"));
                break;
            case "console":
                params.add(createParameterWithOptions("format", "输出格式", "select", false, "JSON", "输出格式", "JSON:JSON,TABLE:TABLE,LINE:LINE"));
                params.add(createParameter("maxLength", "最大长度", "number", false, 1000, "单条数据最大输出长度"));
                break;
            default:
                break;
        }

        return params;
    }

    /**
     * 获取 DataCenter 参数定义
     */
    private List<SpiParameter> getDataCenterParameters(String spiName) {
        List<SpiParameter> params = new ArrayList<>();

        switch (spiName.toLowerCase()) {
            case "local":
                params.add(createParameter("capacity", "队列容量", "number", false, 10000, "内存队列容量"));
                params.add(createParameter("ackEnabled", "启用ACK", "boolean", false, false, "是否启用消息确认"));
                params.add(createParameter("ackTimeout", "ACK超时(ms)", "number", false, 30000, "消息确认超时时间"));
                params.add(createParameter("deadLetterEnabled", "启用死信", "boolean", false, false, "是否启用死信队列"));
                params.add(createParameter("maxRetries", "最大重试次数", "number", false, 3, "消息处理失败最大重试次数"));
                break;
            case "redis":
                params.add(createParameter("host", "Redis主机", "string", true, "localhost", "Redis服务器地址"));
                params.add(createParameter("port", "端口", "number", true, 6379, "Redis端口"));
                params.add(createParameter("password", "密码", "password", false, null, "Redis密码"));
                params.add(createParameter("database", "数据库", "number", false, 0, "Redis数据库索引"));
                params.add(createParameter("streamKey", "Stream Key", "string", false, "sync:data:stream", "Redis Stream Key"));
                params.add(createParameter("consumerGroup", "消费者组", "string", false, "sync-consumers", "消费者组名称"));
                params.add(createParameter("consumerName", "消费者名称", "string", false, "consumer-1", "消费者实例名称"));
                params.add(createParameter("maxLen", "最大长度", "number", false, 100000, "Stream最大长度"));
                break;
            case "kafka":
                params.add(createParameter("bootstrapServers", "服务器地址", "string", true, "localhost:9092", "Kafka服务器地址(逗号分隔多个)"));
                params.add(createParameter("topic", "Topic", "string", true, null, "Kafka Topic名称"));
                params.add(createParameter("groupId", "消费组", "string", false, "sync-group", "消费组ID"));
                params.add(createParameterWithOptions("autoOffsetReset", "偏移重置", "select", false, "earliest", "无偏移时的行为", "从头:earliest,最新:latest"));
                params.add(createParameter("enableAutoCommit", "自动提交", "boolean", false, true, "是否自动提交偏移"));
                params.add(createParameter("maxPollRecords", "拉取数量", "number", false, 500, "每次拉取的最大记录数"));
                break;
            case "mq":
                params.add(createParameter("host", "MQ主机", "string", true, "localhost", "消息队列服务器地址"));
                params.add(createParameter("port", "端口", "number", true, 5672, "消息队列端口"));
                params.add(createParameter("username", "用户名", "string", false, "guest", "MQ用户名"));
                params.add(createParameter("password", "密码", "password", false, "guest", "MQ密码"));
                params.add(createParameter("virtualHost", "虚拟主机", "string", false, "/", "虚拟主机"));
                params.add(createParameter("queueName", "队列名称", "string", true, null, "队列名称"));
                params.add(createParameter("exchangeName", "交换机", "string", false, null, "交换机名称"));
                params.add(createParameter("routingKey", "路由键", "string", false, null, "消息路由键"));
                break;
            default:
                break;
        }

        return params;
    }

    /**
     * 获取 Filter 参数定义
     */
    private List<SpiParameter> getFilterParameters(String spiName) {
        List<SpiParameter> params = new ArrayList<>();

        switch (spiName.toLowerCase()) {
            case "fieldmapping":
                params.add(createParameter("renameMapping", "字段重命名", "keyvalue", false, null, "原字段名->新字段名的映射"));
                params.add(createParameter("selectFields", "选择字段", "string", false, null, "只保留指定字段(逗号分隔)"));
                params.add(createParameter("removeFields", "删除字段", "string", false, null, "删除指定字段(逗号分隔)"));
                params.add(createParameter("defaultValues", "默认值", "keyvalue", false, null, "字段默认值映射"));
                params.add(createParameter("keepUnmapped", "保留未映射", "boolean", false, true, "是否保留未在映射中的字段"));
                break;
            case "validation":
                params.add(createParameter("requiredFields", "必填字段", "string", false, null, "必填字段列表(逗号分隔)"));
                params.add(createParameter("rules", "验证规则", "json", false, null, "自定义验证规则(JSON格式)"));
                params.add(createParameter("discardOnFail", "失败时丢弃", "boolean", false, false, "验证失败时是否丢弃数据"));
                params.add(createParameter("throwOnFail", "失败时异常", "boolean", false, false, "验证失败时是否抛出异常"));
                break;
            case "aggregation":
                params.add(createParameter("groupByFields", "分组字段", "string", true, null, "分组字段(逗号分隔)"));
                params.add(createParameter("aggregations", "聚合配置", "json", true, null, "聚合配置(JSON格式)"));
                params.add(createParameter("windowSize", "窗口大小", "number", false, 100, "聚合窗口大小"));
                break;
            case "deduplication":
                params.add(createParameter("keyFields", "去重字段", "string", true, null, "用于判断重复的字段(逗号分隔)"));
                params.add(createParameter("windowSize", "窗口大小", "number", false, 1000, "去重窗口大小"));
                params.add(createParameterWithOptions("strategy", "去重策略", "select", false, "first", "遇到重复时的处理策略", "保留第一条:first,保留最后一条:last"));
                break;
            case "script":
                params.add(createParameterWithOptions("scriptType", "脚本类型", "select", true, "javascript", "脚本语言类型", "JavaScript:javascript,Groovy:groovy,Python:python"));
                params.add(createParameter("script", "脚本内容", "textarea", true, null, "过滤脚本内容"));
                break;
            case "expression":
                params.add(createParameter("condition", "过滤条件", "textarea", true, null, "过滤条件表达式(SpEL/MVEL)"));
                params.add(createParameterWithOptions("expressionType", "表达式类型", "select", false, "spel", "表达式语言", "SpEL:spel,MVEL:mvel"));
                break;
            default:
                break;
        }

        return params;
    }

    /**
     * 创建参数定义
     */
    private SpiParameter createParameter(String name, String label, String type,
                                         boolean required, Object defaultValue, String description) {
        SpiParameter param = new SpiParameter();
        param.setName(name);
        param.setLabel(label);
        param.setType(type);
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        param.setDescription(description);
        param.setSensitive("password".equals(type));
        return param;
    }

    /**
     * 创建带可选值的参数定义
     *
     * @param options 可选值字符串（格式: label:value,label:value）
     */
    private SpiParameter createParameterWithOptions(String name, String label, String type,
                                                    boolean required, Object defaultValue,
                                                    String description, String options) {
        SpiParameter param = createParameter(name, label, type, required, defaultValue, description);
        if (options != null && !options.isEmpty()) {
            List<Map<String, Object>> optionList = new ArrayList<>();
            String[] parts = options.split(",");
            for (String part : parts) {
                String[] kv = part.trim().split(":");
                Map<String, Object> option = new HashMap<>();
                if (kv.length == 2) {
                    option.put("label", kv[0].trim());
                    option.put("value", kv[1].trim());
                } else {
                    option.put("label", kv[0].trim());
                    option.put("value", kv[0].trim());
                }
                optionList.add(option);
            }
            param.setOptions(optionList);
        }
        return param;
    }

    /**
     * 获取显示名称
     */
    private String getDisplayName(String name, String type) {
        Map<String, String> displayNames = new HashMap<>();
        displayNames.put("jdbc", "JDBC数据库");
        displayNames.put("csv", "CSV文件");
        displayNames.put("tsv", "TSV文件");
        displayNames.put("json", "JSON文件");
        displayNames.put("xml", "XML文件");
        displayNames.put("http", "HTTP接口");
        displayNames.put("mock", "模拟数据");
        displayNames.put("console", "控制台");
        displayNames.put("local", "本地队列");
        displayNames.put("redis", "Redis Stream");
        displayNames.put("kafka", "Kafka");
        displayNames.put("mq", "RabbitMQ");
        displayNames.put("fieldmapping", "字段映射");
        displayNames.put("validation", "数据验证");
        displayNames.put("aggregation", "数据聚合");
        displayNames.put("deduplication", "数据去重");
        displayNames.put("script", "脚本过滤");
        displayNames.put("expression", "表达式过滤");
        return displayNames.getOrDefault(name.toLowerCase(), name);
    }

    /**
     * 获取描述
     */
    private String getDescription(String name, String type) {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("jdbc", "支持MySQL、PostgreSQL、Oracle等关系数据库");
        descriptions.put("csv", "读取逗号分隔值文件");
        descriptions.put("tsv", "读取制表符分隔值文件");
        descriptions.put("json", "读取JSON格式文件，支持JSONPath");
        descriptions.put("xml", "读取XML格式文件，支持XPath");
        descriptions.put("http", "从HTTP接口获取数据，支持GET/POST");
        descriptions.put("mock", "生成模拟测试数据");
        descriptions.put("console", "输出到控制台，用于调试");
        descriptions.put("local", "本地内存队列，适合单机场景");
        descriptions.put("redis", "Redis Stream分布式队列");
        descriptions.put("kafka", "Apache Kafka高吞吐消息队列");
        descriptions.put("mq", "RabbitMQ企业级消息队列");
        descriptions.put("fieldmapping", "字段重命名、选择、删除等操作");
        descriptions.put("validation", "数据格式、必填、范围等验证");
        descriptions.put("aggregation", "数据分组聚合计算");
        descriptions.put("deduplication", "根据字段进行数据去重");
        descriptions.put("script", "使用JavaScript/Groovy脚本过滤");
        descriptions.put("expression", "使用SpEL/MVEL表达式过滤");
        return descriptions.getOrDefault(name.toLowerCase(), "");
    }

    /**
     * 获取图标
     */
    private String getIcon(String name, String type) {
        Map<String, String> icons = new HashMap<>();
        icons.put("jdbc", "database");
        icons.put("csv", "file-text");
        icons.put("tsv", "file-text");
        icons.put("json", "braces");
        icons.put("xml", "code");
        icons.put("http", "globe");
        icons.put("mock", "cpu");
        icons.put("console", "terminal");
        icons.put("local", "inbox");
        icons.put("redis", "server");
        icons.put("kafka", "activity");
        icons.put("mq", "mail");
        icons.put("fieldmapping", "shuffle");
        icons.put("validation", "check-circle");
        icons.put("aggregation", "layers");
        icons.put("deduplication", "copy");
        icons.put("script", "code");
        icons.put("expression", "filter");
        return icons.getOrDefault(name.toLowerCase(), "box");
    }

    /**
     * 获取颜色
     */
    private String getColor(String name, String type) {
        switch (type.toUpperCase()) {
            case TYPE_INPUT:
                return "#52c41a";
            case TYPE_OUTPUT:
                return "#1890ff";
            case TYPE_DATA_CENTER:
                return "#722ed1";
            case TYPE_FILTER:
                return "#faad14";
            default:
                return "#666666";
        }
    }

    /**
     * 测试 Input 连接
     */
    private ReturnResult<String> testInputConnection(String spiName, Map<String, Object> config) {
        try {
            if (adapterManager != null && adapterManager.hasAdapter(TYPE_INPUT, spiName)) {
                String result = adapterManager.test(TYPE_INPUT, spiName, config);
                if (result != null && result.startsWith("连接成功")) {
                    return ReturnResult.ok(result);
                } else if (result != null) {
                    return ReturnResult.error(result);
                }
            }

            if ("jdbc".equalsIgnoreCase(spiName)) {
                String result = SyncDataSourceFactory.testConnection(config);
                if (result.startsWith("连接成功")) {
                    return ReturnResult.ok(result);
                } else {
                    return ReturnResult.error(result);
                }
            }

            Object input = createSpiInstance(INPUT_CLASS, spiName, config);
            if (input != null) {
                return ReturnResult.ok("连接测试成功");
            }
            return ReturnResult.error("无法创建 Input 实例");
        } catch (Exception e) {
            return ReturnResult.error("连接测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 Output 连接
     */
    private ReturnResult<String> testOutputConnection(String spiName, Map<String, Object> config) {
        try {
            if (adapterManager != null && adapterManager.hasAdapter(TYPE_OUTPUT, spiName)) {
                String result = adapterManager.test(TYPE_OUTPUT, spiName, config);
                if (result != null && result.startsWith("连接成功")) {
                    return ReturnResult.ok(result);
                } else if (result != null) {
                    return ReturnResult.error(result);
                }
            }

            if ("jdbc".equalsIgnoreCase(spiName)) {
                String result = SyncDataSourceFactory.testConnection(config);
                if (result.startsWith("连接成功")) {
                    return ReturnResult.ok(result);
                } else {
                    return ReturnResult.error(result);
                }
            }

            Object output = createSpiInstance(OUTPUT_CLASS, spiName, config);
            if (output != null) {
                return ReturnResult.ok("连接测试成功");
            }
            return ReturnResult.error("无法创建 Output 实例");
        } catch (Exception e) {
            return ReturnResult.error("连接测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 DataBus 连接
     */
    private ReturnResult<String> testDataCenterConnection(String spiName, Map<String, Object> config) {
        try {
            if (adapterManager != null && adapterManager.hasAdapter(TYPE_DATA_CENTER, spiName)) {
                String result = adapterManager.test(TYPE_DATA_CENTER, spiName, config);
                if (result != null && result.startsWith("连接成功")) {
                    return ReturnResult.ok(result);
                } else if (result != null) {
                    return ReturnResult.error(result);
                }
            }

            Object sink = createSpiInstance(DATA_CENTER_CLASS, spiName, config);
            if (sink != null) {
                return ReturnResult.ok("连接测试成功");
            }
            return ReturnResult.error("无法创建 Sink 实例");
        } catch (Exception e) {
            return ReturnResult.error("连接测试失败: " + e.getMessage());
        }
    }

    private Class<?> resolveSpiClass(String className) {
        try {
            return Class.forName(className);
        } catch (Exception e) {
            return null;
        }
    }

    private List<SpiParameter> resolveSpiParameters(String className, String name) {
        Class<?> spiClass = resolveSpiClass(className);
        if (spiClass == null) {
            return Collections.emptyList();
        }
        return SpiParameterResolver.resolveParameters(spiClass, name);
    }

    private Object createSpiInstance(String className, String spiName, Map<String, Object> config) {
        Class<?> spiClass = resolveSpiClass(className);
        if (spiClass == null) {
            return null;
        }
        ServiceProvider<?> provider = ServiceProvider.of(spiClass);
        return provider.getNewExtension(spiName, config);
    }

    private List<SpiInfo> buildDefaultSpiInfos(String type) {
        List<String> names;
        if (TYPE_INPUT.equalsIgnoreCase(type)) {
            names = Arrays.asList("jdbc", "csv", "tsv", "json", "xml", "http", "mock");
        } else if (TYPE_OUTPUT.equalsIgnoreCase(type)) {
            names = Arrays.asList("jdbc", "csv", "tsv", "json", "xml", "http", "console");
        } else if (TYPE_DATA_CENTER.equalsIgnoreCase(type)) {
            names = Arrays.asList("local", "redis", "kafka", "mq");
        } else if (TYPE_FILTER.equalsIgnoreCase(type)) {
            names = Arrays.asList("fieldmapping", "validation", "aggregation", "deduplication", "script", "expression");
        } else {
            names = Collections.emptyList();
        }
        if (names.isEmpty()) {
            return Collections.emptyList();
        }
        List<SpiInfo> infos = new ArrayList<>();
        int order = 0;
        for (String name : names) {
            SpiInfo info = new SpiInfo();
            info.setName(name);
            info.setType(type);
            info.setOrder(order++);
            info.setAvailable(true);
            enrichInfo(info, name, type);
            infos.add(info);
        }
        return infos;
    }
}
