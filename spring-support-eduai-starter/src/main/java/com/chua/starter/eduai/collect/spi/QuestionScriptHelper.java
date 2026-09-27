package com.chua.starter.eduai.collect.spi;

import java.util.List;

/**
 * Groovy 采集脚本的工具对象，以变量 {@code collector} 绑定进脚本。
 * <p>
 * 脚本可直接 {@code return} 题目列表，也可通过 {@code collector.choice(...)} 等方法逐题登记后
 * 返回 {@code null}（此时取 {@link #getQuestions()} 累积结果）。脚本内禁止直接 new 业务对象，
 * 统一经本接口产出，保证字段口径与其他采集源一致。
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface QuestionScriptHelper {

    /**
     * 采集上下文（任务配置快照）。
     *
     * @return 上下文
     */
    QuestionSourceContext getContext();

    /**
     * 累积的题目列表。
     *
     * @return 可变列表
     */
    List<CollectedQuestion> getQuestions();

    /**
     * 读取来源配置里的字符串字段。
     *
     * @param key 字段名
     * @return 值（缺失时 null）
     */
    String config(String key);

    /**
     * 读取来源配置里的字符串字段。
     *
     * @param key 字段名
     * @param defaultValue 缺省值
     * @return 值
     */
    String config(String key, String defaultValue);

    /**
     * 读取来源配置里的整数字段。
     *
     * @param key 字段名
     * @param defaultValue 缺省值（非数字或缺失时返回）
     * @return 值
     */
    int configInt(String key, int defaultValue);

    /**
     * HTTP GET 抓取文本（http/https，超时与体积上限由框架控制）。
     *
     * @param url 地址
     * @return 响应体
     */
    String http(String url);

    /**
     * HTTP GET 抓取二进制内容（归档类数据源，如打包题库；超时与体积上限同样由框架控制）。
     *
     * @param url 地址
     * @return 响应体字节
     */
    byte[] httpBytes(String url);

    /**
     * 正则抓取全部匹配组（每组第一个捕获）。
     *
     * @param content 原文
     * @param regex 正则（含一个捕获组）
     * @return 捕获内容列表
     */
    List<String> extract(String content, String regex);

    /**
     * 正则抓取全部匹配的捕获组（每组一个元素，无捕获组时整段命中为唯一元素）。
     *
     * @param content 原文
     * @param regex 正则
     * @return 每次匹配的捕获组列表
     */
    List<List<String>> extractGroups(String content, String regex);

    /**
     * 把题目 JSON 数组（或 {data:[...]} 包装）解析为题目。
     *
     * @param json JSON 文本
     * @return 题目列表（无法解析时抛异常）
     */
    List<CollectedQuestion> parseQuestions(String json);

    /**
     * 登记一道题（原样入库，字段由脚本自行 set）。
     *
     * @param question 题目
     */
    void add(CollectedQuestion question);

    /**
     * 构造解答题。
     *
     * @param stem 题干
     * @param answer 答案
     * @return 题目
     */
    CollectedQuestion question(String stem, String answer);

    /**
     * 构造填空题。
     *
     * @param stem 题干（空格用______表示）
     * @param answer 答案
     * @param analysis 解析（可空）
     * @return 题目
     */
    CollectedQuestion fill(String stem, String answer, String analysis);

    /**
     * 构造判断题（答案存 正确/错误）。
     *
     * @param stem 题干
     * @param expectTrue 期望答案
     * @param analysis 解析（可空）
     * @return 题目
     */
    CollectedQuestion judge(String stem, boolean expectTrue, String analysis);

    /**
     * 构造单选题（选项形如 {@code ["A. 甲", "B. 乙"]}，答案取选项序号字母）。
     *
     * @param stem 题干
     * @param options 选项文本列表
     * @param answerIndex 正确选项下标（从 0 开始）
     * @param analysis 解析（可空）
     * @return 题目
     */
    CollectedQuestion choice(String stem, List<String> options, int answerIndex, String analysis);

    /**
     * 输出脚本日志（进采集服务日志，便于排查脚本内部流程）。
     *
     * @param message 内容
     */
    void log(String message);
}
