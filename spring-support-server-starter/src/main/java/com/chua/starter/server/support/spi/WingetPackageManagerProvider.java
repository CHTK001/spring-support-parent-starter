package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.ArrayList;
import java.util.List;

/**
 * Winget 包管理器 Provider（Windows 10/11）
 * <p>
 * 搜索 {@code winget search}，结果为列对齐表格（Name / Id / Version / Source）。
 * 为保证 {@code winget install --id ... -e} 精确、非交互安装，软件包以 {@code Id} 作为
 * 唯一标识（name），友好名称 Name 放入 summary，Version 放入 version。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "winget", order = 110)
public class WingetPackageManagerProvider extends AbstractPackageManagerProvider {

    @Override
    public String manager() {
        return "winget";
    }

    @Override
    public boolean supports(MsServerHost host) {
        if (host == null) {
            return false;
        }
        return "WINDOWS".equalsIgnoreCase(host.getServerHostOsType())
                || lower(host.getServerHostPackageManager()).contains("winget");
    }

    @Override
    public String searchCommand(String keyword) {
        return "winget search " + keyword;
    }

    @Override
    public String installCommand(String name) {
        return "winget install --id " + name
                + " -e --accept-package-agreements --accept-source-agreements";
    }

    @Override
    public String installedCommand() {
        return "winget list --accept-source-agreements";
    }

    @Override
    public String uninstallCommand(String name) {
        return "winget uninstall --id " + name
                + " -e --accept-source-agreements --silent";
    }

    @Override
    public String upgradeCommand(String name) {
        return "winget upgrade --id " + name
                + " -e --accept-package-agreements --accept-source-agreements --silent";
    }

    @Override
    public List<SoftwarePackage> parse(String output, String keyword) {
        List<SoftwarePackage> list = new ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("-")) {
                continue;
            }
            String[] parts = line.split("\\s{2,}");
            if (parts.length < 3) {
                continue;
            }
            String displayName = parts[0];
            String id = parts[1];
            String version = parts[2];
            // 跳过表头（Name / Id / Version / Source）
            if ("Name".equalsIgnoreCase(displayName) && "Id".equalsIgnoreCase(id)) {
                continue;
            }
            add(list, id, version, displayName);
        }
        return list;
    }

    @Override
    public List<SoftwarePackage> parseInstalled(String output) {
        // winget list 同样为列对齐表格（Name / Id / Version / Source）
        return parseTable(output, 1, 2, 0, "Name", "Id", "Version");
    }
}
