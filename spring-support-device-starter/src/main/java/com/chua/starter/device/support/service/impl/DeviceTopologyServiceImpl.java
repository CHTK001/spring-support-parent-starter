package com.chua.starter.device.support.service.impl;

import com.chua.starter.device.support.entity.DeviceChannel;
import com.chua.starter.device.support.entity.DeviceInfo;
import com.chua.starter.device.support.model.DeviceTopologyEdge;
import com.chua.starter.device.support.model.DeviceTopologyNode;
import com.chua.starter.device.support.model.DeviceTopologyResult;
import com.chua.starter.device.support.service.DeviceChannelService;
import com.chua.starter.device.support.service.DeviceService;
import com.chua.starter.device.support.service.DeviceTopologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机房拓扑服务实现
 *
 * <p>结构：楼层/区域（area）→ 网段（subnet）→ 设备（device）→ 通道（channel）
 * （通道只在 NVR 等多通道设备时出现，作为设备的子节点）</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTopologyServiceImpl implements DeviceTopologyService {

    /** 无 IP 的设备不拆网段，直接挂在区域下 */
    private static final String DEFAULT_AREA = "未分组";

    /** 节点尺寸（直径）：area < subnet < device < channel */
    private static final int AREA_SIZE = 52;
    private static final int SUBNET_SIZE = 40;
    private static final int DEVICE_SIZE = 30;
    private static final int CHANNEL_SIZE = 22;

    private final DeviceService deviceService;
    private final DeviceChannelService channelService;

    @Override
    public DeviceTopologyResult topology() {
        List<DeviceInfo> devices = deviceService.listDevices(null);
        List<DeviceTopologyNode> nodes = new ArrayList<>();
        List<DeviceTopologyEdge> edges = new ArrayList<>();
        // 区域 id → 节点（同一区域只建一个 area 节点）
        Map<String, DeviceTopologyNode> areaNodes = new LinkedHashMap<>(devices.size() / 2 + 1);
        // 网段 id → 节点（同区同网段的多台设备共享一节点，按区 + 网段二段隔离）
        Map<String, DeviceTopologyNode> subnetNodes = new LinkedHashMap<>(devices.size() / 2 + 1);
        for (DeviceInfo device : devices) {
            // 区域：设备未写区域时归入“未分组”
            String areaName = StringUtils.hasText(device.getDeviceInfoArea())
                    ? device.getDeviceInfoArea()
                    : DEFAULT_AREA;
            String areaId = "area:" + areaName.replace(":", "_");
            // 同区域的子网/设备/通道全部注入同一气泡区域，气泡标签 = 区域名（area 节点本身也是成员）
            String areaGroup = areaName;
            DeviceTopologyNode areaNode = areaNodes.get(areaId);
            if (areaNode == null) {
                areaNode = new DeviceTopologyNode(areaId, areaName, areaGroup, "rect", AREA_SIZE, null);
                areaNodes.put(areaId, areaNode);
                nodes.add(areaNode);
            }
            // 网段：未解析 IP 的设备挂在区域下；同区同网段共享一节点
            String cidr = cidrOf(device.getDeviceInfoIp());
            String subnetId = subnetIdOf(areaGroup, cidr);
            DeviceTopologyNode subnetNode = subnetNodes.get(subnetId);
            if (subnetNode == null) {
                subnetNode = subnetNode(subnetId, cidr, areaGroup);
                subnetNodes.put(subnetId, subnetNode);
                nodes.add(subnetNode);
            }
            DeviceTopologyNode deviceNode = new DeviceTopologyNode(
                    "device:" + device.getDeviceInfoId(),
                    device.getDeviceInfoName(),
                    areaGroup,
                    "rect",
                    DEVICE_SIZE,
                    device.getDeviceInfoOnline());
            nodes.add(deviceNode);
            if (subnetId == null) {
                // 没有网段：设备挂在区域下
                edges.add(new DeviceTopologyEdge(areaId, deviceNode.getId()));
            } else {
                // 有网段：区域 -> 网段 -> 设备
                edges.add(new DeviceTopologyEdge(areaId, subnetNode.getId()));
                edges.add(new DeviceTopologyEdge(subnetNode.getId(), deviceNode.getId()));
            }
            // 通道（NVR 的子相机）作为设备的子节点
            for (DeviceChannel channel : channelService.listChannels(device.getDeviceInfoId())) {
                // 通道的联机态随设备（NVR 下挂的通道相机）
                DeviceTopologyNode channelNode = new DeviceTopologyNode(
                        "channel:" + channel.getDeviceChannelId(),
                        channel.getDeviceChannelName(),
                        areaGroup,
                        "circle",
                        CHANNEL_SIZE, device.getDeviceInfoOnline());
                nodes.add(channelNode);
                edges.add(new DeviceTopologyEdge(deviceNode.getId(), channelNode.getId()));
            }
        }
        // 排序：先分组（到面）再按设备和通道的 xml 顺序
        return new DeviceTopologyResult(nodes, edges);
    }

    /**
     * 设备 IP 对应的 /24 网段（例如 192.168.1.0/24）；无法解析时返回 null。
     *
     * <p>IP 带掩码（1.2.3.4/24）或只写 IP（按 /24 归网段）都折回网段地址，
     * 同网段设备共享网段节点，避免 1 设备 1 段（100 台设备 100 个网段）。</p>
     */
    private static String cidrOf(String ip) {
        if (ip == null || ip.contains("-")) return null;
        // 截断 "1.2.3.4/24" 里 IP 部分
        String addr = ip.split("/")[0].trim();
        String[] octets = addr.split("\\.");
        if (octets.length != 4) return null;
        for (String octet : octets) {
            if (octet.isEmpty()) return null;
            int value;
            try {
                value = Integer.parseInt(octet);
            } catch (NumberFormatException e) {
                return null;
            }
            if (value < 0 || value > 255) return null;
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + ".0/24";
    }

    /** 网段节点 id（按区 + 网段二段隔离，避免不同区的同网段撞 id） */
    private static String subnetIdOf(String areaGroup, String cidr) {
        if (cidr == null) return null;
        return "subnet:" + areaGroup.replace(":", "_") + ":" + cidr;
    }

    private static DeviceTopologyNode subnetNode(String subnetId, String cidr, String areaGroup) {
        return new DeviceTopologyNode(
                subnetId,
                cidr,
                areaGroup,
                "circle",
                SUBNET_SIZE);
    }
}
