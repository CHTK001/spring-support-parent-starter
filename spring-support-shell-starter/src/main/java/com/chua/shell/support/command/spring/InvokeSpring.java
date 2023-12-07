package com.chua.shell.support.command.spring;

import com.chua.common.support.shell.ShellResult;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * invoke
 *
 * @author CH
 */
public class InvokeSpring {
    /**
     * 执行
     *
     * @param command 命令
     * @return 结果
     */
    public static ShellResult execute(List<String> command) {
        if (command.size() != 2) {
            return ShellResult.error("无效参数");
        }

        ApplicationContext applicationContext = SpringBeanUtils.getApplicationContext();
        String name = command.get(0);
        String methodName = command.get(1);
        Object bean;
        if (!applicationContext.containsBean(name)) {
            Class<?> aClass = null;
            try {
                aClass = ClassUtils.forName(name, ClassUtils.getDefaultClassLoader());
            } catch (ClassNotFoundException ignored) {
            }

            if (null == aClass) {
                return ShellResult.error(name + "(Bean)不存在");
            }

            Map<String, ?> beansOfType = applicationContext.getBeansOfType(aClass);
            if (beansOfType.isEmpty()) {
                return ShellResult.error(name + "(Bean)不存在");
            }
            bean = beansOfType.values().iterator().next();
        } else {
            bean = applicationContext.getBean(name);
        }

        Method method = ReflectionUtils.findMethod(bean.getClass(), methodName);
        if (null == method) {
            return ShellResult.error(methodName + "(方法)不存在");
        }

        com.chua.common.support.utils.ClassUtils.setAccessible(method);
        try {
            return ShellResult.text(DefaultConversionService.getSharedInstance().convert(method.invoke(bean, new Object[0]), String.class));
        } catch (Exception ignored) {
        }
        return null;
    }
}
