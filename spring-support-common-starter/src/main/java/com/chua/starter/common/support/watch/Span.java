package com.chua.starter.common.support.watch;

import lombok.Data;

import java.io.Serializable;
import java.util.*;

/**
 * @author Administrator
 */
@Data
public class Span implements Serializable {

    private String linkId;  //链路ID
    private String id;  //链路ID
    private String pid;  //链路ID
    private Date enterTime; //方法进入时间
    private long costTime;//耗时
    private String message;
    private List<String> stack;
    private List<String> header;
    private String method;
    private String typeMethod;
    private String type;
    private boolean title;
    private String ex;
    private String error;
    private String db;
    private String model;
    private String threadName;
    private String from;
    private transient volatile Set<String> parents = new LinkedHashSet<>();


    public Span() {
        this.enterTime = new Date();
        this.threadName = Thread.currentThread().getName();
    }

    public Span(String linkId) {
        this.linkId = linkId;
        this.enterTime = new Date();
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