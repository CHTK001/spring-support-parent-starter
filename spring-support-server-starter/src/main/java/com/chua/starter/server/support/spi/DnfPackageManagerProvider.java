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
    public List<SoftwarePackage> parse(String output, String keyword) {
        return parseRhelSearch(output);
    }
}
