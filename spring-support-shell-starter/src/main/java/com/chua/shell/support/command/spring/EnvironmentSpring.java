package com.chua.shell.support.command.spring;

import com.alibaba.fastjson2.JSON;
import com.chua.shell.support.spring.BeanManager;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.google.common.base.Strings;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * invoke
 *
 * @author CH
 */
public class EnvironmentSpring {
    private static final String GET = "GET";
    private static final String SET = "SET";

    public static final PathMatcher PATH_MATCHER = new AntPathMatcher();
    /**
     * 执行
     *
     * @param command 命令
     * @return 结果
     */
    public static String execute(List<String> command) {
        if (command.size() == 3) {
            if (GET.equalsIgnoreCase(command.get(0)) && Strings.isNullOrEmpty(command.get(2))) {
                command = command.subList(0, command.size() - 1);
            }
        }

        if (command.size() == 1) {
            return get(GET, command.get(0));
        }

        //GET
        if (command.size() == 2) {
            String s = command.get(0);
            if (GET.equalsIgnoreCase(s)) {
                return get(GET, command.get(1));
            }
            return set(SET, command.get(0), command.get(1));
        }

        if (command.size() == 3) {
            return set(command.get(0), command.get(1), command.get(2));
        }

        return "命令无法解析";
    }

    /**
     * set
     *
     * @param s  set
     * @param s1 參數
     * @param s2 值
     * @return 結果
     */
    private static String set(String s, String s1, String s2) {
        if (!SET.equalsIgnoreCase(s)) {
            return "命令异常是否是 set";
        }

        BeanManager.getInstance().execute(s1, s2);
        return "";

    }

    /**
     * get
     *
     * @param s  get
     * @param s1 參數
     * @return 結果
     */
    private static String get(String s, String s1) {
        if (!GET.equalsIgnoreCase(s)) {
            return "命令异常是否是 get";
        }


        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        Environment environment = applicationContext.getEnvironment();
        if (!s1.contains("*")) {
            return environment.getProperty(s1, "参数不存在");
        }

        if (environment instanceof ConfigurableEnvironment) {
            Map<String, Object> rs = new LinkedHashMap<>();
            rs.put("head", new String[]{"序列", "名称", "值"});
            List<Object[]> tpl = new LinkedList<>();
            rs.put("rows", tpl);

            AtomicInteger index = new AtomicInteger(1);
            MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
            for (PropertySource<?> propertySource : propertySources) {
                Object source = propertySource.getSource();
                if (source instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet()) {
                        Object k = entry.getKey();
                        Object v = entry.getValue();
                        if (PATH_MATCHER.match(s1, k.toString())) {
                            tpl.add(new Object[]{index.getAndIncrement(), k, v.toString()});
                        }
                    }
                }
            }

            return "@table " + JSON.toJSONString(rs);
//            PATH_MATCHER.match(s1, )

        }
        return "参数不存在";
    }
}
