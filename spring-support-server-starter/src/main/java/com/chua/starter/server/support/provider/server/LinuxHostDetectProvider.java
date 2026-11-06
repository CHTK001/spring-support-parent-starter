// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.server.support.provider.server;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.server.support.connection.ConnectionManager;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.spi.HostDetectProvider;
import com.chua.starter.server.support.software.SoftwareCatalog;
import com.chua.starter.server.support.software.SoftwareInstanceVO;
import com.chua.starter.server.support.util.SshClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
   * Linux 主机硬件探测提供者（SSH 采集）
   * <p>
   * 通过 SSH 连接目标 Linux 主机，执行系统命令采集 CPU/内存/磁盘/系统版本/架构/内网 IP，
   * 探测结果回写主机资产生成 {@link MsServerHost} 资产字段。
   * </p>
   *
   * @author CH
   * @since 4.0.0.42
   */
@Slf4j
@Spi(value = "linux", order = 100)
public class LinuxHostDetectProvider implements HostDetectProvider {

      /**
       * SSH 连接池管理器（负责借取/释放 SSH 连接）
       */
    @Autowired
    private ConnectionManager connectionManager;

    @Override
    public String osType() {
        return "LINUX";
    }

    @Override
    public ReturnResult<MsServerHost> detect(MsServerHost host, SshCredential credential) {
        SshClients.Handle handle = null;
        try {
            handle = connectionManager.borrow(host, credential);
              // CPU 核心数（nproc 命令）
            String cpu = execQuiet(handle, "nproc");
            if (NumberUtils.isNumber(cpu)) {
                host.setServerHostCpuCores(NumberUtils.toInt(cpu));
            }
              // 内存(MB)：直接读 /proc/meminfo 的 MemTotal 换算（精简容器可能没有 free 命令）
            String mem = execQuiet(handle,
                    "awk '/MemTotal/{printf \"%.0f\", $2/1024}' /proc/meminfo");
            if (NumberUtils.isNumber(mem)) {
                host.setServerHostMemoryMb(NumberUtils.toLong(mem));
            }
              // 磁盘(GB, 根分区)，df -k 第二行容量换算
            String disk = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.0f\", $2/1024/1024}'");
            if (NumberUtils.isNumber(disk)) {
                host.setServerHostDiskGb(NumberUtils.toLong(disk));
            }
              // GPU 卡数（nvidia-smi 优先，回退 lspci 统计 VGA）
            String gpuCount = execQuiet(handle, "nvidia-smi -L 2>/dev/null | wc -l");
            if (!NumberUtils.isNumber(gpuCount) || NumberUtils.toInt(gpuCount) == 0) {
                gpuCount = execQuiet(handle, "lspci 2>/dev/null | grep -ci 'vga.*\\|3d controller'");
            }
            if (NumberUtils.isNumber(gpuCount)) {
                int gpu = NumberUtils.toInt(gpuCount);
                if (gpu > 0) {
                    host.setServerHostGpuCount(gpu);
                      // GPU 型号（首个 GPU 名称）
                    String gpuModel = execQuiet(handle, "nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | head -1");
                    if (StringUtils.isBlank(gpuModel)) {
                        gpuModel = execQuiet(handle, "lspci 2>/dev/null | grep -im1 'vga\\|3d controller' | sed 's/^.*controller: //'");
                    }
                    if (StringUtils.isNotBlank(gpuModel)) {
                        host.setServerHostGpuModel(gpuModel.trim());
                    }
                }
            }
              // 网卡信息（JSON 数组：[{"name":"eth0","ip":"10.0.0.3"}]，取 IPv4 全局地址）
            String networks = execQuiet(handle, "ip -o -4 addr show 2>/dev/null | awk 'NR==1{print \"[{\\\"name\\\":\\\"\"$2\"\\\",\\\"ip\\\":\\\"\"substr($4,1,index($4,\"/\")-1)\"\\\"}\"} NR>1{printf \",{\\\"name\\\":\\\"\"$2\"\\\",\\\"ip\\\":\\\"\"substr($4,1,index($4,\"/\")-1)\"\\\"}\"} END{print \"]\"}'");
            if (StringUtils.isNotBlank(networks) && networks.startsWith("[")) {
                host.setServerHostNetworks(networks);
            }
              // CPU 使用率（%）：直接读 /proc/stat 两次采样（间隔 1s），避免 top 在容器/不同 procps 版本下字段错位
            String cpuUsage = execQuiet(handle,
                    "set -- $(grep '^cpu ' /proc/stat); I1=$(($5+$6)); T1=$(($2+$3+$4+$5+$6+$7+$8+$9)); "
                              + "sleep 1; set -- $(grep '^cpu ' /proc/stat); I2=$(($5+$6)); T2=$(($2+$3+$4+$5+$6+$7+$8+$9)); "
                              + "awk -v i1=$I1 -v t1=$T1 -v i2=$I2 -v t2=$T2 "
                              + "'BEGIN{dt=t2-t1; di=i2-i1; if(dt<=0){print 0}else{printf \"%.2f\",(dt-di)*100/dt}}'");
            if (StringUtils.isNotBlank(cpuUsage) && isRatio(cpuUsage)) {
                host.setServerHostCpuUsage(toRatio(cpuUsage));
            }
              // 内存使用率（%）：/proc/meminfo 用 MemTotal 与 MemAvailable（含可回收缓存），直观且稳定
            String memUsage = execQuiet(handle,
                    "awk '/MemTotal/{t=$2}/MemAvailable/{a=$2}/MemFree/{f=$2}"
                              + " END{u=(a>0?a:(f>0?f:0)); if(t>0){printf \"%.2f\",(t-u)*100/t}}' /proc/meminfo");
            if (StringUtils.isNotBlank(memUsage) && isRatio(memUsage)) {
                host.setServerHostMemUsage(toRatio(memUsage));
            }
              // 磁盘使用率（%）（df 根分区已用/总容量）
            String diskUsage = execQuiet(handle, "df -k / | awk 'NR==2{printf \"%.2f\", $3/$2*100}'");
            if (StringUtils.isNotBlank(diskUsage) && isRatio(diskUsage)) {
                host.setServerHostDiskUsage(toRatio(diskUsage));
            }
              // 系统版本（os-release 优先，回退 uname -sr）
            String os = execQuiet(handle,
                    "cat /etc/os-release 2>/dev/null | grep -m1 PRETTY_NAME | cut -d'=' -f2 | tr -d '\"'");
            if (StringUtils.isBlank(os)) {
                os = execQuiet(handle, "uname -sr");
            }
            if (StringUtils.isNotBlank(os)) {
                host.setServerHostOsVersion(os.trim());
            }
              // 架构（uname -m）
            String arch = execQuiet(handle, "uname -m");
            if (StringUtils.isNotBlank(arch)) {
                host.setServerHostOsArch(arch.trim());
            }
              // 包管理器（apt/dnf/yum/brew，供软件仓库 SPI 路由；探测失败回退 unknown）
            String packageManager = execQuiet(handle,
                    "which apt >/dev/null 2>&1 && echo apt || (which dnf >/dev/null 2>&1 && echo dnf"
                              + " || (which yum >/dev/null 2>&1 && echo yum || (which brew >/dev/null 2>&1"
                              + " && echo brew || echo unknown)))");
            if (StringUtils.isNotBlank(packageManager)) {
                host.setServerHostPackageManager(packageManager.trim());
            }
              // 内网 IP（hostname -I 取第一个）
            String ip = execQuiet(handle, "hostname -I 2>/dev/null | awk '{print $1}'");
            if (StringUtils.isNotBlank(ip)) {
                host.setServerHostPrivateIp(ip.trim());
            }
              // 磁盘 IO（KB/s）：/proc/diskstats 两次采样（间隔 1s），整盘扇区增量 *512B
            collectDiskIo(handle, host);
              // 运行软件：ps 全量进程按已知软件特征识别（支持多实例）
            collectSoftware(handle, host);
            return ReturnResult.ok(host, "Linux 硬件探测成功");
        } catch (Exception e) {
            log.warn("[detect] Linux 探测失败 hostId={} address={}: {}",
                    host.getId(), host.getServerHostAddress(), e.getMessage());
            return ReturnResult.error("Linux 硬件探测失败: " + e.getMessage());
        } finally {
            if (handle != null) {
                connectionManager.release(host.getId());
            }
        }
    }

      /**
       * 采集磁盘读写速率（KB/s）：/proc/diskstats 两次采样整盘扇区增量。
       *
       * @param handle SSH 句柄
       * @param host   主机
       */
    private void collectDiskIo(SshClients.Handle handle, MsServerHost host) {
          // whole() 过滤分区：nvme/mmcblk 整盘不含 'p'；sd/vd 整盘不以数字结尾
        String sample = "f(){ awk 'function whole(n){ if(n ~ /^(nvme|mmcblk)/) return index(n,\"p\")==0;"
                  + " return n !~ /[0-9]$/ } { if($3 ~ /^(sd|vd|xvd|hd|nvme|mmcblk)/ && whole($3))"
                  + "{r+=$6;w+=$10} } END{printf \"%d %d\", r, w}' /proc/diskstats; }; "
                  + "echo $(f); sleep 1; echo $(f)";
        String out = execQuiet(handle, sample);
        if (StringUtils.isBlank(out)) {
            return;
        }
        String[] lines = out.trim().split("\\r?\\n");
        if (lines.length < 2) {
            return;
        }
        String[] a = lines[0].trim().split("\\s+");
        String[] b = lines[1].trim().split("\\s+");
        if (a.length < 2 || b.length < 2
                || !NumberUtils.isNumber(a[0]) || !NumberUtils.isNumber(b[0])) {
            return;
        }
        long r1 = NumberUtils.toLong(a[0]), w1 = NumberUtils.toLong(a[1]);
        long r2 = NumberUtils.toLong(b[0]), w2 = NumberUtils.toLong(b[1]);
          // 扇区 512B，间隔 1s，*512/1024 = KB/s；计数器回绕时为负则归零
        double readKbs = Math.max(0, (r2 - r1) * 512.0 / 1024.0);
        double writeKbs = Math.max(0, (w2 - w1) * 512.0 / 1024.0);
        host.setServerHostDiskReadKbs(round2(readKbs));
        host.setServerHostDiskWriteKbs(round2(writeKbs));
    }

      /**
       * 采集运行软件实例：ps 全量进程按软件特征识别并落 JSON。
       *
       * @param handle SSH 句柄
       * @param host   主机
       */
    private void collectSoftware(SshClients.Handle handle, MsServerHost host) {
          // 字段：pid %cpu rss(KB) etimes(s) comm args；--no-headers 去掉表头
        String out = execQuiet(handle, "ps -eo pid,pcpu,rss,etimes,comm,args --no-headers");
        if (StringUtils.isBlank(out)) {
            return;
        }
        List<SoftwareInstanceVO> list = new ArrayList<>();
        int seen = 0;
        for (String line : out.split("\\r?\\n")) {
            if (++seen > 500) {
                break;
            }
              // 前 5 列无空格，第 6 段为完整命令行（可含空格）
            String[] parts = line.trim().split("\\s+", 6);
            if (parts.length < 5 || !NumberUtils.isNumber(parts[0])) {
                continue;
            }
            int pid = NumberUtils.toInt(parts[0]);
            double cpu = NumberUtils.isDecimals(parts[1]) ? NumberUtils.toDouble(parts[1]) : 0;
            double rssKb = NumberUtils.isNumber(parts[2]) ? NumberUtils.toLong(parts[2]) : 0;
            long etimes = NumberUtils.isNumber(parts[3]) ? NumberUtils.toLong(parts[3]) : 0;
            String comm = parts[4];
            String args = parts.length > 5 ? parts[5] : "";
            String type = SoftwareCatalog.match(comm, args);
            if (type != null) {
                list.add(SoftwareCatalog.instance(type, pid, cpu, rssKb / 1024.0, etimes,
                        args.isBlank() ? comm : args));
            }
        }
        if (!list.isEmpty()) {
            try {
                host.setServerHostSoftware(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(list));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignore) {
                  // 序列化失败不阻断探测
            }
        }
    }

      /**
       * 保留两位小数。
       * @param v 原值
       * @return 结果
       */
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

      /**
       * 判断字符串是否为可解析的百分比数字（非空且为数字或小数）
       *
       * @param value 待判断字符串
       * @return 是否可解析
       */
    private boolean isRatio(String value) {
          // 使用率命令输出为小数（如 17.17），NumberUtils.isNumber 遇小数点即 false，
          // 必须用支持小数的 isDecimal 判定，否则三率永远不回写
        return StringUtils.isNotBlank(value) && NumberUtils.isDecimals(value);
    }

      /**
       * 将百分比字符串转为 Double（四舍五入保留 1 位小数），解析失败返回 null
       *
       * @param value 百分比字符串
       * @return Double 或 null
       */
    private Double toRatio(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(value.trim()) * 10.0) / 10.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

      /**
       * 静默执行 SSH 命令并返回输出（失败返回 null，不抛出异常）
       *
       * @param handle  SSH 连接句柄，不能为 null
       * @param command 待执行命令，不能为 null 或空
       * @return 命令标准输出；执行失败返回 null
       */
    private String execQuiet(SshClients.Handle handle, String command) {
        try {
            return handle.client().exec().command(command).executeAndGetOutput();
        } catch (Exception e) {
            log.debug("[detect] Linux 命令执行失败 {}: {}", command, e.getMessage());
            return null;
        }
    }
}