package com.chua.shell.support.shell;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import lombok.Data;
import org.apache.commons.cli.*;
import org.springframework.core.convert.support.DefaultConversionService;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 属性
 *
 * @author CH
 */
@Data
public class CommandAttribute {
    private static final int COMMAND_LIMIT = 40;
    private boolean b;
    /**
     * 分组
     */
    private String group;

    /**
     * 命令
     */
    private String name;
    /**
     * 描述
     */
    private String describe;
    /**
     * 方法
     */
    private Method method;
    /**
     * 对象
     */
    private Object bean;
    private Parameter[] parameters;
    /**
     * 例子
     */
    private Map<String, String> example = new LinkedHashMap<>();

    private List<String> required = new LinkedList<>();
    final Options options = new Options();

    final Map<String, Object> rs = new LinkedHashMap<>();
    private Map<Parameter, ShellParam> pShell = new LinkedHashMap<>();

    public CommandAttribute(boolean b, String group, String name, String describe, Method method, Object bean) {
        this.b = b;
        this.group = group;
        this.name = name;
        this.describe = describe;
        this.method = method;
        this.bean = bean;
        this.doAnalysisMethod();
    }

    private void doAnalysisMethod() {
        this.parameters = method.getParameters();
        initialUsage();
    }

    private void initialUsage() {
        options.addOption(new Option("h", "help", false, "帮助"));
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            ShellParam shellParam = parameter.getDeclaredAnnotation(ShellParam.class);
            if (null != shellParam) {
                String[] example1 = shellParam.example();
                for (String s : example1) {
                    String[] split = s.split(":", 2);
                    if (split.length != 2) {
                        continue;
                    }
                    this.example.put(split[0], split[1]);
                }
                String value = shellParam.value();
                options.addOption(Option.builder()
                        .longOpt(value)
                        .required(shellParam.required())
                        .numberOfArgs(shellParam.numberOfArgs())
                        .option(Strings.isNullOrEmpty(shellParam.shortName()) ? (b ? value.substring(0, 1) : value) : shellParam.shortName())
                        .optionalArg(!shellParam.required())
                        .type(shellParam.numberOfArgs() > 1 ? List.class : String.class)
                        .desc(shellParam.describe())
                        .argName(value)
                        .build());

                pShell.put(parameter, shellParam);
            }

        }
    }

    private boolean isHelp(List<String> value) {
        if (value.size() == 1 && hasRequired()) {
            return true;
        }

        for (String s : value) {
            if (s.contains("--help") || s.contains("-h") || s.contains("-?") || s.contains("--?")) {
                return true;
            }
        }

        return false;
    }

    private boolean hasRequired() {
        return !required.isEmpty();
    }

    private boolean isHelp(String[] value) {
        return isHelp(Arrays.asList(value));
    }

    /**
     * 执行方法
     *
     * @param command 参数
     * @param shell   shell
     * @param obj     对象
     * @return 结果
     * @[param pipeData 数据
     */
    public String execute(String command, String pipeData, Shell shell, Object obj) {
        List<String> options = Splitter.onPattern("\\s+").splitToList(command);
        if (isHelp(options)) {
            return helpInfo(shell);
        }

        Map<String, Object> env = analysisEnv(options, shell);
        return execute(options, pipeData, env, obj);
    }

    /**
     * 执行
     *
     * @param options  命令
     * @param pipeData 上个结果
     * @param env      环境
     * @param obj      对象
     * @return
     */
    private String execute(List<String> options, String pipeData, Map<String, Object> env, Object obj) {
        CommandLineParser parser = new DefaultParser();
        String[] strings = options.subList(1, options.size()).toArray(new String[0]);
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(this.options, strings);
        } catch (ParseException ignored) {
            return "命令解析失败";
        }

        return execute(options, commandLine, pipeData, env, obj);
    }

    /**
     * 执行
     *
     * @param options     命令
     * @param commandLine 命令
     * @param pipeData    上个结果
     * @param env         环境
     * @param obj           obj
     * @return
     */
    private String execute(List<String> options, CommandLine commandLine, String pipeData, Map<String, Object> env, Object obj) {
        Object[] args = analysisArgs(options, commandLine, pipeData, env, obj);
        method.setAccessible(true);
        try {
            return method.invoke(bean, args).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 参数
     *
     * @param options     命令
     * @param commandLine 命令
     * @param pipeData    上个结果
     * @param env         环境
     * @param obj       obj
     * @return
     */
    private Object[] analysisArgs(List<String> options, CommandLine commandLine, String pipeData, Map<String, Object> env, Object obj) {
        int index = 0;
        boolean hasOption = hasOptions(options);
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getDeclaredAnnotation(ShellOriginal.class) != null) {
                args[i] = converter(Joiner.on(" ").join(options.subList(1, options.size())), parameter.getType(), env);
                continue;
            }

            if (parameter.getDeclaredAnnotation(ShellPipe.class) != null) {
                args[i] = pipeData;
                continue;
            }

            ShellParam shellParam = parameter.getDeclaredAnnotation(ShellParam.class);
            if (null == shellParam) {
                if (null != obj && parameter.getType().isAssignableFrom(obj.getClass())) {
                    args[i] = obj;
                    continue;
                }


                List<String> argList = commandLine.getArgList();
                lo:
                for (Object value : env.values()) {
                    if (null != value && parameter.getType().isAssignableFrom(value.getClass())) {
                        args[i] = value;
                        break lo;
                    }
                }
                if (null != args[i]) {
                    continue;
                }
                args[i] = converter(argList.get(index++ % argList.size()), parameter.getType(), env);
                continue;
            }

            int i1 = shellParam.numberOfArgs();
            if (i1 > 1) {
                if (!hasOption) {
                    args[i] = converter(commandLine.getArgList(), parameter.getType(), env);
                    continue;
                }
                args[i] = converter(commandLine.getOptionValues(shellParam.value()), parameter.getType(), env);
                continue;
            }
            if (!hasOption) {
                List<String> argList = commandLine.getArgList();
                try {
                    args[i] = converter(argList.get(index++ % argList.size()), parameter.getType(), env);
                } catch (Exception e) {
                    args[i] = converter(shellParam.defaultValue(), parameter.getType(), env);
                }
                continue;
            }
            args[i] = converter(commandLine.getOptionValue(shellParam.value(), shellParam.defaultValue()), parameter.getType(), env);
        }

        return args;
    }

    private boolean hasOptions(List<String> options) {
        for (String option : options) {
            if (option.startsWith("-")) {
                return true;
            }
        }

        return false;
    }

    private Object converter(Object optionValue, Class<?> type, Map<String, Object> env) {
        if (optionValue instanceof String && optionValue.toString().startsWith("'") && optionValue.toString().endsWith("'")) {
            optionValue = DefaultConversionService.getSharedInstance().convert(env.getOrDefault(optionValue.toString().substring(1, optionValue.toString().length() - 1), optionValue), String.class);
        }
        return DefaultConversionService.getSharedInstance().convert(optionValue, type);
    }

    /**
     * 环境
     *
     * @param options 命令
     * @param shell   shell
     * @return 环境
     */
    private Map<String, Object> analysisEnv(List<String> options, Shell shell) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("shell", shell);
        env.putAll(System.getenv());
        env.putAll(shell.getEnvironment());
        return env;
    }

    private String helpInfo(Shell shell) {
        HelpFormatter hf = new HelpFormatter();
        try (StringWriter stringWriter = new StringWriter();
             PrintWriter printWriter = new PrintWriter(stringWriter)) {
            hf.printHelp(printWriter, 800, name, null, this.options, 1, 3, null, true);
            String toString = stringWriter.toString();
            if (!(shell instanceof WebShell)) {
                System.out.println(toString);
            }
            return toString;
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * 获取命令
     *
     * @param command 命令行
     * @return 命令
     */
    private String analysisCommand(String command) {
        return command.split("\\s+")[0];
    }

    /**
     * 执行方法
     *
     * @param options 参数
     * @param shell   shell
     * @return 结果
     */
    public String execute(List<String> options, Shell shell) {

        Object[] args = create(options, shell);
        method.setAccessible(true);
        try {
            return String.valueOf(method.invoke(bean, args));
        } catch (Throwable e) {
            return "无效参数";
        }
    }

    /**
     * 参数
     *
     * @param options 请求
     * @param shell   shell
     * @return 参数
     */
    private Object[] create(List<String> options, Shell shell) {
        Object[] args = new Object[parameters.length];
        if (args.length == 0) {
            return args;
        }

        try {
            CommandLineParser parser = new DefaultParser();
            String[] strings = options.subList(1, options.size()).toArray(new String[0]);
            CommandLine commandLine = parser.parse(this.options, strings);
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (parameter.getDeclaredAnnotation(ShellOriginal.class) != null) {
                    try {
                        args[i] = createValue(parameter, Joiner.on(" ").join(strings));
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                try {
                    args[i] = createValue(parameter, createArg(parameter, commandLine, shell));
                } catch (Exception ignored) {
                }
            }
            args = checkDefault(args, commandLine);
        } catch (ParseException e) {
            return null;
        }


        return args;
    }

    private Object[] checkDefault(Object[] args, CommandLine commandLine) {
        Object[] rs = new Object[args.length];
        int count = 0;
        for (int i = 0; i < parameters.length; i++) {
            rs[i] = args[i];
            if (null != rs[i] && !"".equals(rs[i])) {
                continue;
            }

            Parameter parameter = parameters[i];
            ShellParam shellParam = pShell.get(parameter);
            if (null == shellParam) {
                continue;
            }

            if (!shellParam.isDefault()) {
                continue;
            }
            List<String> argList = commandLine.getArgList();
            if (argList.size() > count) {
                rs[i] = argList.get(count++);
            }
        }

        return rs;
    }

    private Object createValue(Parameter parameter, Object arg) {
        Class<?> type = parameter.getType();
        if (null == arg || type.isAssignableFrom(arg.getClass())) {
            return arg;
        }

        if (String.class.isAssignableFrom(type)) {
            if (arg instanceof Iterable) {
                return Joiner.on(" ").join((Iterable<? extends Object>) arg);
            }

            return String.valueOf(arg);
        }
        return DefaultConversionService.getSharedInstance().convert(arg, type);
    }

    /**
     * 参数
     *
     * @param parameter   字段
     * @param commandLine 选项
     * @param shell       shell
     * @return 结果
     */
    private Object createArg(Parameter parameter, CommandLine commandLine, Shell shell) {
        if (Shell.class == parameter.getType()) {
            return shell;
        }
        ShellParam shellParam = pShell.get(parameter);
        if (null == shellParam) {
            return null;
        }

        if (shellParam.numberOfArgs() > 1) {
            return commandLine.getOptionValues(shellParam.value());
        }
        return commandLine.getOptionValue(shellParam.value(), shellParam.defaultValue());
    }


    public Map<String, Object> usage() {
        if (rs.isEmpty()) {
            HelpFormatter hf = new HelpFormatter();
            try (StringWriter stringWriter = new StringWriter();
                 PrintWriter printWriter = new PrintWriter(stringWriter)) {
                hf.printHelp(printWriter, 800, name, null, options, 1, 1, null, true);
                String toString = stringWriter.toString();
                rs.put("title", name);
                rs.put("key", name);
                rs.put("group", group);
                rs.put("description", describe);
                rs.put("usage", toString.split("\r")[0]);
                List<Map<String, String>> ex = new LinkedList<>();
                rs.put("example", ex);
                for (Map.Entry<String, String> entry : example.entrySet()) {
                    Map<String, String> item = new HashMap<>(2);
                    item.put("cmd", entry.getKey());
                    item.put("desc", entry.getValue());

                    ex.add(item);
                }

                return rs;
            } catch (IOException ignored) {
            }

            return Collections.emptyMap();
        }
        return rs;
    }


}
