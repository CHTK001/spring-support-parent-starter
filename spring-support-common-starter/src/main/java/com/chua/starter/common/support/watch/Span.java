package com.chua.starter.common.support.watch;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
/**
 * @author Administrator
 */
@Getter
@Setter
public class Span implements Serializable {
    /**
     * 分布式追踪中的链路ID
     */
    private String linkId;
    /**
     * Span的唯一标识ID
     */
    private String id;
    /**
     * 父Span的ID
     */
    private String pid;
    /**
     * 进入Span的时间（纳秒级）
     */
    private long enterTime;
    /**
     * Span结束的时间（纳秒级）
     */
    private long endTime;
    /**
     * Span的耗时
     */
    private long costTime;
    /**
     * 消息描述
     */
    private String message;
    /**
     * Stack信息列表
     */
    private List<String> stack;
    /**
     * Header信息列表
     */
    private List<String> header;
    /**
     * 调用的方法名
     */
    private String method;
    /**
     * 方法的类型
     */
    private String typeMethod;
    /**
     * Span的类型
     */
    private String type;

    /**
     * 方法参数
     */
    private Object[] args;
    /**
     * 是否是标题Span
     */
    private boolean title;
    /**
     * 异常信息
     */
    private String ex;
    /**
     * 错误信息
     */
    private String error;
    /**
     * 数据库信息
     */
    private String db;
    /**
     * 模型信息
     */
    private String model;
    /**
     * 线程名称
     */
    private String threadName;
    /**
     * 来源信息
     */
    private String from;

    /**
     * 子Span列表
     */
    private List<Span> children;
    /**
     * 父Span的类名集合，用于追踪调用链路
     */
    private transient volatile Set<String> parents = new LinkedHashSet<>();

    /**
     * 默认构造方法，初始化进入时间和线程名称
     */
    public Span() {
        this.enterTime = System.nanoTime();
        this.threadName = Thread.currentThread().getName();
    }

    /**
     * 带链路ID的构造方法
     * @param linkId 分布式追踪中的链路ID
     * 初始化链路ID、进入时间、堆栈信息和线程名称
     */
    public Span(String linkId) {
        this.linkId = linkId;
        this.enterTime = System.nanoTime();
        setStack(Thread.currentThread().getStackTrace());
        this.threadName = Thread.currentThread().getName();
    }

    /**
     * 设置堆栈信息
     * @param stackTrace 堆栈追踪信息数组
     * 过滤掉不需要的堆栈信息，保存关键的类名和方法信息
     */
    public void setStack(StackTraceElement[] stackTrace) {
        List<String> rs = new LinkedList<>();
        for (int i = 0, stackTraceLength = stackTrace.length; i < stackTraceLength; i++) {
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            // 跳过特定的类名
            if (className.startsWith("com.chua.agent.support")) {
                continue;
            }

            String string = element.toString();
            rs.add(string);
            // 特殊条件判断，决定是否添加到parents集合
            if (i < 1 || string.startsWith("sun") || string.contains("$") || string.contains("<init>") || string.startsWith("java")) {
                continue;
            }

            int i1 = string.indexOf("(");
            parents.add(i1 > -1 ? string.substring(0, i1) : string);
        }
        this.stack = rs;
    }
}
