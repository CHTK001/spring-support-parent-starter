package com.chua.shell.support.oshi;

import com.alibaba.fastjson2.JSON;
import com.chua.shell.support.shell.ShellMapping;
import com.chua.shell.support.shell.ShellParam;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;

import java.util.*;

/**
 * @author CH
 */
public class OshiCommand {

    SystemInfo si = new SystemInfo();

    HardwareAbstractionLayer hal = si.getHardware();
    OperatingSystem os = si.getOperatingSystem();

    static {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
    }

    /**
     * help
     *
     * @return help
     */
    @ShellMapping(value = {"proc"}, describe = "系统进程")
    public String proc(
            @ShellParam(value = "cpu", defaultValue = "false") boolean showCpu,
            @ShellParam(value = "address", defaultValue = "false") boolean showAddress,
            @ShellParam(value = "num", defaultValue = "5") int num
    ) {

        if (showCpu) {
            GlobalMemory memory = hal.getMemory();
            List<OSProcess> procs = Arrays.asList(os.getProcesses(num, OperatingSystem.ProcessSort.CPU));
            Map<String, Object> rs = new LinkedHashMap<>();
            rs.put("head", new String[]{"PID", "CPU", "内存", "VSZ", "RSS", "NAME"});
            List<String[]> tpl = new LinkedList<>();
            rs.put("rows", tpl);
            for (int i = 0; i < procs.size() && i < 5; i++) {
                OSProcess p = procs.get(i);
                tpl.add(new String[]{
                        String.format("%5d", p.getProcessID()),
                        String.format("%5.1f", 100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime()),
                        String.format("%5.1f", 100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime()),
                        String.format("%4.1f", 100d * p.getResidentSetSize() / memory.getTotal(), FormatUtil.formatBytes(p.getVirtualSize())),
                        String.format("%9s", 100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime()),
                        String.format("%9s", FormatUtil.formatBytes(p.getResidentSetSize()), p.getName())
                });
            }
            return "@table " + JSON.toJSONString(rs);
        }

        if (showAddress) {

            NetworkIF[] networkIFs = hal.getNetworkIFs();
            Map<String, Object> rs = new LinkedHashMap<>();
            rs.put("head", new String[]{"名称", "MAC", "MTU", "Speed", "IPv4", "IPV6", "Traffic"});
            List<String[]> tpl = new LinkedList<>();
            rs.put("rows", tpl);
            for (NetworkIF net : networkIFs) {
                boolean hasData = net.getBytesRecv() > 0 || net.getBytesSent() > 0 || net.getPacketsRecv() > 0
                        || net.getPacketsSent() > 0;
                tpl.add(new String[]{
                        String.format(" Name: %s (%s)%n", net.getName(), net.getDisplayName()),
                        net.getMacaddr(),
                        net.getMTU() + "",
                        FormatUtil.formatValue(net.getSpeed(), "bps"),
                        Arrays.toString(net.getIPv4addr()),
                        Arrays.toString(net.getIPv6addr()),
                        String.format("   Traffic: received %s/%s%s; transmitted %s/%s%s %n",
                                hasData ? net.getPacketsRecv() + " packets" : "?",
                                hasData ? FormatUtil.formatBytes(net.getBytesRecv()) : "?",
                                hasData ? " (" + net.getInErrors() + " err)" : "",
                                hasData ? net.getPacketsSent() + " packets" : "?",
                                hasData ? FormatUtil.formatBytes(net.getBytesSent()) : "?",
                                hasData ? " (" + net.getOutErrors() + " err)" : "")
                });
            }
            return "@table " + JSON.toJSONString(rs);
        }

        return "@ansi " + AnsiOutput.toString(
                "",
                AnsiColor.BRIGHT_BLUE, "进程数: ", AnsiColor.RED, os.getProcessCount(), "\r\n",
                AnsiColor.BRIGHT_BLUE, "线程数: ", AnsiColor.RED, os.getThreadCount(), "\r\n"
        );
    }

    ;
}
