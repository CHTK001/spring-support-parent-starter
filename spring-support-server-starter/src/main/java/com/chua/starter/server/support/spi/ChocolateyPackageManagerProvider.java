package com.chua.starter.server.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.entity.SoftwarePackage;

import java.util.List;

/**
 * Chocolatey 包管理器 Provider（Windows 远程/非交互场景的通用实现）。
 * <p>
 * Chocolatey 专为自动化设计，可在 WinRM 非交互会话、SYSTEM/服务账户下正常工作，
 * 并兼容 Windows Server 2012 R2 等较老系统（choco 1.x 仅需 .NET 4.x）。
 * 搜索使用 {@code --limit-output} 输出机器可读的 {@code name|version} 管道格式，
 * 安装使用 {@code -y} 非交互并关闭进度噪音。
 * </p>
 * <p>
 * 当主机实际包管理器为 choco 时优先于 {@link WingetPackageManagerProvider} 命中
 * （order 更高）；winget 仅在未检测到 choco 的 Windows 上兜底。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "choco", order = 120)
public class ChocolateyPackageManagerProvider extends AbstractPackageManagerProvider {

    /**
     * choco 可执行文件全路径（默认安装位置）。WinRM 非交互会话下命令名发现不稳定，
     * 用 & 调用全路径最可靠；该路径无空格。
    */
    private static final String CHOCO = "& 'C:\\ProgramData\\chocolatey\\bin\\choco.exe'";

    @Override
    public String manager() {
        return "choco";
    }

    @Override
    public boolean supports(MsServerHost host) {
        if (host == null) {
            return false;
        }
        // 仅在明确探测到 choco 时命中，避免在未安装 choco 的机器上误路由
        return lower(host.getServerHostPackageManager()).contains("choco");
    }

    @Override
    public String searchCommand(String keyword) {
        // --limit-output(-r)：输出 name|version，便于稳定解析
        return CHOCO + " search " + keyword + " --limit-output --page-size=30";
    }

    @Override
    public String installCommand(String name) {
        // -y 非交互确认；--no-progress/--limit-output 抑制进度条等噪音
        return CHOCO + " install " + name + " -y --no-progress --limit-output";
    }

    @Override
    public List<SoftwarePackage> parse(String output, String keyword) {
        List<SoftwarePackage> list = new java.util.ArrayList<>();
        if (output == null) {
            return list;
        }
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            // --limit-output 下每个结果为 name|version[|...]，其余提示行不含 '|'，天然过滤
            if (!line.contains("|")) {
                continue;
            }
            String[] parts = line.split("\\|");
            String name = parts[0].trim();
            String version = parts.length >= 2 ? parts[1].trim() : null;
            // Chocolatey 搜索不直接返回简介，以包名占位保持字段非空
            add(list, name, version, name);
        }
        return list;
    }
}
