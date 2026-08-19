package com.chua.common.starter.support.network.quality;

import com.chua.common.support.utils.CommandLine;

import java.nio.file.Path;

/**
 * IP2Proxy LITE 离线 IP 质量检测示例
 *
 * @author CH
 */
public class IpQualityExample {

    /**
     * 默认 IP2Proxy BIN 数据库路径
     */
    private static final String DEFAULT_BIN_PATH = "G:\\work\\utils-support-resource-parent\\utils-support-resource-ip2proxy-starter\\src\\main\\resources\\IP2PROXY-LITE-PX2.BIN";

    /**
     * 默认测试 IP 列表
     */
    private static final String DEFAULT_TEST_IPS = "1.2.3.4,5.6.7.8,8.8.8.8,1.1.1.1";

    /** Main */
    public static void main(String[] args) throws Exception {
        CommandLine cli = CommandLine.parse(args)
                .program("IpQualityExample")
                .register("bin", "b", "IP2Proxy BIN 数据库文件路径", DEFAULT_BIN_PATH)
                .register("ips", "i", "待检测的 IP 列表（逗号分隔）", DEFAULT_TEST_IPS)
                .register("help", "h", "显示帮助信息");
        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String binPath = cli.get("bin", DEFAULT_BIN_PATH);
        String ips = cli.get("ips", DEFAULT_TEST_IPS);
        IpQualityExample example = new IpQualityExample();

        if (cli.has("ips")) {
            example.runWithIps(binPath, ips.split(","));
        } else {
            example.runWithDefaults();
        }
    }

    /** 运行WithDefaults */
    private void runWithDefaults() throws Exception {
        runWithIps(DEFAULT_BIN_PATH, DEFAULT_TEST_IPS.split(","));
    }

    /** 运行WithIps */
    private void runWithIps(String binPath, String[] testIps) throws Exception {
        IP2ProxyLiteIpQualityDetector detector = new IP2ProxyLiteIpQualityDetector(Path.of(binPath));
        for (String ip : testIps) {
            IpQuality q = detector.detect(ip.trim());
            System.out.println("IP: " + ip);
            System.out.println("  国家代码: " + q.getCountry());
            System.out.println("  省份: " + q.getRegion());
            System.out.println("  城市: " + q.getCity());
            System.out.println("  运营商: " + q.getIsp());
            System.out.println("  代理: " + q.isProxy());
            System.out.println("  VPN: " + q.isVpn());
            System.out.println("  Tor: " + q.isTor());
            System.out.println("  数据中心: " + q.isDataCenter());
            System.out.println("  云服务: " + q.isCloudService());
            System.out.println("  使用类型: " + q.getUsageType());
            System.out.println();
        }
    }
}
