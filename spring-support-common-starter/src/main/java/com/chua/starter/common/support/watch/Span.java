package com.chua.starter.common.support.watch;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
* @author Administrator
* @author CH
* @since 4.0.0
 */
public class Span implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid
    /**
    * 分布式追踪中的链路标识
    */
    private String linkId;
    /**
    * Span的唯一标识标识
    */
    private String id;
    /**
    * 父Span的标识
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
    * 头部信息列表
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
    * 带链路标识的构造方法
    * @param linkId 分布式追踪中的链路标识
    * 初始化链路标识、进入时间、堆栈信息和线程名称
    */
    public Span(String linkId) {
        this.linkId = linkId;
        this.enterTime = System.nanoTime();
        /**
        * 设置stack。
        * @param Thread.currentThread( Thread.当前thread(
        */
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
            // 跳过特定的类型
            if (className.startsWith("com.chua.agent.support")) {
                continue;
            }

            String string = element.toString();
            rs.add(string);
 // 特殊条件判断，决定是否添加到父集合
            if (i < 1 || string.startsWith("sun") || string.contains("$") || string.contains("<init>") || string.startsWith("java")) {
                continue;
            }

            int i1 = string.indexOf("(");
            parents.add(i1 > -1 ? string.substring(0, i1) : string);
        }
        this.stack = rs;
    }

    /**
    * 获取 链接标识
    *
    * @return linkId
    */
    public String getLinkId() {
        return linkId;
    }

    /**
    * 设置 链接标识
    *
    * @param linkId 链接标识
    */
    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    /**
    * 获取 标识
    *
    * @return id
    */
    public String getId() {
        return id;
    }

    /**
    * 设置 标识
    *
    * @param id 标识
    */
    public void setId(String id) {
        this.id = id;
    }

    /**
    * 获取 pid
    *
    * @return pid
    */
    public String getPid() {
        return pid;
    }

    /**
    * 设置 pid
    *
    * @param pid pid
    */
    public void setPid(String pid) {
        this.pid = pid;
    }

    /**
    * 获取 enter时间
    *
    * @return enterTime
    */
    public long getEnterTime() {
        return enterTime;
    }

    /**
    * 设置 enter时间
    *
    * @param enterTime enter时间
    */
    public void setEnterTime(long enterTime) {
        this.enterTime = enterTime;
    }

    /**
    * 获取 结束时间
    *
    * @return endTime
    */
    public long getEndTime() {
        return endTime;
    }

    /**
    * 设置 结束时间
    *
    * @param endTime 结束时间
    */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    /**
    * 获取 cost时间
    *
    * @return costTime
    */
    public long getCostTime() {
        return costTime;
    }

    /**
    * 设置 cost时间
    *
    * @param costTime cost时间
    */
    public void setCostTime(long costTime) {
        this.costTime = costTime;
    }

    /**
    * 获取 消息
    *
    * @return message
    */
    public String getMessage() {
        return message;
    }

    /**
    * 设置 消息
    *
    * @param message 消息
    */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
    * 获取 stack
    *
    * @return stack
    */
    public List<String> getStack() {
        return stack;
    }

    /**
    * 获取 头部
    *
    * @return header
    */
    public List<String> getHeader() {
        return header;
    }

    /**
    * 设置 头部
    *
    * @param header 头部
    */
    public void setHeader(List<String> header) {
        this.header = header;
    }

    /**
    * 获取 方法
    *
    * @return method
    */
    public String getMethod() {
        return method;
    }

    /**
    * 设置 方法
    *
    * @param method 方法
    */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
    * 获取 类型方法
    *
    * @return typeMethod
    */
    public String getTypeMethod() {
        return typeMethod;
    }

    /**
    * 设置 类型方法
    *
    * @param typeMethod 类型方法
    */
    public void setTypeMethod(String typeMethod) {
        this.typeMethod = typeMethod;
    }

    /**
    * 获取 类型
    *
    * @return type
    */
    public String getType() {
        return type;
    }

    /**
    * 设置 类型
    *
    * @param type 类型
    */
    public void setType(String type) {
        this.type = type;
    }

    /**
    * 获取 参数
    *
    * @return args
    */
    public Object[] getArgs() {
        return args;
    }

    /**
    * 设置 参数
    *
    * @param args 参数
    */
    public void setArgs(Object[] args) {
        this.args = args;
    }

    /**
    * 获取 title
    *
    * @return title
    */
    public boolean getTitle() {
        return title;
    }

    /**
    * 设置 title
    *
    * @param title title
    */
    public void setTitle(boolean title) {
        this.title = title;
    }

    /**
    * 获取 ex
    *
    * @return ex
    */
    public String getEx() {
        return ex;
    }

    /**
    * 设置 ex
    *
    * @param ex ex
    */
    public void setEx(String ex) {
        this.ex = ex;
    }

    /**
    * 获取 错误
    *
    * @return error
    */
    public String getError() {
        return error;
    }

    /**
    * 设置 错误
    *
    * @param error 错误
    */
    public void setError(String error) {
        this.error = error;
    }

    /**
    * 获取 db
    *
    * @return db
    */
    public String getDb() {
        return db;
    }

    /**
    * 设置 db
    *
    * @param db db
    */
    public void setDb(String db) {
        this.db = db;
    }

    /**
    * 获取 模型
    *
    * @return model
    */
    public String getModel() {
        return model;
    }

    /**
    * 设置 模型
    *
    * @param model 模型
    */
    public void setModel(String model) {
        this.model = model;
    }

    /**
    * 获取 thread名称
    *
    * @return threadName
    */
    public String getThreadName() {
        return threadName;
    }

    /**
    * 设置 thread名称
    *
    * @param threadName thread名称
    */
    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    /**
    * 获取 从
    *
    * @return from
    */
    public String getFrom() {
        return from;
    }

    /**
    * 设置 从
    *
    * @param from 从
    */
    public void setFrom(String from) {
        this.from = from;
    }

    /**
    * 获取 children
    *
    * @return children
    */
    public List<Span> getChildren() {
        return children;
    }

    /**
    * 设置 children
    *
    * @param children children
    */
    public void setChildren(List<Span> children) {
        this.children = children;
    }

    /**
    * 获取 父
    *
    * @return parents
    */
    public Set<String> getParents() {
        return parents;
    }

    /**
    * 设置 父
    *
    * @param parents 父
    */
    public void setParents(Set<String> parents) {
        this.parents = parents;
    }

}

