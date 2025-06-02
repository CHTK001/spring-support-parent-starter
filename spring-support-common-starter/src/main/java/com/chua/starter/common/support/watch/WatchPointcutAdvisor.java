package com.chua.starter.common.support.watch;

import com.chua.common.support.span.TrackContext;
import com.chua.common.support.utils.StringUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * 切面
 *
 * @author CH
 */
@Lazy
public class WatchPointcutAdvisor extends StaticMethodMatcherPointcutAdvisor implements InitializingBean {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (Proxy.isProxyClass(targetClass)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(method, Watch.class) ||
                AnnotatedElementUtils.hasAnnotation(targetClass, Watch.class);

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                Span currentSpan = NewTrackManager.getCurrentSpan();
                if (null == currentSpan) {
                    String linkId = UUID.randomUUID().toString();
                    TrackContext.setLinkId(linkId);
                }
                Span entrySpan = NewTrackManager.createEntrySpan();
                Method method = invocation.getMethod();
                Object obj = invocation.getThis();
                Object[] objects = invocation.getArguments();
                try {
                    return invocation.proceed();
                } finally {
                    endSpan(currentSpan, entrySpan, method, obj, objects);
                }
            }

        });
    }



    private static void endSpan(Span currentSpan, Span lastSpan, Method method, Object obj, Object[] objects) {
        NewTrackManager.refreshCost(lastSpan);
        NewTrackManager.refreshCost(currentSpan);
        if (null != lastSpan) {
            doRefreshSpan(method, objects, lastSpan);
        }
    }


    public static List<Span> build(Stack<Span> spans) {
        if(null == spans) {
            return Collections.emptyList();
        }

        NewTrackManager.clear();
        List<Span> spans1 = new LinkedList<>(spans);

        refix(spans1);
        if (spans1.size() == 1) {
            if ("java.lang.Object".equals(spans1.get(0).getType())) {
                //
            } else {
            }
        } else {
            //  Collections.reverse(spans1);
            Set<Span> rs = new LinkedHashSet<>(spans);
            if (spans1.isEmpty()) {
                return spans1;
            }

            List<Span> result = new LinkedList<>(rs);
            Span span1 = result.get(0);
            Span span2 = result.get(result.size() - 1);
            if (null == span1.getEx()) {
                span1.setEx(span2.getEx());
            }

            return sendSpan(result);
        }

        return spans1;
    }
    private static List<Span> sendSpan(List<Span> result) {
        Span span1 = result.get(0);
        if(StringUtils.isNullOrEmpty(span1.getEx())) {
            return result;
        }
        List<Span> spans2 = new LinkedList<>();
        Span pSpan = null;
        Map<String, String> cache = new LinkedHashMap<>();
        for (Span span : result) {
            if (!StringUtils.isNullOrEmpty(span.getTypeMethod())) {
                String pid = null;
                if (null != pSpan) {
                    if (pSpan.getTypeMethod().equals(span.getTypeMethod())) {
                        continue;
                    }
                    if (cache.containsKey(span.getId())) {
                        pid = cache.get(span.getId());
                    } else {
                        pid = pSpan.getId();
                        cache.put(span.getId(), pSpan.getId());
                    }
                    span.setPid(pid);
                }
                spans2.add(span);
                pSpan = span;
            }
        }

       return repackage(spans2);
    }


    private static List<Span> repackage(List<Span> spans2) {
        List<Span> rs = new LinkedList<>();
        for (Span span : spans2) {
            rs.add(span);
            if (span.getParents().isEmpty()) {
                continue;
            }

            if (null == span.getFrom()) {
                continue;
            }

            String newPid = getNewPid(rs, span);
            if (null == newPid) {
                continue;
            }

            if (span.getId().equals(newPid)) {
                continue;
            }
            span.setPid(newPid);
        }

        return rs;
    }
    private static String getNewPid(List<Span> source, Span span) {
        Set<String> parents = span.getParents();
        for (String parent : parents) {
            if (parent.equals(span.getFrom())) {
                continue;
            }

            String parent1 = getParent(source, parent);
            if (null != parent1) {
                if (parent1.equals(span.getId()) || parent1.equals(span.getPid())) {
                    return null;
                }
                return parent1;
            }
        }

        return null;
    }

    private static String getParent(List<Span> source, String parent) {
        String pid  = null;
        for (int i = 0; i < source.size(); i++) {
            Span span = source.get(i);
            if(null != span.getFrom() && span.getFrom().equals(parent)) {
                pid = span.getId();
            }

        }
        return pid;
    }

    private static void refix(List<Span> spans1) {
        List<Span> del = new LinkedList<>();
        List<String> sign = new LinkedList<>();
        for (Span span : spans1) {
            if ("java.lang.Object".equals(span.getType())) {
                del.add(span);
                continue;
            }

            String key = null;
            try {
                key = createKey(span);
            } catch (Exception e) {
                continue;
            }
            if (sign.contains(key)) {
                del.add(span);
                continue;
            }


            sign.add(key);
        }

        spans1.removeAll(del);

    }
    private static String createKey(Span span) {
        return span.getMessage() + span.getEnterTime() + span.getPid();
    }

    /**
     * 链路
     *
     * @param method   方法
     * @param args     参数
     * @param lastSpan 链路
     */
    static void doRefreshSpan(Method method, Object[] args, Span lastSpan) {
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        lastSpan.setFrom(className + "." + methodName);
        lastSpan.setMessage("链路追踪(MQ)：" + lastSpan.getLinkId() + " " + className + "." + methodName + " 耗时：" + lastSpan.getCostTime() + "ms");
        lastSpan.setStack(Thread.currentThread().getStackTrace());
        lastSpan.setTypeMethod(className + "." + methodName);
        lastSpan.setMethod(method.getDeclaringClass().getSimpleName() + "." + methodName);
        lastSpan.setType(className);
    }

}
