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
public class Span  implements Serializable {

    private String linkId;  //链路ID
    private String id;  //链路ID
    private String pid;  //链路ID
    private long enterTime = System.nanoTime(); //方法进入时间
    private long endTime;
    private long costTime;//耗时
    private String message;
    private List<String> stack;
    private List<String> header;
    private String method;
    private String typeMethod;
    private String type;

    private Object[] args;
    private boolean title;
    private String ex;
    private String error;
    private String db;
    private String model;
    private String threadName;
    private String from;
    private transient volatile Set<String> parents = new LinkedHashSet<>();

    public void setPid(String pid) {
        this.pid = pid;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Span() {
        this.enterTime = System.nanoTime();
        this.threadName = Thread.currentThread().getName();
    }

    public Span(String linkId) {
        this.linkId = linkId;
        this.enterTime = System.nanoTime();
        setStack(Thread.currentThread().getStackTrace());
        this.threadName = Thread.currentThread().getName();
    }

    public void setStack(StackTraceElement[] stackTrace) {
        List<String> rs = new LinkedList<>();
        for (int i = 0, stackTraceLength = stackTrace.length; i < stackTraceLength; i++) {
            StackTraceElement element = stackTrace[i];
            String className = element.getClassName();
            if (className.startsWith("com.chua.agent.support")) {
                continue;
            }

            String string = element.toString();
            rs.add(string);
            if (i < 1 || string.startsWith("sun") || string.contains("$") || string.contains("<init>") || string.startsWith("java")) {
                continue;
            }

            int i1 = string.indexOf("(");
            parents.add(i1 > -1 ? string.substring(0, i1) : string);
        }
        this.stack = rs;
    }
}