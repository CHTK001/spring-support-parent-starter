package com.chua.starter.common.support.watch;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
/**
 * @author Administrator
 */
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
            // 跳过特定的类型
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
    /**
     * 获取 linkId
     *
     * @return linkId
     */
    public String getLinkId() {
        return linkId;
    }

    /**
     * 设置 linkId
     *
     * @param linkId linkId
     */
    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    /**
     * 获取 id
     *
     * @return id
     */
    public String getId() {
        return id;
    }

    /**
     * 设置 id
     *
     * @param id id
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
     * 获取 enterTime
     *
     * @return enterTime
     */
    public long getEnterTime() {
        return enterTime;
    }

    /**
     * 设置 enterTime
     *
     * @param enterTime enterTime
     */
    public void setEnterTime(long enterTime) {
        this.enterTime = enterTime;
    }

    /**
     * 获取 endTime
     *
     * @return endTime
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * 设置 endTime
     *
     * @param endTime endTime
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    /**
     * 获取 costTime
     *
     * @return costTime
     */
    public long getCostTime() {
        return costTime;
    }

    /**
     * 设置 costTime
     *
     * @param costTime costTime
     */
    public void setCostTime(long costTime) {
        this.costTime = costTime;
    }

    /**
     * 获取 message
     *
     * @return message
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置 message
     *
     * @param message message
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
     * 获取 header
     *
     * @return header
     */
    public List<String> getHeader() {
        return header;
    }

    /**
     * 设置 header
     *
     * @param header header
     */
    public void setHeader(List<String> header) {
        this.header = header;
    }

    /**
     * 获取 method
     *
     * @return method
     */
    public String getMethod() {
        return method;
    }

    /**
     * 设置 method
     *
     * @param method method
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * 获取 typeMethod
     *
     * @return typeMethod
     */
    public String getTypeMethod() {
        return typeMethod;
    }

    /**
     * 设置 typeMethod
     *
     * @param typeMethod typeMethod
     */
    public void setTypeMethod(String typeMethod) {
        this.typeMethod = typeMethod;
    }

    /**
     * 获取 type
     *
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * 设置 type
     *
     * @param type type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取 args
     *
     * @return args
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * 设置 args
     *
     * @param args args
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
     * 获取 error
     *
     * @return error
     */
    public String getError() {
        return error;
    }

    /**
     * 设置 error
     *
     * @param error error
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
     * 获取 model
     *
     * @return model
     */
    public String getModel() {
        return model;
    }

    /**
     * 设置 model
     *
     * @param model model
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 获取 threadName
     *
     * @return threadName
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * 设置 threadName
     *
     * @param threadName threadName
     */
    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    /**
     * 获取 from
     *
     * @return from
     */
    public String getFrom() {
        return from;
    }

    /**
     * 设置 from
     *
     * @param from from
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
     * 获取 parents
     *
     * @return parents
     */
    public Set<String> getParents() {
        return parents;
    }

    /**
     * 设置 parents
     *
     * @param parents parents
     */
    public void setParents(Set<String> parents) {
        this.parents = parents;
    }

    /**
     * 获取 rs
     *
     * @return rs
     */
    public List<String> getRs() {
        return rs;
    }

    /**
     * 设置 rs
     *
     * @param rs rs
     */
    public void setRs(List<String> rs) {
        this.rs = rs;
    }

    /**
     * 获取 element
     *
     * @return element
     */
    public StackTraceElement getElement() {
        return element;
    }

    /**
     * 设置 element
     *
     * @param element element
     */
    public void setElement(StackTraceElement element) {
        this.element = element;
    }

    /**
     * 获取 className
     *
     * @return className
     */
    public String getClassName() {
        return className;
    }

    /**
     * 设置 className
     *
     * @param className className
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * 获取 string
     *
     * @return string
     */
    public String getString() {
        return string;
    }

    /**
     * 设置 string
     *
     * @param string string
     */
    public void setString(String string) {
        this.string = string;
    }

    /**
     * 获取 i1
     *
     * @return i1
     */
    public int getI1() {
        return i1;
    }

    /**
     * 设置 i1
     *
     * @param i1 i1
     */
    public void setI1(int i1) {
        this.i1 = i1;
    }


        this.stack = rs;
    }
}

