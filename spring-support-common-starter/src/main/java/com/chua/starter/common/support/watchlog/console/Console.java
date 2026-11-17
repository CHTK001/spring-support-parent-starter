package com.chua.starter.common.support.watchlog.console;

/**
 * 控制台日志整合对象（SPI）。
 *
 * <p>只做一件事：按调用层级把一行行日志整合起来。{@link #section(String)} 追加一行，
 * 并返回下一级对象；同一级继续写就继续用当前对象调用 {@code section}：</p>
 *
 * <pre>{@code
 * Console console = Consoles.create();
 * console.section("① 结论 有 1 条任务审核回退，后续 3 条重置");
 * console.section("② 分组 A=[A] → B=[B01,B2]");
 *
 * Console detail = console.section("④ 逐条");
 * detail.section("A     审核回退(3)     jd=0 shr=1287885749576826884");
 * detail.section("B01   未开始(0)");
 *
 * log.info("[RWB链路]\n{}", console);
 * }</pre>
 *
 * <p>输出：</p>
 *
 * <pre>{@code
 * ① 结论 有 1 条任务审核回退，后续 3 条重置
 * ② 分组 A=[A] → B=[B01,B2]
 * ④ 逐条
 *   A     审核回退(3)     jd=0 shr=1287885749576826884
 *   B01   未开始(0)
 * }</pre>
 *
 * <p>{@code console.section("A").section("B")} 中 {@code B} 是 {@code A} 的下一级；
 * 需要同级时继续用父对象调用 {@code section}。所有节点共享同一份日志，
 * {@link #toString()} 与 {@link #print()} 输出整份日志。实现不要求线程安全。</p>
 *
 * <p>本 SPI 只做通用日志整合，不承载具体业务语义：等级、颜色、耗时、MDC 头部、
 * 缩进连接符与输出编码均由 {@link ConsoleConfig} 决定。</p>
 *
 * @author CH
 * @since 4.0.0
 * @see DefaultConsole
 * @see Consoles
 * @see ConsoleConfig
 * @see ConsoleLevel
 */
public interface Console {

    /**
     * 追加一行日志，并返回下一级对象。
     *
     * <p>用返回的对象继续调用 {@code section}，新行就是当前行的下一级；
     * 用当前对象继续调用 {@code section}，新行与当前行同级。</p>
     *
     * <p>本方法不改变等级：新行沿用当前节点的等级（根节点为
     * {@link ConsoleLevel#INFO}）。需要指定等级时用 {@link #info}、{@link #warn}、
     * {@link #error}、{@link #debug}。</p>
     *
     * @param line 日志行
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console section(String line);

    /**
     * 追加一行日志，支持 {@code {}} 占位符。
     *
     * <p>占位符语义与 {@code StringUtils.format} 一致：</p>
     *
     * <pre>{@code
     * console.section("分组 {} 共 {} 条", "A", 3);
     * }</pre>
     *
     * @param line 日志行模板
     * @param args 占位符参数
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console section(String line, Object... args);

    /**
     * 追加一行 INFO 级别日志，并返回下一级对象。
     *
     * @param line 日志行
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console info(String line);

    /**
     * 追加一行 INFO 级别日志，支持 {@code {}} 占位符。
     *
     * @param line 日志行模板
     * @param args 占位符参数
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console info(String line, Object... args);

    /**
     * 追加一行 WARN 级别日志，并返回下一级对象。
     *
     * @param line 日志行
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console warn(String line);

    /**
     * 追加一行 WARN 级别日志，支持 {@code {}} 占位符。
     *
     * @param line 日志行模板
     * @param args 占位符参数
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console warn(String line, Object... args);

    /**
     * 追加一行 ERROR 级别日志，并返回下一级对象。
     *
     * @param line 日志行
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console error(String line);

    /**
     * 追加一行 ERROR 级别日志，支持 {@code {}} 占位符。
     *
     * @param line 日志行模板
     * @param args 占位符参数
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console error(String line, Object... args);

    /**
     * 追加一行 DEBUG 级别日志，并返回下一级对象。
     *
     * @param line 日志行
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console debug(String line);

    /**
     * 追加一行 DEBUG 级别日志，支持 {@code {}} 占位符。
     *
     * @param line 日志行模板
     * @param args 占位符参数
     * @return 下一级对象，与当前对象共享同一份日志
     */
    Console debug(String line, Object... args);

    /**
     * 退回上一层。
     *
     * <p>用于「当前层级已经写完，接着回到父级继续写」的场景：</p>
     *
     * <pre>{@code
     * Console root = Consoles.create();
     * Console child = root.section("父级");
     * child.section("子级");
     * child.end().section("父级的下一行");   // 与「父级」同级
     * }</pre>
     *
     * @return 上一层对象；已在根层级时返回自身
     */
    Console end();

    /**
     * 获取整份日志。
     *
     * @return 多行文本，每行以换行符结尾
     */
    @Override
    String toString();

    /**
     * 输出整份日志到标准输出。
     */
    void print();
}
