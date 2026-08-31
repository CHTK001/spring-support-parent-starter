package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.network.support.tshark.PacketParserService;
import com.chua.network.support.tshark.PacketRecord;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import com.chua.starter.monitor.support.mapper.MsTsharkPacketMapper;
import com.chua.starter.monitor.support.mapper.MsTsharkSessionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * TShark 网络抓包服务
 */
@Slf4j
@Service
public class TsharkCaptureService {

    private static final String TSHARK_BIN = System.getenv().getOrDefault("TSHARK_BIN", "tshark");
    private static final String CAPTURE_DIR = System.getenv().getOrDefault("TSHARK_CAPTURE_DIR", "/opt/spring-support/captures");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<Long, Process> running = new ConcurrentHashMap<>();
    private final MsTsharkSessionMapper sessionMapper;
    private final MsTsharkPacketMapper packetMapper;

    public TsharkCaptureService(MsTsharkSessionMapper sessionMapper, MsTsharkPacketMapper packetMapper) {
        this.sessionMapper = sessionMapper;
        this.packetMapper = packetMapper;
    }

    public ReturnResult<List<String>> listInterfaces() {
        try {
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BIN, "-D");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            List<String> ifaces = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split("\\.");
                    if (parts.length >= 2) {
                        String name = parts[1].trim().split("\\s+")[0];
                        ifaces.add(name);
                    } else {
                        ifaces.add(line.trim());
                    }
                }
            }
            p.waitFor(10L, TimeUnit.SECONDS);
            return ReturnResult.ok(ifaces);
        } catch (Exception e) {
            log.error("tshark list interfaces failed", e);
            return ReturnResult.error("无法列出网卡，请确认 tshark 已安装: " + e.getMessage());
        }
    }

    public ReturnResult<Boolean> checkTshark() {
        try {
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BIN, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < 300) {
                    sb.append(line).append('\n');
                }
            }
            boolean ok = p.waitFor(10L, TimeUnit.SECONDS) && sb.length() > 0;
            return ok ? ReturnResult.ok(true) : ReturnResult.error("tshark 不可用");
        } catch (Exception e) {
            return ReturnResult.error("tshark 不可用: " + e.getMessage());
        }
    }

    public ReturnResult<MsTsharkSession> startCapture(String name, String iface, String filter, Integer count, Integer timeout) {
        try {
            if (iface == null || iface.isBlank()) {
                return ReturnResult.error("请指定网卡");
            }
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            String fileName = UUID.randomUUID().toString().replace("-", "") + ".pcap";
            Path filePath = Paths.get(CAPTURE_DIR, fileName);
            List<String> cmd = new ArrayList<>();
            cmd.add(TSHARK_BIN);
            cmd.add("-i");
            cmd.add(iface);
            if (filter != null && !filter.isBlank()) {
                cmd.add("-f");
                cmd.add(filter);
            }
            if (count != null && count > 0) {
                cmd.add("-c");
                cmd.add(String.valueOf(count));
            }
            if (timeout != null && timeout > 0) {
                cmd.add("-a");
                cmd.add("duration:" + timeout);
            }
            cmd.add("-w");
            cmd.add(filePath.toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(CAPTURE_DIR + "/" + fileName + ".log"));
            Process proc = pb.start();
            MsTsharkSession session = new MsTsharkSession();
            session.setSessionName(name == null || name.isBlank() ? iface + " 抓包" : name);
            session.setCaptureInterface(iface);
            session.setCaptureFilter(filter);
            session.setCaptureStatus(1);
            session.setPacketCount(0);
            session.setFilePath(filePath.toString());
            session.setCaptureDuration(timeout);
            session.setStartTime(LocalDateTime.now());
            this.sessionMapper.insert(session);
            this.running.put(session.getId(), proc);
            this.asyncFinalize(session.getId(), proc, filePath);
            return ReturnResult.ok(session);
        } catch (Exception e) {
            log.error("start capture failed", e);
            return ReturnResult.error("启动抓包失败: " + e.getMessage());
        }
    }

    @Async
    public void asyncFinalize(Long sessionId, Process proc, Path filePath) {
        try {
            boolean finished = proc.waitFor(60L, TimeUnit.MINUTES);
            if (!finished) {
                proc.destroy();
                proc.waitFor(5L, TimeUnit.SECONDS);
            }
            this.parsePcapToDb(sessionId, filePath);
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setCaptureStatus(0);
                session.setEndTime(LocalDateTime.now());
                this.sessionMapper.updateById(session);
            }
        } catch (Exception e) {
            log.error("finalize capture failed, session={}", sessionId, e);
        } finally {
            this.running.remove(sessionId);
        }
    }

    public ReturnResult<Void> stopCapture(Long sessionId) {
        Process proc = this.running.get(sessionId);
        if (proc == null) {
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setCaptureStatus(0);
                session.setEndTime(LocalDateTime.now());
                this.sessionMapper.updateById(session);
            }
            return ReturnResult.success();
        }
        proc.destroy();
        try {
            proc.waitFor(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.running.remove(sessionId);
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setCaptureStatus(0);
            session.setEndTime(LocalDateTime.now());
            this.sessionMapper.updateById(session);
        }
        return ReturnResult.success();
    }

    /**
     * 解析 pcap 文件: 用 tshark 输出 JSON 数组, 遍历每个元素入库。
     */
    private void parsePcapToDb(Long sessionId, Path filePath) {
        try {
            if (!Files.exists(filePath) || Files.size(filePath) == 0L) {
                log.warn("capture file empty, session={}", sessionId);
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BIN, "-r", filePath.toString(), "-T", "json", "-E", "separator=,", "-x", "--no-duplicate-keys");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder(8192);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 0x100000)) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String jsonText = sb.toString();
            if (jsonText.isBlank()) {
                return;
            }
            JsonNode root = OBJECT_MAPPER.readTree(jsonText);
            int batch = 0;
            if (root.isArray()) {
                for (JsonNode node : root) {
                    this.savePacket(sessionId, node.toString());
                    batch++;
                }
            } else if (root.isObject()) {
                this.savePacket(sessionId, root.toString());
                batch++;
            }
            if (batch > 0) {
                MsTsharkSession session = this.sessionMapper.selectById(sessionId);
                if (session != null) {
                    session.setPacketCount(batch);
                    this.sessionMapper.updateById(session);
                }
            }
            p.waitFor(60L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("parse pcap failed, session={}", sessionId, e);
        }
    }

    private void savePacket(Long sessionId, String jsonLine) {
        try {
            PacketRecord rec = PacketParserService.parse(jsonLine);
            if (rec == null) {
                return;
            }
            MsTsharkPacket packet = new MsTsharkPacket();
            packet.setSessionId(sessionId);
            packet.setPacketTime(LocalDateTime.now());
            packet.setSourceIp(rec.sourceIp());
            packet.setDestIp(rec.destinationIp());
            packet.setSourcePort(rec.sourcePort());
            packet.setDestPort(rec.destinationPort());
            packet.setProtocol(rec.protocol());
            packet.setPacketLength(rec.length());
            packet.setPacketInfo(rec.info());
            packet.setRawData(rec.rawData());
            packet.setRestoredText(rec.restoredText());
            this.packetMapper.insert(packet);
        } catch (Exception e) {
            log.debug("skip packet parse error: {}", e.getMessage());
        }
    }

    public ReturnResult<IPage<MsTsharkSession>> pageSessions(int page, int size) {
        IPage<MsTsharkSession> p = this.sessionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MsTsharkSession>().orderByDesc(MsTsharkSession::getId));
        return ReturnResult.ok(p);
    }

    public ReturnResult<IPage<MsTsharkPacket>> pagePackets(Long sessionId, int page, int size) {
        IPage<MsTsharkPacket> p = this.packetMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId)
                        .orderByDesc(MsTsharkPacket::getId));
        return ReturnResult.ok(p);
    }

    public ReturnResult<MsTsharkPacket> getPacket(Long id) {
        MsTsharkPacket packet = this.packetMapper.selectById(id);
        return packet == null ? ReturnResult.error("数据包不存在: " + id) : ReturnResult.ok(packet);
    }

    public ReturnResult<List<Map<String, Object>>> protocolStats(Long sessionId) {
        List<MsTsharkPacket> packets = this.packetMapper.selectList(
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId));
        Map<String, int[]> counter = new LinkedHashMap<>();
        for (MsTsharkPacket p : packets) {
            String proto = (p.getProtocol() == null || p.getProtocol().isBlank()) ? "OTHER" : p.getProtocol();
            int[] c = counter.computeIfAbsent(proto, k -> new int[2]);
            c[0]++;
            c[1] += p.getPacketLength() != null ? p.getPacketLength() : 0;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counter.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("protocol", e.getKey());
            m.put("count", e.getValue()[0]);
            m.put("bytes", e.getValue()[1]);
            result.add(m);
        }
        return ReturnResult.ok(result);
    }

    public ReturnResult<List<Map<String, Object>>> topFlows(Long sessionId, int limit) {
        List<MsTsharkPacket> packets = this.packetMapper.selectList(
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId));
        Map<String, int[]> counter = new LinkedHashMap<>();
        for (MsTsharkPacket p : packets) {
            String src = p.getSourceIp() != null ? p.getSourceIp() : "?";
            String dst = p.getDestIp() != null ? p.getDestIp() : "?";
            String flow = src + ":" + p.getSourcePort() + " -> " + dst + ":" + p.getDestPort();
            int[] c = counter.computeIfAbsent(flow, k -> new int[2]);
            c[0]++;
            c[1] += p.getPacketLength() != null ? p.getPacketLength() : 0;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counter.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flow", e.getKey());
            m.put("count", e.getValue()[0]);
            m.put("bytes", e.getValue()[1]);
            result.add(m);
        }
        result.sort((a, b) -> (Integer) b.get("count") - (Integer) a.get("count"));
        return ReturnResult.ok(result.subList(0, Math.min(limit, result.size())));
    }

    public ReturnResult<Void> deleteSession(Long sessionId) {
        Process proc = this.running.get(sessionId);
        if (proc != null) {
            proc.destroy();
            this.running.remove(sessionId);
        }
        this.packetMapper.delete(new LambdaQueryWrapper<MsTsharkPacket>().eq(MsTsharkPacket::getSessionId, sessionId));
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        if (session != null && session.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(session.getFilePath()));
            } catch (Exception ignored) {
            }
        }
        this.sessionMapper.deleteById(sessionId);
        return ReturnResult.success();
    }
}
