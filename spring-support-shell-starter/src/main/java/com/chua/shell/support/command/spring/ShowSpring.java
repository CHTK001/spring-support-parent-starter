package com.chua.shell.support.command.spring;

import com.chua.common.support.ansi.AnsiColor;
import com.chua.common.support.ansi.AnsiOutput;
import com.chua.common.support.function.SafeFunction;
import com.chua.common.support.shell.ShellResult;
import com.chua.common.support.shell.ShellTable;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * invoke
 *
 * @author CH
 */
public class ShowSpring {
    private static String BEAN = "bean";

    /**
     * 执行
     *
     * @param command 命令
     * @return 结果
     */
    public static ShellResult execute(List<String> command) {
        if (command.size() > 1) {
            return ShellResult.error( "无效参数");
        }

        String s = command.get(0);
        if (BEAN.equalsIgnoreCase(s)) {
            ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
            String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
            ShellTable rs = new ShellTable("序列", "Bean", "类");

            List<Object[]> tpl = new LinkedList<>();
            for (String beanDefinitionName : beanDefinitionNames) {
                rs.addRow(beanDefinitionName, applicationContext.getBean(beanDefinitionName).getClass().getTypeName());
            }
            return ShellResult.table(rs.toString());

        }
        if ("mapping".equalsIgnoreCase(s)) {
            ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
            RequestMappingHandlerMapping requestMappingHandlerMapping;
            try {
                requestMappingHandlerMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            } catch (BeansException e) {
                return ShellResult.error("无法解析");
            }
            Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
            ShellTable rs = new ShellTable("方法", "路径", "响应类型", "处理方法");



            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                RequestMappingInfo entryKey = entry.getKey();
                HandlerMethod handlerMethod = entry.getValue();
                rs.addRow(
                        AnsiOutput.toString(AnsiColor.GREEN, Arrays.toString(entryKey.getMethodsCondition().getMethods().stream().map(Enum::name).toArray(String[]::new))),
                        AnsiOutput.toString(AnsiColor.BLUE, ObjectUtils.withNull(entryKey.getPatternsCondition(), (SafeFunction<PatternsRequestCondition, Object>) patternsRequestCondition -> Arrays.toString(ArrayUtils.toArray(entryKey.getPatternsCondition().getPatterns())))),
                        entryKey.getProducesCondition().getProducibleMediaTypes().stream().map(MediaType::toString).collect(Collectors.joining("\r\n")),
                        handlerMethod.toString()
                );
            }
            return ShellResult.table(rs.toString());

        }

        return ShellResult.text("");
    }
}
