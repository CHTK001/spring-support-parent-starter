package com.chua.shell.support.shell.mapping;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.view.view.TableView;
import com.chua.shell.support.StringUtils;
import com.chua.shell.support.command.spring.EnvironmentSpring;
import com.chua.shell.support.shell.ShellMapping;
import com.chua.shell.support.shell.ShellParam;
import com.chua.shell.support.shell.ShellPipe;
import com.chua.shell.support.shell.adaptor.CommandAdaptor;
import com.chua.shell.support.shell.adaptor.JsonCommandAdaptor;
import com.chua.shell.support.shell.adaptor.TxtCommandAdaptor;
import com.chua.shell.support.shell.adaptor.XmlCommandAdaptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统命令
 *
 * @author CH
 */
public class SystemCommand {
    private static final String OS_NAME = System.getProperty("os.name").toUpperCase();

    private static final Map<String, CommandAdaptor> FILE_ADAPTOR = new ConcurrentHashMap<>();
    private static final CharSequence WIN = "WIN";

    static {
        FILE_ADAPTOR.put("xml", new XmlCommandAdaptor());
        FILE_ADAPTOR.put("txt", new TxtCommandAdaptor());
        FILE_ADAPTOR.put("log", new TxtCommandAdaptor());
        FILE_ADAPTOR.put("json", new JsonCommandAdaptor());
    }

    private static final String IP_CONFIG = "ipconfig";

    public static final DecimalFormat D = new DecimalFormat("0.00");
    private static final String IF_CONFIG = "ifconfig";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    /**
     * help
     *
     * @return help
     */
    @ShellMapping(value = {"mem"}, describe = "java内存情况")
    public String spring() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        Map<String, Object> rs = new LinkedHashMap<>();
        rs.put("head", Lists.newArrayList("最大内存", "已使用内存", "剩余内存"));
        rs.put("rows", Collections.singletonList(Lists.newArrayList(
                StringUtils.getNetFileSizeDescription(maxMemory, D),
                StringUtils.getNetFileSizeDescription(totalMemory, D),
                StringUtils.getNetFileSizeDescription(freeMemory, D)
        )));
        try {
            return "@table " + new ObjectMapper().writeValueAsString(rs);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    /**
     * ip
     *
     * @return help
     */
    @ShellMapping(value = {"cat"}, describe = "显示文本文件")
    public String cat(
            @ShellParam(value = "file", example = {"cat --file xx.txt: 反编译文件"}, numberOfArgs = 2) String file
    ) {
        File file1 = new File(file);
        if (!file1.exists()) {
            return "文件不存在";
        }

        String extension = FileUtils.getExtension(file);
        CommandAdaptor commandAdaptor = FILE_ADAPTOR.get(extension);
        if (null == commandAdaptor) {
            commandAdaptor = new TxtCommandAdaptor();
        }

        return commandAdaptor.handler(file1.getAbsolutePath());
    }

    /**
     * ip
     *
     * @return help
     */
    @ShellMapping(value = {"ip"}, describe = "显示当前系统IP")
    public String ip() {
        Map<String, Object> rs = new LinkedHashMap<>();
        rs.put("head", new String[]{"序号", "网卡名称", "IPv4地址", "MAC"});

        List<String[]> rs1 = new LinkedList<>();
        rs.put("rows", rs1);

        try {
            int index = 1;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // *EDIT*
                    if (addr instanceof Inet6Address) {
                        continue;
                    }
                    byte[] hardwareAddress = iface.getHardwareAddress();
                    String mac = null;
                    if (hardwareAddress != null) {
                        String[] hexadecimalFormat = new String[hardwareAddress.length];
                        for (int i = 0; i < hardwareAddress.length; i++) {
                            hexadecimalFormat[i] = String.format("%02X", hardwareAddress[i]);
                        }

                        mac = String.join("-", hexadecimalFormat);
                    }
                    String ip = addr.getHostAddress();
                    rs1.add(new String[]{String.valueOf(index++), iface.getDisplayName(), ip, mac});
                }
            }
            return "@table " + JSON.toJSONString(rs);
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * netstat
     *
     * @return port
     */
    @ShellMapping(value = {"netstat"}, describe = "显示当前系统端口")
    public String netstat() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(out);
            ExecuteWatchdog watchdog = new ExecuteWatchdog(10000);
            CommandLine commandLine = CommandLine.parse("netstat -ano");
            DefaultExecutor executor = new DefaultExecutor();
            executor.setStreamHandler(pumpStreamHandler);
            executor.setWatchdog(watchdog);
            executor.execute(commandLine);
            if (OS_NAME.contains(WIN)) {
                return new String(out.toByteArray(), "GBK");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        return "";
    }

    /**
     * ls
     */
    @ShellMapping(value = {"date"}, describe = "服务器时间")
    public String date() {
        return LocalDateTime.now().format(FORMATTER);
    }

    /**
     * ls
     */
    @ShellMapping(value = {"ls"}, describe = "显示当前目录文件")
    public String ls() {
        File file = new File(".");
        File[] files = file.listFiles();
        TableView tableView = new TableView(new TableView.ColumnDefine[]{
                new TableView.ColumnDefine(100, false, TableView.Align.LEFT),
                new TableView.ColumnDefine(30, false, TableView.Align.LEFT),
                new TableView.ColumnDefine(30, false, TableView.Align.LEFT),
        });
        tableView.addRow("文件名", "文件类型", "大小");
        for (File file1 : files) {
            tableView.addRow(file1.isDirectory() ?
                            AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, file1.getName()) :
                            AnsiOutput.toString(AnsiColor.BRIGHT_BLUE, file1.getName()),
                    file1.isDirectory() ? "文件夹" : "文件", StringUtils.getNetFileSizeDescription(file1.length(), D));
        }

        return tableView.draw();
    }

    /**
     * grep
     */
    @ShellMapping(value = {"grep"}, describe = "过滤信息")
    public String grep(@ShellPipe String data, @ShellParam(value = "name", numberOfArgs = 1) String name) {
        if (Strings.isNullOrEmpty(name)) {
            return data;
        }

        if (data.startsWith("@table ")) {
            return "@table " + analysisTable(data.substring(7), name);
        }

        List<String> split = Splitter.on("\n").omitEmptyStrings().trimResults().splitToList(data);
        if (split.size() == 1) {
            return data;
        }

        List<String> line = new LinkedList<>();
        int index = 0;
        for (int i = 0; i < split.size(); i++) {
            String s = split.get(i);
            if (s.contains(" ") || s.startsWith("+----")) {
                line.add(s);
                break;
            }

            if (split.size() > (i + 1)) {
                String s1 = split.get(i + 1);
                if (s1.contains(" ")) {
                    index = i + 1;
                    line.add(s1);
                    break;
                }
            }
            line.add(s);
            break;
        }
        for (int i = index + 1; i < split.size(); i++) {
            String s = split.get(i);
            if (isMatch(s, name)) {
                line.add(ansi(s, name));
            }
        }

        if (line.size() == 1) {
            return "";
        }
        return Joiner.on("\r\n").join(line);
    }

    private String analysisTable(String substring, String name) {
        JSONObject jsonObject = JSON.parseObject(substring);
        JSONArray jsonArray = jsonObject.getJSONArray("rows");
        List less = new LinkedList();
        jsonArray.forEach(it -> {
            if (isMathch(it.toString(), name)) {
                JSONArray item = (JSONArray) it;
                less.add(item.stream().map(Object::toString).collect(Collectors.toList()));
            }
        });
        jsonObject.put("rows", less);

        return jsonObject.toJSONString();
    }

    private boolean isMathch(String toString, String name) {
        if (name.contains("*")) {
            return EnvironmentSpring.PATH_MATCHER.match(name, toString);
        }

        return toString.contains(name);
    }

    private String ansi(String name, String s) {
        if (name.contains("*")) {
            return name;
        }
        return name.replace(s, AnsiOutput.toString(AnsiColor.RED, s));
    }

    final static PathMatcher MATCHER = new AntPathMatcher();

    private boolean isMatch(String s, String name) {
        if (name.contains("*")) {
            return MATCHER.match("*" + name + "*", s);
        }
        return s.contains(name);
    }
}
