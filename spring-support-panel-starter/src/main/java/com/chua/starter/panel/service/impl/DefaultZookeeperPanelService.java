package com.chua.starter.panel.service.impl;

import com.chua.starter.panel.service.ZookeeperPanelService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认 ZooKeeper 面板服务（基于 Curator）。
 *
 * @author CH
 */
public class DefaultZookeeperPanelService implements ZookeeperPanelService {

    private final Map<String, CuratorFramework> clientMap = new ConcurrentHashMap<>();

    @Override
    public String open(String host, int port, int sessionTimeoutMs) {
        String connectionId = UUID.randomUUID().toString();
        int timeout = sessionTimeoutMs > 0 ? sessionTimeoutMs : 30000;
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(host + ":" + port)
                .sessionTimeoutMs(timeout)
                .connectionTimeoutMs(10000)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        try {
            client.blockUntilConnected(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.close();
            throw new IllegalStateException("ZooKeeper 连接超时: " + host + ":" + port);
        }
        clientMap.put(connectionId, client);
        return connectionId;
    }

    @Override
    public void close(String connectionId) {
        CuratorFramework client = clientMap.remove(connectionId);
        if (client != null) {
            client.close();
        }
    }

    @Override
    public List<String> ls(String connectionId, String path) {
        try {
            return getClient(connectionId).getChildren().forPath(normalizePath(path));
        } catch (Exception e) {
            throw new RuntimeException("ls 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    @Override
    public String get(String connectionId, String path) {
        try {
            byte[] data = getClient(connectionId).getData().forPath(normalizePath(path));
            if (data == null || data.length == 0) return "";
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("get 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    @Override
    public void set(String connectionId, String path, String data) {
        try {
            byte[] bytes = data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8);
            getClient(connectionId).setData().forPath(normalizePath(path), bytes);
        } catch (Exception e) {
            throw new RuntimeException("set 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String connectionId, String path, boolean recursive) {
        try {
            String p = normalizePath(path);
            if (recursive) {
                getClient(connectionId).delete().deletingChildrenIfNeeded().forPath(p);
            } else {
                getClient(connectionId).delete().forPath(p);
            }
        } catch (Exception e) {
            throw new RuntimeException("delete 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> stat(String connectionId, String path) {
        try {
            Stat stat = new Stat();
            getClient(connectionId).getData().storingStatIn(stat).forPath(normalizePath(path));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("czxid", stat.getCzxid());
            result.put("mzxid", stat.getMzxid());
            result.put("ctime", stat.getCtime());
            result.put("mtime", stat.getMtime());
            result.put("version", stat.getVersion());
            result.put("cversion", stat.getCversion());
            result.put("aversion", stat.getAversion());
            result.put("dataLength", stat.getDataLength());
            result.put("numChildren", stat.getNumChildren());
            result.put("ephemeralOwner", stat.getEphemeralOwner());
            result.put("pzxid", stat.getPzxid());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("stat 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String connectionId, String path) {
        try {
            return getClient(connectionId).checkExists().forPath(normalizePath(path)) != null;
        } catch (Exception e) {
            throw new RuntimeException("exists 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    @Override
    public String create(String connectionId, String path, String data, String mode) {
        try {
            byte[] bytes = data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8);
            CreateMode createMode = parseCreateMode(mode);
            return getClient(connectionId).create()
                    .creatingParentsIfNeeded()
                    .withMode(createMode)
                    .forPath(normalizePath(path), bytes);
        } catch (Exception e) {
            throw new RuntimeException("create 失败: " + path + " - " + e.getMessage(), e);
        }
    }

    private CuratorFramework getClient(String connectionId) {
        CuratorFramework client = clientMap.get(connectionId);
        if (client == null) throw new IllegalStateException("ZooKeeper 连接不存在: " + connectionId);
        return client;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    private CreateMode parseCreateMode(String mode) {
        if (mode == null) return CreateMode.PERSISTENT;
        return switch (mode.toUpperCase()) {
            case "EPHEMERAL" -> CreateMode.EPHEMERAL;
            case "PERSISTENT_SEQUENTIAL" -> CreateMode.PERSISTENT_SEQUENTIAL;
            case "EPHEMERAL_SEQUENTIAL" -> CreateMode.EPHEMERAL_SEQUENTIAL;
            default -> CreateMode.PERSISTENT;
        };
    }
}
