import java.io.*;
import java.nio.file.*;

public class RemoveAllBom {
    public static void main(String[] args) throws IOException {
        String[] files = {
            "spring-support-queue-mqtt-starter/src/main/java/com/chua/starter/queue/mqtt/MqttQueueAutoConfiguration.java",
            "spring-support-queue-mqtt-starter/src/main/java/com/chua/starter/queue/mqtt/MqttMessageTemplate.java",
            "spring-support-queue-kafka-starter/src/main/java/com/chua/starter/queue/kafka/KafkaMessageTemplate.java",
            "spring-support-queue-rabbitmq-starter/src/main/java/com/chua/starter/queue/rabbitmq/RabbitMessageTemplate.java",
            "spring-support-queue-rabbitmq-starter/src/main/java/com/chua/starter/queue/rabbitmq/RabbitQueueAutoConfiguration.java"
        };
        
        for (String file : files) {
            Path path = Paths.get(file);
            if (Files.exists(path)) {
                byte[] data = Files.readAllBytes(path);
                if (data.length >= 3 && data[0] == (byte)0xEF && data[1] == (byte)0xBB && data[2] == (byte)0xBF) {
                    byte[] newData = new byte[data.length - 3];
                    System.arraycopy(data, 3, newData, 0, newData.length);
                    Files.write(path, newData);
                    System.out.println("已移除 BOM: " + file);
                } else {
                    System.out.println("无 BOM: " + file);
                }
            }
        }
    }
}
