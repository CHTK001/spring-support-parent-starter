package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.List;

/**
 * YUM 包管理器 Provider（CentOS 7 / RHEL 7 等老版本）
 * <p>
 * 搜索 {@code yum search}，安装 {@code sudo yum install -y name}，输出按 RHEL 系格式解析。
 * 8 及以上版本由 {@link DnfPackageManagerProvider} 处理。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "yum", order = 120)
public class YumPackageManagerProvider extends AbstractPackageManagerProvider {

    @Override
    public String manager() {
        return "yum";
    }

    @Override
    public boolean supports(MsServerHost host) {
        if (host == null || !isLinux(host)) {
            return false;
        }
        String pm = lower(host.getServerHostPackageManager());
        String version = lower(host.getServerHostOsVersion());
        if (pm.contains("yum")) {
            return true;
        }
        if (pm.contains("dnf") || pm.contains("apt")) {
            return false;
        }
        // 老版本 yum 系：CentOS 7 / RHEL 7（排除 Stream 与 8/9，它们走 dnf）
        if (version.contains("centos") && !containsAny(version, "stream", "linux 8", "linux 9")) {
            return true;
        }
        return containsAny(version, "red hat enterprise linux server 7",
                "red hat enterprise linux 7", "rhel 7", "centos linux 7");
    }

    @Override
    public String searchCommand(String keyword) {
        return "yum search " + keyword;
    }

    @Override
    public String installCommand(String name) {
        return "sudo yum install -y " + name;
    }

    @Override
    public String installedCommand() {
        return "yum list installed";
    }

    @Override
    public String uninstallCommand(String name) {
        return "sudo yum remove -y " + name;
    }

    @Override
    public String upgradeCommand(String name) {
        return "sudo yum update -y " + name;
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
