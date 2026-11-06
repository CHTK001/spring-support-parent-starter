package com.chua.starter.collect.engine.spi;

import com.chua.starter.collect.engine.CollectEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点元数据构造工具。
 *
 * <p>把每个可执行节点都要暴露的循环四参数集中一处，新增节点类型不需要重复抄写，
 * 设计器的属性面板因此对所有节点保持一致的循环配置区。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectShapeMetas {

    private CollectShapeMetas() {
    }

    /**
     * 构造节点元数据骨架，已带上循环参数。
     *
     * @param shape       节点类型
     * @param label       显示名称
     * @param group       分组：来源/解析/控制/输出
     * @param icon        图标标识
     * @param description 功能说明
     * @return 节点元数据
     */
    public static CollectShapeMeta of(String shape, String label, String group, String icon, String description) {
        CollectShapeMeta meta = new CollectShapeMeta();
        meta.setShape(shape);
        meta.setLabel(label);
        meta.setGroup(group);
        meta.setIcon(icon);
        meta.setDescription(description);
        meta.setParams(loopParams());
        addTimeoutParams(meta);
        return meta;
    }

    /**
     * 给所有节点补统一的节点级超时参数。
     *
     * <p>执行器已有同名 {@code timeout} 时不重复添加，由 CollectEngine 统一读取该参数做超时控制；
     * 策略只影响超时后的行为：自动延长一次等待时间，或直接结束该分支。</p>
     */
    private static void addTimeoutParams(CollectShapeMeta meta) {
        if (meta.getParams().stream().anyMatch(param -> "timeout".equals(param.getName()))) {
            return;
        }
        CollectParamMeta timeout = text("timeout", "超时毫秒", "number",
                "0/留空表示不限制；超过后按「超时策略」自动延长一次或结束该分支");
        timeout.setDefaultValue("0");
        meta.getParams().add(timeout);
        meta.getParams().add(option("timeoutPolicy", "超时策略", "end",
                "超时后自动延长一次相同时长继续等待，或直接结束该分支", "extend", "end"));
    }

    /**
     * 所有可执行节点共用的循环参数。
     *
     * @return 循环参数列表
     */
    public static List<CollectParamMeta> loopParams() {
        List<CollectParamMeta> params = new ArrayList<>();
        params.add(text(CollectEngine.PARAM_LOOP_COUNT, "逐条处理的集合", "textarea",
                "填次数如 10，或填集合表达式如 ${url}、${data}；留空只执行一次。填集合时每一项就是一轮的 ${item}"));
        CollectParamMeta index = text(CollectEngine.PARAM_LOOP_INDEX, "轮次变量名", "text",
                "当前是第几轮（从 0 起），写入的变量名，留空用 index");
        index.setDefaultValue("index");
        params.add(index);
        CollectParamMeta item = text(CollectEngine.PARAM_LOOP_ITEM_VARIABLE, "当前条变量名", "text",
                "本轮处理的那条数据写入的变量名，下游用 ${item} 引用");
        item.setDefaultValue("item");
        params.add(item);
        CollectParamMeta parallel = option(CollectEngine.PARAM_LOOP_PARALLEL, "并行逐条", "0",
                "选 1 时各条同时入池，由流程线程数限流；下游按条各自展开", "0", "1");
        params.add(parallel);
        return params;
    }

    /**
     * 构造带默认值与提示的下拉参数。
     *
     * @param name         参数名
     * @param label        显示标题
     * @param defaultValue 默认值
     * @param tip          填写提示
     * @param options      候选项
     * @return 参数元数据
     */
    public static CollectParamMeta option(String name, String label, String defaultValue, String tip,
                                          String... options) {
        CollectParamMeta meta = CollectParamMeta.select(name, label, options);
        meta.setDefaultValue(defaultValue);
        meta.setTip(tip);
        return meta;
    }

    /**
     * 构造文本类参数并登记默认值。
     *
     * @param name         参数名
     * @param label        显示标题
     * @param type         控件类型
     * @param tip          填写提示
     * @return 参数元数据
     */
    public static CollectParamMeta text(String name, String label, String type, String tip) {
        CollectParamMeta meta = CollectParamMeta.of(name, label, type);
        meta.setTip(tip);
        return meta;
    }

    /**
     * 构造必填文本参数。
     *
     * @param name  参数名
     * @param label 显示标题
     * @param type  控件类型
     * @param tip   填写提示
     * @return 参数元数据
     */
    public static CollectParamMeta required(String name, String label, String type, String tip) {
        CollectParamMeta meta = text(name, label, type, tip);
        meta.setRequired(true);
        return meta;
    }

    /**
     * 构造「输出名」参数：必填、默认 {@code data}。
     *
     * <p>所有产出型节点共用。引擎在节点执行后会把该变量别名成统一的
     * {@code ${data}}（即上一个节点的产出），并派生 {@code dataJson}、
     * 登记跨节点留存 —— 所以输出名无论填什么，{@code ${data}} 恒可用。
     * 默认 {@code data} 让新节点开箱即落在统一变量模型上，用户也可自定义
     * 成别的名字（存量流程留空时引擎按执行器默认名兜底，引用不破坏）。</p>
     *
     * <p>注意：请求节点不用本方法 —— 它会派生 {@code 输出名+Html/Doc/Json} 同伴
     * 变量，若输出名填 {@code data} 会和引擎派生的 {@code dataJson} 撞名，
     * 故请求节点输出名仍默认 {@code resp}。</p>
     *
     * @param tip 填写提示
     * @return 参数元数据
     */
    public static CollectParamMeta outputVariable(String tip) {
        CollectParamMeta meta = CollectParamMeta.of("outputVariable", "输出名", "text");
        meta.setTip(tip);
        meta.setDefaultValue("data");
        meta.setRequired(true);
        return meta;
    }

    /**
     * 构造数据源下拉。
     *
     * <p>候选项不在这里写死，由前端从数据源选项接口读，首项固定是应用主库 {@code app}，
     * 因此新增数据源不需要改任何节点元数据。</p>
     *
     * @param name         参数名
     * @param label        显示标题
     * @param defaultValue 默认值
     * @param tip          填写提示
     * @return 参数元数据
     */
    public static CollectParamMeta datasource(String name, String label, String defaultValue, String tip) {
        CollectParamMeta meta = text(name, label, "datasource", tip);
        meta.setDefaultValue(defaultValue);
        return meta;
    }

    /**
     * 构造数据表下拉，候选项随数据源级联刷新。
     *
     * @param name         参数名
     * @param label        显示标题
     * @param defaultValue 默认值
     * @param tip          填写提示
     * @return 参数元数据
     */
    public static CollectParamMeta table(String name, String label, String defaultValue, String tip) {
        CollectParamMeta meta = text(name, label, "table", tip);
        meta.setDefaultValue(defaultValue);
        meta.setDependsOn("datasource");
        return meta;
    }

    /**
     * 构造字段映射表控件。
     *
     * <p>存储格式仍是 {@code cell:列名} 名值对，引擎侧读取逻辑不变；只是把成对的行编辑
     * 换成「目标列下拉 + 表达式输入」的表格，左列候选直接来自目标表的真实列元数据。</p>
     *
     * @param name        参数名
     * @param label       显示标题
     * @param tableParam  取列来源的表参数名
     * @param tip         填写提示
     * @return 参数元数据
     */
    public static CollectParamMeta mapping(String name, String label, String tableParam, String tip) {
        CollectParamMeta meta = text(name, label, "mapping", tip);
        meta.setColumnsFrom(tableParam);
        // 入库节点没有字段映射就写不出任何东西，执行器也会直接报错，这里前置成必填
        meta.setRequired(true);
        return meta;
    }

    /**
     * 追加参数并返回元数据，便于链式声明。
     *
     * @param meta   节点元数据
     * @param params 待追加参数
     * @return 入参元数据
     */
    public static CollectShapeMeta with(CollectShapeMeta meta, CollectParamMeta... params) {
        for (CollectParamMeta param : params) {
            if (param == null) {
                continue;
            }
            boolean exists = meta.getParams().stream().anyMatch(p -> param.getName().equals(p.getName()));
            if (!exists) {
                meta.getParams().add(param);
            }
        }
        return meta;
    }
}
