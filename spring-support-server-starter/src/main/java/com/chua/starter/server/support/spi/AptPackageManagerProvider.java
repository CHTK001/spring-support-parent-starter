package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.ArrayList;
import java.util.List;

/**
 * APT 包管理器 Provider（Debian / Ubuntu 等）
 * <p>
 * 搜索使用 {@code apt-cache search}，输出形如 {@code name - summary}；
 * 安装使用 {@code sudo apt-get install -y name}（依赖目标用户具备免密 sudo）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "apt", order = 110)
public class AptPackageManagerProvider extends AbstractPackageManagerProvider {

    @Override
    public String manager() {
        return "apt";
    }

    @Override
    public boolean supports(MsServerHost host) {
        if (host == null || !isLinux(host)) {
            return false;
        }
        String pm = lower(host.getServerHostPackageManager());
        String version = lower(host.getServerHostOsVersion());
        if (pm.contains("apt")) {
            return true;
        }
        if (pm.contains("dnf") || pm.contains("yum")) {
            return false;
        }
        if (containsAny(version, "ubuntu", "debian", "deepin", "kali", "raspbian", "linux mint", "uos")) {
            return true;
        }
        if (containsAny(version, "centos", "red hat", "rocky", "almalinux", "fedora", "rhel")) {
            return false;
        }
        // Linux 但包管理器/版本无法判定时，apt 作为最终兜底（最常见的云镜像）
        return pm.isBlank() || pm.equals("unknown");
    }

    @Override
    public String searchCommand(String keyword) {
        return "apt-cache search " + keyword;
    }

    @Override
    public String installCommand(String name) {
        return "sudo apt-get install -y " + name;
    }

    @Override
    public List<SoftwarePackage> parse(String output, String keyword) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            int dash = line.indexOf(" - ");
            if (dash <= 0) {
                continue;
            }
            String name = line.substring(0, dash).trim();
            String summary = line.substring(dash + 3).trim();
            if (name.contains(" ")) {
                name = name.split("\\s+")[0];
            }
            add(list, name, null, summary);
        }
        return list;
    }
}
