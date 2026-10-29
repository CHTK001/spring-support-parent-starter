package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.List;

/**
 * DNF 包管理器 Provider（Fedora / CentOS 8+ / RHEL 8+ / Rocky / AlmaLinux）
 * <p>
 * 搜索 {@code dnf search}，安装 {@code sudo dnf install -y name}，输出按 RHEL 系格式解析。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "dnf", order = 130)
public class DnfPackageManagerProvider extends AbstractPackageManagerProvider {

    @Override
    public String manager() {
        return "dnf";
    }

    @Override
    public boolean supports(MsServerHost host) {
        if (host == null || !isLinux(host)) {
            return false;
        }
        String pm = lower(host.getServerHostPackageManager());
        String version = lower(host.getServerHostOsVersion());
        if (pm.contains("dnf")) {
            return true;
        }
        if (pm.contains("apt") || pm.contains("yum")) {
            return false;
        }
        // dnf 系发行版特征（含 CentOS Stream / 8 及以上）
        return containsAny(version, "fedora", "rocky", "almalinux", "centos stream",
                "centos linux 8", "centos linux 9", "red hat enterprise linux 8",
                "red hat enterprise linux 9", "rhel 8", "rhel 9");
    }

    @Override
    public String searchCommand(String keyword) {
        return "dnf search " + keyword;
    }

    @Override
    public String installCommand(String name) {
        return "sudo dnf install -y " + name;
    }

    @Override
    public String installedCommand() {
        return "dnf list installed";
    }

    @Override
    public String uninstallCommand(String name) {
        return "sudo dnf remove -y " + name;
    }

    @Override
    public String upgradeCommand(String name) {
        return "sudo dnf upgrade -y " + name;
    }

    @Override
    public String upgradableCommand() {
        // 有可用更新时退出码为 100，业务层只看输出
        return "dnf -q check-update 2>/dev/null || true";
    }

    @Override
    public String dependenciesCommand(String name) {
        return "dnf repoquery --requires " + name + " 2>/dev/null";
    }

    @Override
    public List<SoftwarePackage> parseUpgradable(String output) {
        return parseRhelInstalled(output);
    }

    @Override
    public List<String> parseDependencies(String output) {
        List<String> list = new java.util.ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            // rpm 能力型依赖（libc.so.6()(64bit)）不是可读包名，跳过
            if (line.isEmpty() || line.contains("(") || line.startsWith("/")) {
                continue;
            }
            if (!list.contains(line)) {
                list.add(line);
            }
        }
        return list;
    }

    @Override
    public List<SoftwarePackage> parse(String output, String keyword) {
        return parseRhelSearch(output);
    }

    @Override
    public List<SoftwarePackage> parseInstalled(String output) {
        return parseRhelInstalled(output);
    }
}
