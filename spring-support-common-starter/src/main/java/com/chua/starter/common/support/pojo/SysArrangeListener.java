//package com.chua.starter.common.support.pojo;
//
//import com.chua.common.support.database.repository.Repository;
//import com.chua.common.support.lang.arrange.ArrangeLogger;
//
//import java.util.Date;
//
///**
// * @author CH
// */
//public class SysArrangeListener implements ArrangeLogger {
//
//    private String tid;
//    private String arrangeId;
//    private Repository<SysArrangeLogger> loggerRepository;
//
//    public SysArrangeListener(String tid, String arrangeId, Repository<SysArrangeLogger> loggerRepository) {
//        this.tid = tid;
//        this.arrangeId = arrangeId;
//        this.loggerRepository = loggerRepository;
//    }
//
//    @Override
//    public void listen(String message, String name, long cost) {
//        loggerRepository.save(new SysArrangeLogger(null, arrangeId, tid, message, new Date(), cost, name));
//    }
//}
