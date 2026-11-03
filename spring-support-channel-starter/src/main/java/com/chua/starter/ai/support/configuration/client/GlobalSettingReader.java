package com.chua.starter.ai.support.configuration.client;

import java.math.BigDecimal;

/**
 * 全局设置读取契约 —— 统一封装 {@code sys_ai_global_setting} 键值表的类型安全读取。
 *
 * <p>各配置消费者（GlobalPanel、RAG、ChatClient、用量任务）统一通过本接口读取
 * 指定分组的键值配置，避免重复手写字符串解析。</p>
 *
 * <p><b>取值链（自上而下，命中即止）：</b></p>
 * <ol>
 *   <li>YAML 覆盖层 {@code plugin.ai.settings.<group>.<field>}（部署级钦定，重启生效）——
 *       见 {@link AiSettingsYamlProperties}，运维换 layout/OCR 管线走这里；</li>
 *   <li>用户覆盖 {@code sys_ai_user_setting}（设置页按用户保存）；</li>
 *   <li>全局数据库 {@code sys_ai_global_setting}（设置页保存，热更新）；</li>
 *   <li>消费方代码默认值（getBool/getInt 等的 defaultValue 参数）。</li>
 * </ol>
 *
 * <p>业务模块（eduai 等）只依赖本接口；实现由 {@code DefaultGlobalSettingReader} 提供。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface GlobalSettingReader {

    /**
     * 读取字符串值。
     *
     * <p><b>分层语义：YAML 覆盖 &gt; 当前用户的覆盖 &gt; 全局默认。</b>
     * 后端把 runtime / rag / face / ocr / image / image_search / generate / layout /
     * input / memory / bot / asr 等分组声明为「每个用户管自己的默认」
     * （见 {@code SettingsController.USER_WRITABLE_GROUPS}），而各业务消费端
     * （FaceClientManager / ImageRecognitionClientManager / LayoutClientManager /
     * RagClientManager / 记忆抽取 / 输入优化 …）统一走本方法取值，
     * 所以用户层必须在这里生效，否则用户在设置页改的模型「保存成功却不生效」。</p>
     *
     * <p>没有用户覆盖时行为与以前完全一致（只读全局层）。</p>
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    String getStr(String group, String field);

    /**
     * 按**指定用户**读取字符串值（不依赖调用线程的登录上下文）。
     *
     * <p>用于「重建某个账号的实例」这类场景：重建线程上往往没有该账号的上下文
     * （例如由用户 A 触发、却要重建**所有账号**的管线）。此时必须显式传入账号，
     * 否则会把所有账号都按触发者的配置建 —— 那比「改了不生效」更糟。</p>
     *
     * <p>语义与 {@link #getStr(String, String)} 一致：YAML 覆盖最高，其次用户覆盖，
     * 且**有覆盖行就以它为准**（哪怕值为空，即「显式清空」不回退全局）。</p>
     *
     * @param userId 用户标识；为空表示只读全局层
     * @param group  分组
     * @param field  字段名
     * @return 生效值，未配置时返回 空
     */
    String getStrAs(String userId, String group, String field);

    /**
     * 读取字符串值（<b>不经缓存</b>，直连数据库）。
     *
     * <p>为什么需要它：{@link #getStr(String, String)} 走的是
     * {@code @Cacheable(REDIS_CACHE_ALWAYS)} 的 Redis 缓存，只有页面的保存路径会主动失效。
     * 绕过页面直接改库（运维改库、脚本改库）时会读到旧值 —— 线上实测过：默认模型在库里已是
     * 新值，缓存里仍是已废弃的旧模型名，进程就一直按旧值解析，报出来的错误与库里配置完全对不上，
     * 极难排查。</p>
     *
     * <p>因此「错一次就整条链路失败」的模型解析必须走本方法。
     * YAML 覆盖层（{@code plugin.ai.settings.*}）不受缓存语义影响，仍在此方法链首命中 ——
     * 部署钦定的值不存在「读到旧值」的问题。</p>
     *
     * @param group 分组
     * @param field 字段名
     * @return 值，未配置时返回 空
     */
    String getStrDirect(String group, String field);

    /**
     * 读取布尔值。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 未配置或解析失败时的默认值
     * @return 布尔值
     */
    boolean getBool(String group, String field, boolean defaultValue);

    /**
     * 读取整数值。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 未配置或解析失败时的默认值
     * @return 整数值
     */
    int getInt(String group, String field, int defaultValue);

    /**
     * 读取可空整数值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 整数值，未配置或解析失败时返回 空
     */
    Integer getIntOrNull(String group, String field);

    /**
     * 读取可空长整数值。
     *
     * @param group 分组
     * @param field 字段名
     * @return 长整数值，未配置或解析失败时返回 空
     */
    Long getLongOrNull(String group, String field);

    /**
     * 读取可空小数。
     *
     * @param group 分组
     * @param field 字段名
     * @return 小数值，未配置或解析失败时返回 空
     */
    BigDecimal getDecimalOrNull(String group, String field);

    /**
     * 读取小数值，解析失败时返回默认值。
     *
     * @param group        分组
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 小数值
     */
    double getDouble(String group, String field, double defaultValue);
}
