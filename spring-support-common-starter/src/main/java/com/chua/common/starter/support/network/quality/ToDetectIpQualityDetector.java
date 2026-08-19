package com.chua.common.starter.support.network.quality;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

/**
 * ToDetect IP 质量检测器实现
 *
 * @since 4.0.0.42
 * @author CH
 */
public class ToDetectIpQualityDetector extends AbstractHttpIpQualityDetector {

    /** BaseURL */
    private final String baseUrl;

    public ToDetectIpQualityDetector() {
        this("https://www.todetect.cn/ip");
    }

    public ToDetectIpQualityDetector(String baseUrl) {
        super();
        this.baseUrl = baseUrl;
    }

    @Override
    protected String buildUrl(String ip) {
        return baseUrl + "/" + ip;
    }

    @Override
    protected IpQuality parseResult(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        IpQuality q = new IpQuality();
        q.setIp(optString(root, "ip"));
        q.setProxy(getBool(root, "proxy"));
        q.setVpn(getBool(root, "vpn"));
        q.setTor(getBool(root, "tor"));
        q.setCloudService(getBool(root, "cloud"));
        q.setDataCenter(getBool(root, "datacenter"));
        q.setHighRiskProxy(getBool(root, "high_risk_proxy"));
        q.setRiskScore(getInt(root, "risk_score"));
        q.setRiskLevel(optString(root, "risk_level"));
        q.setIsp(optString(root, "isp"));
        q.setCountry(optString(root, "country"));
        q.setRegion(optString(root, "region"));
        q.setCity(optString(root, "city"));
        q.setLongitude(getDouble(root, "longitude"));
        q.setLatitude(getDouble(root, "latitude"));
        q.setUsageType(optString(root, "usage_type"));
        q.setAsn(optString(root, "asn"));
        q.setOrganisation(optString(root, "organisation"));
        return q;
    }

    private boolean getBool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return false;
        }
        return v.booleanValue();
    }

    private int getInt(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return 0;
        }
        return v.intValue();
    }

    private double getDouble(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return 0.0;
        }
        return v.doubleValue();
    }
}