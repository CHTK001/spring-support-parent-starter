// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.NumberUtils;
import com.chua.starter.monitor.support.model.IPv4ConfigVO;
import com.chua.starter.monitor.support.model.NetworkAdapterDetailVO;
import com.chua.starter.monitor.support.service.HostNetworkService;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.spi.SystemInfoProviderRouter;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
   * 主机网络服务实现：Windows 经 PowerShell（Get-NetAdapter / NetIPConfiguration /
   * NetIPAddress / DnsClient）读取与下发，统一以 -EncodedCommand 编码，
   * 兼容本机(cmd)、agent(cmd)、SSH/WinRM(已在 PowerShell 内) 各通道。
   *
   * @author CH
   * @since 4.0.0.42
   */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostNetworkServiceImpl implements HostNetworkService {

      /**
       * Windows 平台标识
       */
    private static final String WINDOWS = "WINDOWS";

      /**
       * 读取网卡信息的 PowerShell 脚本（{0}=网卡名/描述/索引）
       */
    private static final String PS_READ = """
            $want='%s'
            $ProgressPreference='SilentlyContinue'
            try{
              $ErrorActionPreference='Stop'
              $a=Get-NetAdapter | Where-Object { $_.Name -eq $want -or $_.InterfaceDescription -eq $want -or ("$($_.ifIndex)" -eq $want
                  ) } | Select-Object -First 1
              if($null -eq $a){ 'NOTFOUND'; exit }
              $ip=Get-NetIPConfiguration -InterfaceIndex $a.ifIndex
              $v4=@(Get-NetIPAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_
                  .PrefixOrigin -ne 'WellKnown' })
              $v6=@(Get-NetIPAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv6 -ErrorAction SilentlyContinue | Where-Object { $_
                  .PrefixOrigin -ne 'WellKnown' })
              $dns=@((Get-DnsClientServerAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv4).ServerAddresses)
              $gw=@()
              if($ip -and $ip.IPv4DefaultGateway){ $gw=@($ip.IPv4DefaultGateway.NextHop) }
              $first=$v4 | Select-Object -First 1
              $o=[ordered]@{
                name=$a.Name; ifIndex=$a.ifIndex; description=$a.InterfaceDescription;
                status=$a.Status; mac=$a.MacAddress; speed=$a.LinkSpeed; mtu=$a.MtuSize;
                dhcp=($null -ne $first -and $first.PrefixOrigin -eq 'Dhcp');
                ipv4=@($v4 | ForEach-Object { "$($_.IPAddress)/$($_.PrefixLength)" });
                ipv6=@($v6 | ForEach-Object { "$($_.IPAddress)/$($_.PrefixLength)" });
                gateway=$gw; dns=$dns
              }
              ConvertTo-Json -InputObject $o -Depth 6 -Compress
            }catch{ 'ERR: '+$_.Exception.Message }
            """;

      /**
       * 列出全部可见网卡的 PowerShell 脚本（输出 JSON 数组；单网卡时为对象，Java 侧归一）
       */
    private static final String PS_LIST = """
            try{
              $ErrorActionPreference='Stop'
              $ProgressPreference='SilentlyContinue'
              $list=@()
                /**
                 * in
                 * @param (Get-NetAdapter) 参数
                 * @return 返回值
                */
              foreach($a in (Get-NetAdapter)){
                $v4=@()
                try { $v4=@((Get-NetIPAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where
                     -Object { $_.PrefixOrigin -ne 'WellKnown' }).IPAddress) } catch { $v4=@() }
                $o=[ordered]@{
                  name=$a.Name; ifIndex=$a.ifIndex; description=$a.InterfaceDescription;
                  status=$a.Status; mac=$a.MacAddress; speed=$a.LinkSpeed; mtu=$a.MtuSize;
                  ipv4=$v4
                }
                $list+=$o
              }
              ConvertTo-Json -InputObject $list -Depth 6 -Compress
            }catch{ 'ERR: '+$_.Exception.Message }
            """;
      /**
       * 主机台账映射器
       */
    private final MsServerHostMapper hostMapper;

      /**
       * 采集通道路由器
       */
    private final SystemInfoProviderRouter providerRouter;

      /**
       * JSON 解析
       */
    private final ObjectMapper objectMapper;

    @Override
    public ReturnResult<NetworkAdapterDetailVO> adapterDetail(Long hostId, String adapterName) {
        JsonNode node;
        try {
            node = readAdapter(hostId, adapterName);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        NetworkAdapterDetailVO vo = new NetworkAdapterDetailVO();
        vo.setName(text(node, "name"));
        vo.setIfIndex(node.path("ifIndex").asInt(0));
        vo.setDescription(text(node, "description"));
        vo.setStatus(text(node, "status"));
        vo.setMacAddress(text(node, "mac"));
        vo.setLinkSpeed(text(node, "speed"));
        vo.setMtu(node.path("mtu").asInt(0));
        vo.setDhcpEnabled(node.path("dhcp").asBoolean(false));
        vo.setIpv4Addresses(toStringList(node.get("ipv4")));
        vo.setIpv6Addresses(toStringList(node.get("ipv6")));
        vo.setGateways(toStringList(node.get("gateway")));
        vo.setDnsServers(toStringList(node.get("dns")));
        return ReturnResult.ok(vo);
    }

    @Override
    public ReturnResult<List<NetworkAdapterDetailVO>> adapters(Long hostId) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (!WINDOWS.equalsIgnoreCase(trim(host.getServerHostOsType()))) {
            return ReturnResult.error("网卡图形化列表当前仅支持 Windows 主机");
        }
        SystemInfoProvider provider;
        try {
            provider = providerRouter.route(host);
        } catch (Exception e) {
            return ReturnResult.error("通道不可达: " + trim(e.getMessage()));
        }
        String out = runPs(provider, host, PS_LIST);
        if (out.startsWith("ERR:")) {
            return ReturnResult.error("读取网卡列表失败: " + out.substring(4).trim());
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(out));
            List<NetworkAdapterDetailVO> result = new ArrayList<>();
              // 单网卡时 ConvertTo-Json 输出对象，统一包成数组遍历
            if (node.isArray()) {
                for (JsonNode item : node) {
                    result.add(toLightVO(item));
                }
            } else if (node.isObject()) {
                result.add(toLightVO(node));
            }
            return ReturnResult.ok(result);
        } catch (Exception e) {
            return ReturnResult.error("解析网卡列表失败: " + trim(out));
        }
    }

      /**
       * 列表 JSON 节点转轻量网卡 VO。
       *
       * @param node 节点
       * @return VO
       */
    private static NetworkAdapterDetailVO toLightVO(JsonNode node) {
        NetworkAdapterDetailVO vo = new NetworkAdapterDetailVO();
        vo.setName(text(node, "name"));
        vo.setIfIndex(node.path("ifIndex").asInt(0));
        vo.setDescription(text(node, "description"));
        vo.setStatus(text(node, "status"));
        vo.setMacAddress(text(node, "mac"));
        vo.setLinkSpeed(text(node, "speed"));
        vo.setMtu(node.path("mtu").asInt(0));
        vo.setIpv4Addresses(toStringList(node.get("ipv4")));
        return vo;
    }

    @Override
    public ReturnResult<IPv4ConfigVO> ipv4(Long hostId, String adapterName) {
        JsonNode node;
        try {
            node = readAdapter(hostId, adapterName);
        } catch (IllegalStateException e) {
            return ReturnResult.error(e.getMessage());
        }
        IPv4ConfigVO vo = new IPv4ConfigVO();
        vo.setAdapterName(text(node, "name"));
        vo.setIfIndex(node.path("ifIndex").asInt(0));
        boolean dhcp = node.path("dhcp").asBoolean(false);
        vo.setDhcp(dhcp);
          // 首个 IPv4 地址拆出地址与前缀
        List<String> v4 = toStringList(node.get("ipv4"));
        if (!v4.isEmpty()) {
            String first = v4.get(0);
            int slash = first.indexOf('/');
            if (slash > 0) {
                vo.setAddress(first.substring(0, slash));
                vo.setPrefixLength(NumberUtils.toInt(first.substring(slash + 1), 24));
            } else {
                vo.setAddress(first);
            }
        }
        List<String> gw = toStringList(node.get("gateway"));
        if (!gw.isEmpty()) {
            vo.setGateway(gw.get(0));
        }
        List<String> dns = toStringList(node.get("dns"));
        if (!dns.isEmpty()) {
            vo.setDnsPreferred(dns.get(0));
            if (dns.size() > 1) {
                vo.setDnsAlternate(dns.get(1));
            }
              // 静态 IP 且有 DNS，视为手工 DNS；DHCP 下默认自动
            vo.setDnsDhcp(dhcp);
        } else {
            vo.setDnsDhcp(true);
        }
        return ReturnResult.ok(vo);
    }

    @Override
    public ReturnResult<Void> applyIpv4(Long hostId, IPv4ConfigVO config) {
        if (config == null) {
            return ReturnResult.error("配置不能为空");
        }
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("主机不存在: " + hostId);
        }
        if (!WINDOWS.equalsIgnoreCase(trim(host.getServerHostOsType()))) {
            return ReturnResult.error("IPv4 图形化配置当前仅支持 Windows 主机");
        }
        String adapterName = trim(config.getAdapterName());
        if (adapterName.isEmpty()) {
            return ReturnResult.error("缺少网卡名称");
        }
        boolean dhcp = Boolean.TRUE.equals(config.getDhcp());
        boolean dnsDhcp = Boolean.TRUE.equals(config.getDnsDhcp());
        StringBuilder ps = new StringBuilder();
        ps.append("$want='").append(psEscape(adapterName)).append("'\n");
        ps.append("try{\n $ErrorActionPreference='Stop'\n");
        ps.append(" $a=Get-NetAdapter | Where-Object { $_.Name -eq $want -or $_.InterfaceDescription -eq $want -or (\"$($_.ifIndex)\" -eq $w" +
            "ant) } | Select-Object -First 1\n");
        ps.append(" if($null -eq $a){ 'NOTFOUND'; exit }\n");
        ps.append(" $idx=$a.ifIndex\n");
        if (dhcp) {
            ps.append(" Set-NetIPInterface -InterfaceIndex $idx -AddressFamily IPv4 -Dhcp Enabled\n");
        } else {
            String addr = trim(config.getAddress());
            if (addr.isEmpty()) {
                return ReturnResult.error("静态方式下 IP 地址不能为空");
            }
            int prefix = config.getPrefixLength() == null ? 24 : NumberUtils.toInt(String.valueOf(config.getPrefixLength()), 24);
            if (prefix < 1 || prefix > 128) {
                return ReturnResult.error("子网前缀长度不合法(1-128)");
            }
            ps.append(" Get-NetRoute -InterfaceIndex $idx -AddressFamily IPv4 | Where-Object { $_.DestinationPrefix -eq '0.0.0.0/0' } | Remo" +
                "ve-NetRoute -Confirm:$false\n");
            ps.append(" @(Get-NetIPAddress -InterfaceIndex $idx -AddressFamily IPv4 | Where-Object { $_.PrefixOrigin -ne 'WellKnown' }) | Fo" +
                "rEach-Object { Remove-NetIPAddress -IPAddress $_.IPAddress -Confirm:$false }\n");
            ps.append(" $p=@{ InterfaceIndex=$idx; IPAddress='").append(psEscape(addr))
              .append("'; PrefixLength=").append(prefix).append("; AddressFamily='IPv4' }\n");
            String gw = trim(config.getGateway());
            if (!gw.isEmpty()) {
                ps.append(" $p.DefaultGateway='").append(psEscape(gw)).append("'\n");
            }
            ps.append(" New-NetIPAddress @p | Out-Null\n");
        }
        if (dnsDhcp) {
            ps.append(" Set-DnsClientServerAddress -InterfaceIndex $idx -AddressFamily IPv4 -ResetServerAddresses\n");
        } else {
            List<String> servers = new ArrayList<>();
            String dns1 = trim(config.getDnsPreferred());
            String dns2 = trim(config.getDnsAlternate());
            if (!dns1.isEmpty()) {
                servers.add(dns1);
            }
            if (!dns2.isEmpty()) {
                servers.add(dns2);
            }
            if (servers.isEmpty()) {
                return ReturnResult.error("手工 DNS 方式下至少需要一个 DNS 服务器，或改为自动获取 DNS");
            }
            ps.append(" $list=@(");
            for (int i = 0; i < servers.size(); i++) {
                if (i > 0) {
                    ps.append(',');
                }
                ps.append("'").append(psEscape(servers.get(i))).append("'");
            }
            ps.append(")\n Set-DnsClientServerAddress -InterfaceIndex $idx -AddressFamily IPv4 -ServerAddresses $list\n");
        }
        ps.append(" 'OK'\n");
        ps.append("}catch{ 'ERR: '+$_.Exception.Message }\n");

        SystemInfoProvider provider;
        try {
            provider = providerRouter.route(host);
        } catch (Exception e) {
            return ReturnResult.error("通道不可达: " + trim(e.getMessage()));
        }
        String out = runPs(provider, host, ps.toString());
        if (out.startsWith("OK")) {
            return ReturnResult.ok();
        }
        if (out.startsWith("NOTFOUND")) {
            return ReturnResult.error("未找到网卡: " + adapterName);
        }
        if (out.startsWith("ERR:")) {
            return ReturnResult.error("IPv4 下发失败: " + out.substring(4).trim());
        }
        return ReturnResult.error("IPv4 下发无明确结果（命令可能未生效）: " + out);
    }

      /**
       * 读取网卡原始 JSON（含平台校验）。
       *
       * @param hostId      主机 ID
       * @param adapterName 网卡名
       * @return JSON 节点
       * @throws IllegalStateException 主机不存在/非 Windows/通道异常
       */
    private JsonNode readAdapter(Long hostId, String adapterName) {
        MsServerHost host = hostMapper.selectById(hostId);
        if (host == null) {
            throw new IllegalStateException("主机不存在: " + hostId);
        }
        if (!WINDOWS.equalsIgnoreCase(trim(host.getServerHostOsType()))) {
            throw new IllegalStateException("网卡详情图形化查看当前仅支持 Windows 主机");
        }
        String name = trim(adapterName);
        if (name.isEmpty()) {
            throw new IllegalStateException("缺少网卡名称");
        }
        SystemInfoProvider provider;
        try {
            provider = providerRouter.route(host);
        } catch (Exception e) {
            throw new IllegalStateException("通道不可达: " + trim(e.getMessage()));
        }
        String body = String.format(PS_READ, psEscape(name));
        String out = runPs(provider, host, body);
        if (out.startsWith("NOTFOUND")) {
            throw new IllegalStateException("未找到网卡: " + name);
        }
        if (out.startsWith("ERR:")) {
            throw new IllegalStateException("读取网卡失败: " + out.substring(4).trim());
        }
        try {
            return objectMapper.readTree(extractJson(out));
        } catch (Exception e) {
            throw new IllegalStateException("解析网卡信息失败: " + trim(out));
        }
    }

      /**
       * 以 -EncodedCommand 方式执行 PowerShell 脚本（各通道通用）。
       *
       * @param provider 采集通道
       * @param host     目标主机
       * @param psBody   PowerShell 脚本体
       * @return 标准输出（已 trim）
       */
    private String runPs(SystemInfoProvider provider, MsServerHost host, String psBody) {
        String encoded = Base64.getEncoder().encodeToString(psBody.getBytes(StandardCharsets.UTF_16LE));
        String cli = "powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand " + encoded;
        String out;
        try {
            out = provider.exec(host, cli);
        } catch (RuntimeException e) {
              // 通道执行失败（主机下线/SSH 拒绝/凭据错误）一律走 ERR: 业务错误口径，
              // 与其余调用点保持一致，避免 IllegalStateException 冒泡成 HTTP 409。
            return "ERR: " + trim(e.getMessage());
        }
        return out == null ? "" : out.trim();
    }

      /**
       * PowerShell 单引号字符串转义（单引号翻倍）。
       *
       * @param value 原值
       * @return 转义后值
       */
      /**
       * 从可能被 CLIXML/进度记录污染的输出中提取 JSON 片段
       * （首个 '[' 或 '{' 到末个 ']' 或 '}'）。
       *
       * @param out 原始输出
       * @return JSON 子串
       */
    private static String extractJson(String out) {
        if (out == null) {
            return "";
        }
        int start = -1;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c == '[' || c == '{') {
                start = i;
                break;
            }
        }
        int end = Math.max(out.lastIndexOf(']'), out.lastIndexOf('}'));
        return (start >= 0 && end >= start) ? out.substring(start, end + 1) : out.trim();
    }

      /**
       * ps Escape
       * @param value 字符串
       * @return 返回值
      */
    private static String psEscape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

      /**
       * 读取 JsonNode 文本并归一。
       *
       * @param node 节点
       * @param key  字段
       * @return 文本
       */
    private static String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v == null || v.isNull() ? "" : v.asText("");
    }

      /**
       * JSON 数组节点转字符串列表。
       *
       * @param array 数组节点
       * @return 列表
       */
    private static List<String> toStringList(JsonNode array) {
        List<String> list = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                if (item != null && !item.isNull()) {
                    list.add(item.asText(""));
                }
            }
        }
        return list;
    }

      /**
       * null 安全 trim。
       *
       * @param value 原值
       * @return trim 后值
       */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}