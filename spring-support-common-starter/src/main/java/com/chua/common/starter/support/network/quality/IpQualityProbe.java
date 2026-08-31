package com.chua.common.starter.support.network.quality;

import com.chua.common.support.utils.CommandLine;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * IP2Proxy LITE 离线 IP 质量检测示例
 *
 * @author CH
 */
@Slf4j
public class IpQualityProbe {

    /**
     * 默认 IP2Proxy BIN 数据库路径
     */
    private static final String DEFAULT_BIN_PATH = System.getProperty("ip2proxy.bin.path", "IP2PROXY-LITE-PX2.BIN");

    /**
     * 默认测试 IP 列表
     */
    private static final String DEFAULT_TEST_IPS = "1.2.3.4,5.6.7.8,8.8.8.8,1.1.1.1";

    /** Main */
    public static void main(String[] args) throws Exception {
        CommandLine cli = CommandLine.parse(args)
                .program("IpQualityProbe")
                .register("bin", "b", "IP2Proxy BIN 数据库文件路径", DEFAULT_BIN_PATH)
                .register("ips", "i", "待检测的 IP 列表（逗号分隔）", DEFAULT_TEST_IPS)
                .register("help", "h", "显示帮助信息");
        if (cli.isHelp()) {
            cli.help();
            return;
        }

        String binPath = cli.get("bin", DEFAULT_BIN_PATH);
        String ips = cli.get("ips", DEFAULT_TEST_IPS);
        IpQualityProbe example = new IpQualityProbe();

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
            log.info("IP: {}", ip);
            log.info("  国家代码: {}", q.getCountry());
            log.info("  省份: {}", q.getRegion());
            log.info("  城市: {}", q.getCity());
            log.info("  运营商: {}", q.getIsp());
            log.info("  代理: {}", q.isProxy());
            log.info("  VPN: {}", q.isVpn());
            log.info("  Tor: {}", q.isTor());
            log.info("  数据中心: {}", q.isDataCenter());
            log.info("  云服务: {}", q.isCloudService());
            log.info("  使用类型: {}", q.getUsageType());
            log.info("");
        }
    }
}
