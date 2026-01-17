import java.io.*;
import java.nio.file.*;

public class remove_bom_queue {
    public static void main(String[] args) throws IOException {
        String[] files = {
            "spring-support-queue-starter/src/main/java/com/chua/starter/queue/Acknowledgment.java",
            "spring-support-queue-starter/src/main/java/com/chua/starter/queue/Message.java"
        };
        
        for (String file : files) {
            Path path = Paths.get(file);
            byte[] bytes = Files.readAllBytes(path);
            
            // 检查并移除 BOM
            if (bytes.length >= 3 && 
                bytes[0] == (byte)0xEF && 
                bytes[1] == (byte)0xBB && 
                bytes[2] == (byte)0xBF) {
                byte[] newBytes = new byte[bytes.length - 3];
                System.arraycopy(bytes, 3, newBytes, 0, newBytes.length);
                Files.write(path, newBytes);
                System.out.println("Removed BOM from " + file);
            }
        }
    }
}

