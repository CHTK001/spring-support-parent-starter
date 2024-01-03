package com.chua.shell.support.command.spring;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.shell.ShellResult;
import com.chua.common.support.shell.ShellTable;
import com.google.common.base.Strings;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

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
    public static ShellResult execute(List<String> command) {
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

        return ShellResult.error("命令无法解析");
    }

    /**
     * set
     *
     * @param s     set
     * @param name  參數
     * @param value 值
     * @return 結果
     */
    private static ShellResult set(String s, String name, String value) {
        if (!SET.equalsIgnoreCase(s)) {
            return ShellResult.error("命令异常是否是 set");
        }

        EnvironmentMapping environmentMapping = SpringBeanUtils.getEnvironmentMapping();
        environmentMapping.execute(name, value);
        return ShellResult.text("");

    }

    /**
     * get
     *
     * @param s  get
     * @param s1 參數
     * @return 結果
     */
    private static ShellResult get(String s, String s1) {
        if (!GET.equalsIgnoreCase(s)) {
            return ShellResult.error("命令异常是否是 get");
        }


        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        Environment environment = applicationContext.getEnvironment();
        if (!s1.contains(CommonConstant.SYMBOL_ASTERISK)) {
            return ShellResult.error(environment.getProperty(s1, "参数不存在"));
        }

        if (environment instanceof ConfigurableEnvironment) {
            ShellTable rs = new ShellTable("序列", "名称", "值");

            AtomicInteger index = new AtomicInteger(1);
            MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
            for (PropertySource<?> propertySource : propertySources) {
                Object source = propertySource.getSource();
                if (source instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet()) {
                        Object k = entry.getKey();
                        Object v = entry.getValue();
                        if (PATH_MATCHER.match(s1, k.toString())) {
                            rs.addRow(index.getAndIncrement() + "", k + "", v.toString());
                        }
                    }
                }
            }

            return ShellResult.table(rs.toString());
//            PATH_MATCHER.match(s1, )

        }
        return ShellResult.error("参数不存在");
    }
}
