package com.chua.shell.support.shell.mapping;

import com.chua.common.support.view.view.ClassInfoView;
import com.chua.common.support.view.view.ObjectVO;
import com.chua.common.support.view.view.ObjectView;
import com.chua.shell.support.CfrDecompiler;
import com.chua.shell.support.Decompiler;
import com.chua.shell.support.shell.ShellMapping;
import com.chua.shell.support.shell.ShellParam;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.DiagnosticCommandMBean;
import org.springframework.util.ClassUtils;
import org.zeroturnaround.zip.commons.FileUtils;
import org.zeroturnaround.zip.commons.IOUtils;
import sun.management.ManagementFactoryHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * 基础命令
 *
 * @author CH
 */
public class DelegateCommand {

    public static final int COMMAND_LIMIT = 60;

    DiagnosticCommandMBean diagnosticCommandMBean = ManagementFactoryHelper.getDiagnosticCommandMBean();
    private final ObjectMapper objectMapper = new ObjectMapper();


    @ShellMapping(value = {"view"}, describe = "预览")
    public String view(
            @ShellParam(value = "mode", describe = "模式", example = {"view --mode CLASS class: view --mode CLASS java.lang.String"}, numberOfArgs = 2) List<String> file
    ) {
        if (null == file || file.size() != 2) {
            return "参数不正确, view --mode CLASS class: 模式";
        }

        if ("class".equalsIgnoreCase(file.get(0))) {
            try {
                return new ClassInfoView(ClassUtils.forName(file.get(1), ClassLoader.getSystemClassLoader()), true, 100).draw();
            } catch (ClassNotFoundException e) {
                return "类不存在, view --mode CLASS class";
            }
        }

        if ("bean".equalsIgnoreCase(file.get(0))) {
            try {
                return new ObjectView(new ObjectVO(SpringBeanUtils.getApplicationContext().getBean(file.get(1)), 5)).draw();
            } catch (Exception e) {
                return "bean不存在, view --mode bean Shell";
            }
        }
        return "";
    }

    /**
     * jad
     *
     * @param file     文件/类
     * @param showLine 是否显示行号
     * @return 结果
     */
    @ShellMapping(value = {"jad"}, describe = "反编译文件")
    public String jad(
            @ShellParam(value = "file", example = {"jad --file java.lang.String toString: 反编译文件"}, numberOfArgs = 2) List<String> file,
            @ShellParam(value = "line", example = {"jad --file java.lang.String toString --line:  反编译文件"}, defaultValue = "true") Boolean showLine
    ) {
        if (null == file || file.size() < 1) {
            return "参数不正确";
        }
        Decompiler decompiler = new CfrDecompiler();

        String s = file.get(0);
        Class<?> aClass = null;
        try {
            aClass = Class.forName(s, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ignored) {
        }

        if (null != aClass) {
            URL resource = Thread.currentThread().getContextClassLoader().getResource(s.replace(".", "/") + ".class");
            if (null == resource) {
                return "文件/类不存在";
            }

            if ("file".equals(resource.getProtocol())) {
                return decompiler.decompile(resource.getFile(), file.size() == 1 ? null : file.get(1), false, showLine);
            }

            File temp = new File(System.getProperty("os.home"), aClass.getTypeName().replace(".", "_"));
            try (FileOutputStream fos = new FileOutputStream(temp);
                 InputStream is = resource.openStream();
            ) {
                IOUtils.copy(is, fos);
            } catch (IOException ignored) {
            }
            try {
                return decompiler.decompile(temp.getAbsolutePath(), file.size() == 1 ? null : file.get(1), false, showLine);
            } finally {
                try {
                    FileUtils.forceDelete(temp);
                } catch (IOException ignored) {
                }
            }
        }

        return decompiler.decompile(s, file.size() == 1 ? null : file.get(1), false, showLine);
    }

    /**
     * jstack
     *
     * @return jstack
     */
    @ShellMapping(value = "jstack", describe = "jstack")
    public String stack() {
        try {
            Object res = diagnosticCommandMBean.invoke("threadPrint", new Object[]{new String[]{}}, new String[]{String[].class.getName()});
            return res.toString();
        } catch (Exception ignored) {
        }
        return "命令不存在";
    }


}
