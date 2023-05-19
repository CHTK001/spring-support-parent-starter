package com.chua.starter.server.log;//package com.chua.starter.server.log;
//
//import com.chua.starter.log.LogEvent;
//import com.chua.starter.log.LogMessage;
//import com.chua.starter.log.listener.Listener;
//import com.chua.starter.log.zip.TarGzip;
//import org.apache.commons.io.FileUtils;
//import org.springframework.beans.factory.InitializingBean;
//import org.springframework.core.convert.ConversionService;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Resource;
//import java.io.File;
//import java.io.IOException;
//import java.io.Serializable;
//import java.nio.file.Files;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Collection;
//import java.util.LinkedList;
//import java.util.List;
//
//import static java.nio.charset.StandardCharsets.UTF_8;
//
///**
// * @author CH
// */
//@Component("mysql")
//@EnableScheduling
//public class MysqlListener implements Listener, InitializingBean {
//
//    private final String path = "/log-back";
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//    private final DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//    @Resource
//    private ConversionService conversionService;
//
//    @Resource
//    private BingLogTableHolder bingLogTableHolder;
//
//    public MysqlListener() {
//        try {
//            FileUtils.forceMkdir(new File(path));
//        } catch (IOException ignored) {
//        }
//    }
//
//    @Override
//    public void listener(LogMessage logMessage) {
//        if(logMessage.getEvent() == LogEvent.UNKNOWN) {
//            return;
//        }
//
//        if(logMessage.getEvent() != LogEvent.DELETE && logMessage.getEvent() != LogEvent.UPDATE) {
//            return;
//        }
//
//        File file = new File(path, LocalDate.now().format(formatter));
//        File databaseFile = new File(file, logMessage.getDatabase());
//        File eventFile = new File(databaseFile, logMessage.getEvent().name().toLowerCase());
//        try {
//            FileUtils.forceMkdir(eventFile);
//        } catch (IOException ignored) {
//        }
//
//        if(logMessage.getEvent() == LogEvent.DELETE) {
//            try {
//                FileUtils.writeLines(new File(eventFile, logMessage.getTable() + ".log"), createLine(logMessage.getDatabase() + "." + logMessage.getTable(), logMessage.getOldData()), true);
//            } catch (IOException ignored) {
//            }
//            return;
//        }
//
//
//        if(logMessage.getEvent() == LogEvent.UPDATE) {
//            try {
//                FileUtils.writeLines(new File(eventFile, logMessage.getTable() + ".log"), createLine(logMessage.getDatabase() + "." + logMessage.getTable(), logMessage.getOldData()), true);
//                FileUtils.writeLines(new File(eventFile, logMessage.getTable() + ".log"), createLine(logMessage.getDatabase() + "." + logMessage.getTable(), logMessage.getNewData()), true);
//            } catch (IOException ignored) {
//            }
//        }
//    }
//
//    private Collection<?> createLine(String table, Collection<Serializable[]> oldData) {
//        List<String> rs = new LinkedList<>();
//        for (Serializable[] oldDatum : oldData) {
//            rs.add(createItem(table, oldDatum));
//        }
//        return rs;
//    }
//
//    private String createItem(String table, Serializable[] oldDatum) {
//        StringBuffer stringBuffer = new StringBuffer();
//        stringBuffer.append(LocalDateTime.now().format(formatterTime)).append(": ");
//        stringBuffer.append(" 字段: [");
//        int index = 1;
//        for (Serializable ignored : oldDatum) {
//            stringBuffer.append(bingLogTableHolder.getTable(table, index ++)).append(", ");
//        }
//
//        stringBuffer = stringBuffer.delete(stringBuffer.length() - 2, stringBuffer.length());
//        stringBuffer.append("] 数据: [");
//
//        for (Serializable serializable : oldDatum) {
//            stringBuffer.append(createValue(serializable)).append(", ");
//        }
//
//        return stringBuffer.substring(0, stringBuffer.length() - 2) + "]";
//    }
//
//    private String createValue(Serializable serializable) {
//        if(serializable == null) {
//            return "";
//        }
//
//        if(serializable instanceof String) {
//            return serializable.toString();
//        }
//
//        if(Number.class.isAssignableFrom(serializable.getClass())) {
//            return serializable.toString();
//        }
//
//
//        if(serializable instanceof byte[]) {
//            return new String((byte[]) serializable, UTF_8);
//        }
//
//        try {
//            return conversionService.convert(serializable, String.class);
//        } catch (Exception e) {
//            return serializable.toString();
//        }
//    }
//
//
//    @Scheduled(cron = "0 0 0/12 * * ?")
//    public void execute() {
//        File file = new File(path);
//        File[] files = file.listFiles();
//        if(null == files) {
//            return;
//        }
//
//        for (File file1 : files) {
//            backup(file1);
//        }
//    }
//
//    private void backup(File file1) {
//        String name = file1.getName();
//        LocalDate parse = null;
//        try {
//            parse = LocalDate.parse(name, formatter);
//        } catch (Exception ignored) {
//        }
//
//        if(null == parse || !parse.isBefore(LocalDate.now())) {
//            return;
//        }
//
//
//        TarGzip tar = new TarGzip();
//        tar.addFile(file1);
//        File file = new File(file1.getParentFile(), file1.getName() + ".tar.gz");
//        try {
//            tar.to(Files.newOutputStream(file.toPath()));
//            FileUtils.deleteDirectory(file1);
//        } catch (IOException ignored) {
//        }
//    }
//
//    @Override
//    public void afterPropertiesSet() throws Exception {
//
//    }
//}
