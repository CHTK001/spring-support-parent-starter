package com.chua.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 指标上报线程：先向平台注册，之后每 10 秒推送一次指标。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class MetricsPusher implements Runnable {

    /** 平台地址 */
    private final String serverUrl;
    /** 注册令牌 */
    private final String token;
    /** 系统信息采集器 */
    private final AgentSystemInfo sysInfo;
    /** HTTP 客户端（连接超时 5s） */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /**
     * 构造上报线程。
     *
     * @param serverUrl 平台地址
     * @param token     注册令牌
     * @param sysInfo   系统信息采集器
     */
    public MetricsPusher(String serverUrl, String token, AgentSystemInfo sysInfo) {
        this.serverUrl = serverUrl;
        this.token = token;
        this.sysInfo = sysInfo;
    }

    /**
     * 线程主体：注册成功后进入 10 秒间隔的指标推送循环，被中断时退出。
     */
    @Override
    public void run() {
        // 先向平台注册
        try {
            String info = sysInfo.info();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/ms/agent/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(info))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Agent] Registered with platform");
        } catch (Exception e) {
            System.err.println("[Agent] Register failed: " + e.getMessage());
        }

        // 指标推送循环
        while (true) {
            try {
                Thread.sleep(10_000);
                String metrics = sysInfo.metrics();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/ms/agent/metrics"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(metrics))
                        .build();
                http.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("[Agent] Push failed: " + e.getMessage());
            }
        }
    }
}
