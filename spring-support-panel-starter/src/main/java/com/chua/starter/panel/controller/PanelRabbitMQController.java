package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.RabbitMQPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/rabbitmq")
public class PanelRabbitMQController {

    private final RabbitMQPanelService rabbitMQPanelService;

    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 5672)));
        String username = String.valueOf(body.getOrDefault("username", "guest"));
        String password = String.valueOf(body.getOrDefault("password", "guest"));
        int managementPort = Integer.parseInt(String.valueOf(body.getOrDefault("managementPort", 15672)));
        try {
            return ReturnResult.ok(rabbitMQPanelService.open(host, port, username, password, managementPort));
        } catch (Exception e) {
            return ReturnResult.error("RabbitMQ 连接失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        rabbitMQPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    @GetMapping("/{connectionId}/overview")
    public ReturnResult<Map<String, Object>> overview(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.overview(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询概览失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/nodes")
    public ReturnResult<List<Map<String, Object>>> listNodes(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listNodes(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询节点失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/queues")
    public ReturnResult<List<Map<String, Object>>> listQueues(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listQueues(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询队列失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/queues/{queueName}")
    public ReturnResult<Map<String, Object>> queueDetail(
            @PathVariable String connectionId,
            @PathVariable String queueName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.queueDetail(connectionId, queueName));
        } catch (Exception e) {
            return ReturnResult.error("查询队列详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/exchanges")
    public ReturnResult<List<Map<String, Object>>> listExchanges(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listExchanges(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询交换机失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/exchanges/{exchangeName}")
    public ReturnResult<Map<String, Object>> exchangeDetail(
            @PathVariable String connectionId,
            @PathVariable String exchangeName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.exchangeDetail(connectionId, exchangeName));
        } catch (Exception e) {
            return ReturnResult.error("查询交换机详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/bindings")
    public ReturnResult<List<Map<String, Object>>> listBindings(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listBindings(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询绑定失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/connections")
    public ReturnResult<List<Map<String, Object>>> listConnections(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listConnections(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询连接失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/channels")
    public ReturnResult<List<Map<String, Object>>> listChannels(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listChannels(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询 Channel 失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/publish")
    public ReturnResult<Boolean> publishMessage(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String exchangeName = String.valueOf(body.getOrDefault("exchange", ""));
        String routingKey = String.valueOf(body.getOrDefault("routingKey", ""));
        String payload = String.valueOf(body.getOrDefault("payload", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = body.containsKey("properties")
                ? (Map<String, Object>) body.get("properties") : null;
        try {
            return ReturnResult.ok(rabbitMQPanelService.publishMessage(
                    connectionId, exchangeName, routingKey, payload, properties));
        } catch (Exception e) {
            return ReturnResult.error("发布消息失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/queues/{queueName}/get")
    public ReturnResult<List<Map<String, Object>>> getMessages(
            @PathVariable String connectionId,
            @PathVariable String queueName,
            @RequestBody Map<String, Object> body) {
        int count = Integer.parseInt(String.valueOf(body.getOrDefault("count", 10)));
        String ackMode = String.valueOf(body.getOrDefault("ackMode", "ack_requeue_true"));
        try {
            return ReturnResult.ok(rabbitMQPanelService.getMessages(connectionId, queueName, count, ackMode));
        } catch (Exception e) {
            return ReturnResult.error("消费消息失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/queues/{queueName}")
    public ReturnResult<Boolean> deleteQueue(
            @PathVariable String connectionId,
            @PathVariable String queueName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.deleteQueue(connectionId, queueName));
        } catch (Exception e) {
            return ReturnResult.error("删除队列失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/queues/{queueName}/contents")
    public ReturnResult<Boolean> purgeQueue(
            @PathVariable String connectionId,
            @PathVariable String queueName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.purgeQueue(connectionId, queueName));
        } catch (Exception e) {
            return ReturnResult.error("清空队列失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/queues")
    public ReturnResult<Boolean> createQueue(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String queueName = String.valueOf(body.getOrDefault("name", ""));
        boolean durable = Boolean.TRUE.equals(body.get("durable"));
        boolean autoDelete = Boolean.TRUE.equals(body.get("autoDelete"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = body.containsKey("arguments")
                ? (Map<String, Object>) body.get("arguments") : null;
        try {
            return ReturnResult.ok(rabbitMQPanelService.createQueue(connectionId, queueName, durable, autoDelete, arguments));
        } catch (Exception e) {
            return ReturnResult.error("创建队列失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/exchanges")
    public ReturnResult<Boolean> createExchange(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String exchangeName = String.valueOf(body.getOrDefault("name", ""));
        String type = String.valueOf(body.getOrDefault("type", "direct"));
        boolean durable = Boolean.TRUE.equals(body.get("durable"));
        boolean autoDelete = Boolean.TRUE.equals(body.get("autoDelete"));
        boolean internal = Boolean.TRUE.equals(body.get("internal"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = body.containsKey("arguments")
                ? (Map<String, Object>) body.get("arguments") : null;
        try {
            return ReturnResult.ok(rabbitMQPanelService.createExchange(connectionId, exchangeName, type, durable, autoDelete, internal, arguments));
        } catch (Exception e) {
            return ReturnResult.error("创建交换机失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/exchanges/{exchangeName}")
    public ReturnResult<Boolean> deleteExchange(
            @PathVariable String connectionId,
            @PathVariable String exchangeName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.deleteExchange(connectionId, exchangeName));
        } catch (Exception e) {
            return ReturnResult.error("删除交换机失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/bindings")
    public ReturnResult<Boolean> createBinding(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String exchangeName = String.valueOf(body.getOrDefault("exchange", ""));
        String queueName = String.valueOf(body.getOrDefault("queue", ""));
        String routingKey = String.valueOf(body.getOrDefault("routingKey", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = body.containsKey("arguments")
                ? (Map<String, Object>) body.get("arguments") : null;
        try {
            return ReturnResult.ok(rabbitMQPanelService.createBinding(connectionId, exchangeName, queueName, routingKey, arguments));
        } catch (Exception e) {
            return ReturnResult.error("创建绑定失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  用户管理
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{connectionId}/users")
    public ReturnResult<List<Map<String, Object>>> listUsers(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listUsers(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询用户列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/users/{username}")
    public ReturnResult<Map<String, Object>> userDetail(
            @PathVariable String connectionId,
            @PathVariable String username) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.userDetail(connectionId, username));
        } catch (Exception e) {
            return ReturnResult.error("查询用户详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/users")
    public ReturnResult<Boolean> createUser(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", ""));
        String password = String.valueOf(body.getOrDefault("password", ""));
        String tags = String.valueOf(body.getOrDefault("tags", ""));
        try {
            return ReturnResult.ok(rabbitMQPanelService.createUser(connectionId, username, password, tags));
        } catch (Exception e) {
            return ReturnResult.error("创建用户失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/users/{username}")
    public ReturnResult<Boolean> deleteUser(
            @PathVariable String connectionId,
            @PathVariable String username) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.deleteUser(connectionId, username));
        } catch (Exception e) {
            return ReturnResult.error("删除用户失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/users/{username}/permissions")
    public ReturnResult<List<Map<String, Object>>> getUserPermissions(
            @PathVariable String connectionId,
            @PathVariable String username) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.getUserPermissions(connectionId, username));
        } catch (Exception e) {
            return ReturnResult.error("查询用户权限失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/users/{username}/permissions")
    public ReturnResult<Boolean> setUserPermissions(
            @PathVariable String connectionId,
            @PathVariable String username,
            @RequestBody Map<String, Object> body) {
        String vhost = String.valueOf(body.getOrDefault("vhost", "/"));
        String configure = String.valueOf(body.getOrDefault("configure", ".*"));
        String write = String.valueOf(body.getOrDefault("write", ".*"));
        String read = String.valueOf(body.getOrDefault("read", ".*"));
        try {
            return ReturnResult.ok(rabbitMQPanelService.setUserPermissions(
                    connectionId, username, vhost, configure, write, read));
        } catch (Exception e) {
            return ReturnResult.error("设置用户权限失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  虚拟主机管理
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{connectionId}/vhosts")
    public ReturnResult<List<Map<String, Object>>> listVhosts(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.listVhosts(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询虚拟主机列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/vhosts/{vhostName}")
    public ReturnResult<Map<String, Object>> vhostDetail(
            @PathVariable String connectionId,
            @PathVariable String vhostName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.vhostDetail(connectionId, vhostName));
        } catch (Exception e) {
            return ReturnResult.error("查询虚拟主机详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/{connectionId}/vhosts")
    public ReturnResult<Boolean> createVhost(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String vhostName = String.valueOf(body.getOrDefault("name", ""));
        try {
            return ReturnResult.ok(rabbitMQPanelService.createVhost(connectionId, vhostName));
        } catch (Exception e) {
            return ReturnResult.error("创建虚拟主机失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/vhosts/{vhostName}")
    public ReturnResult<Boolean> deleteVhost(
            @PathVariable String connectionId,
            @PathVariable String vhostName) {
        try {
            return ReturnResult.ok(rabbitMQPanelService.deleteVhost(connectionId, vhostName));
        } catch (Exception e) {
            return ReturnResult.error("删除虚拟主机失败: " + e.getMessage());
        }
    }
}
