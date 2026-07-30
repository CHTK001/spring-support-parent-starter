package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.KafkaPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Kafka 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/kafka")
public class PanelKafkaController {

    private final KafkaPanelService kafkaPanelService;

    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 9092)));
        String username = body.containsKey("username") ? String.valueOf(body.get("username")) : null;
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        String protocol = body.containsKey("protocol") ? String.valueOf(body.get("protocol")) : null;
        try {
            return ReturnResult.ok(kafkaPanelService.open(host, port, username, password, protocol));
        } catch (Exception e) {
            return ReturnResult.error("Kafka 连接失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        kafkaPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    @GetMapping("/{connectionId}/cluster-health")
    public ReturnResult<Map<String, Object>> clusterHealth(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(kafkaPanelService.clusterHealth(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询集群健康信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/brokers")
    public ReturnResult<List<Map<String, Object>>> listBrokers(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(kafkaPanelService.listBrokers(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询 Broker 列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/topics")
    public ReturnResult<List<Map<String, Object>>> listTopics(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(kafkaPanelService.listTopics(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询 Topic 列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/topics")
    public ReturnResult<Boolean> createTopic(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String topicName = String.valueOf(body.getOrDefault("topicName", ""));
        int numPartitions = Integer.parseInt(String.valueOf(body.getOrDefault("numPartitions", 1)));
        short replicationFactor = Short.parseShort(String.valueOf(body.getOrDefault("replicationFactor", 1)));
        try {
            return ReturnResult.ok(kafkaPanelService.createTopic(connectionId, topicName, numPartitions, replicationFactor));
        } catch (Exception e) {
            return ReturnResult.error("创建 Topic 失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/topics")
    public ReturnResult<Boolean> deleteTopic(
            @PathVariable String connectionId,
            @RequestParam String topicName) {
        try {
            return ReturnResult.ok(kafkaPanelService.deleteTopic(connectionId, topicName));
        } catch (Exception e) {
            return ReturnResult.error("删除 Topic 失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/topics/{topicName}")
    public ReturnResult<Map<String, Object>> topicDetail(
            @PathVariable String connectionId,
            @PathVariable String topicName) {
        try {
            return ReturnResult.ok(kafkaPanelService.topicDetail(connectionId, topicName));
        } catch (Exception e) {
            return ReturnResult.error("查询 Topic 详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/messages")
    public ReturnResult<List<Map<String, Object>>> consumeMessages(
            @PathVariable String connectionId,
            @RequestParam String topicName,
            @RequestParam(defaultValue = "-1") int partition,
            @RequestParam(defaultValue = "-1") long offset,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ReturnResult.ok(kafkaPanelService.consumeMessages(connectionId, topicName, partition, offset, limit));
        } catch (Exception e) {
            return ReturnResult.error("消费消息失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/consumer-groups")
    public ReturnResult<List<Map<String, Object>>> listConsumerGroups(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(kafkaPanelService.listConsumerGroups(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询消费者组列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/consumer-groups/{groupId}")
    public ReturnResult<Map<String, Object>> consumerGroupDetail(
            @PathVariable String connectionId,
            @PathVariable String groupId) {
        try {
            return ReturnResult.ok(kafkaPanelService.consumerGroupDetail(connectionId, groupId));
        } catch (Exception e) {
            return ReturnResult.error("查询消费者组详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/topics/{topicName}/offsets")
    public ReturnResult<List<Map<String, Object>>> topicOffsets(
            @PathVariable String connectionId,
            @PathVariable String topicName) {
        try {
            return ReturnResult.ok(kafkaPanelService.topicOffsets(connectionId, topicName));
        } catch (Exception e) {
            return ReturnResult.error("查询 Topic offset 失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/consumer-groups/{groupId}/reset")
    public ReturnResult<Boolean> resetConsumerGroupOffset(
            @PathVariable String connectionId,
            @PathVariable String groupId,
            @RequestBody Map<String, String> body) {
        String topicName = body.getOrDefault("topicName", "");
        String strategy = body.getOrDefault("strategy", "earliest");
        try {
            return ReturnResult.ok(kafkaPanelService.resetConsumerGroupOffset(connectionId, groupId, topicName, strategy));
        } catch (Exception e) {
            return ReturnResult.error("重置 offset 失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/messages")
    public ReturnResult<Boolean> produceMessage(
            @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        String topicName = body.getOrDefault("topicName", "");
        String key = body.getOrDefault("key", "");
        String value = body.getOrDefault("value", "");
        try {
            return ReturnResult.ok(kafkaPanelService.produceMessage(connectionId, topicName, key, value));
        } catch (Exception e) {
            return ReturnResult.error("发送消息失败: " + e.getMessage());
        }
    }
}
