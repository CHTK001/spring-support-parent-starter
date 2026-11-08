package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import com.chua.starter.monitor.support.model.TsharkAnomalyVO;
import com.chua.starter.monitor.support.model.TsharkFlowVO;
import com.chua.starter.monitor.support.model.TsharkInterfaceVO;
import com.chua.starter.monitor.support.model.TsharkHandshakeVO;
import com.chua.starter.monitor.support.model.TsharkProtocolStatVO;
import com.chua.starter.monitor.support.model.TsharkTcpStreamVO;
import com.chua.starter.monitor.support.model.dto.TsharkCaptureStartDTO;
import com.chua.starter.monitor.support.service.impl.TsharkCaptureService;
import com.chua.starter.monitor.support.tshark.TsharkInstallStreamRegistry;
import com.chua.starter.monitor.support.tshark.TsharkRuntime;
import com.chua.starter.monitor.support.tshark.TsharkRuntimeStatus;
import com.chua.starter.monitor.support.config.TsharkRetentionProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * ç½‘ç»œæŠ“åŒ…æŽ¥å£
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "ç½‘ç»œæŠ“åŒ…", description = "ç½‘å¡æŠ“åŒ…ä¼šè¯/æ•°æ®åŒ…/ç»Ÿè®¡")
@RestController
@RequestMapping("/ms/monitor/tshark")
public class TsharkCaptureController {

    private final TsharkCaptureService tsharkCaptureService; // tsharkæŠ“åŒ…æœåŠ¡

    private final TsharkRuntime tsharkRuntime; // tshark è¿è¡Œæ—¶æ£€æµ‹ä¸Žè‡ªåŠ¨è¡¥é½

    private final TsharkInstallStreamRegistry installStreamRegistry; // å®‰è£…è¿›åº¦æŽ¨é€
    private final TsharkRetentionProperties retentionProperties; // å°ç¹ç­ç¥



    /**
     * æž„é€ å™¨ï¼Œæ³¨å…¥ tshark æŠ“åŒ…æœåŠ¡
     *
     * @param tsharkCaptureService  tshark æŠ“åŒ…æœåŠ¡
     * @param tsharkRuntime         tshark è¿è¡Œæ—¶
     * @param installStreamRegistry å®‰è£…è¿›åº¦æŽ¨é€æ³¨å†Œè¡¨
     */
    public TsharkCaptureController(TsharkCaptureService tsharkCaptureService,
                                   TsharkRuntime tsharkRuntime,
                                    TsharkInstallStreamRegistry installStreamRegistry, TsharkRetentionProperties retentionProperties) {
        this.tsharkCaptureService = tsharkCaptureService;
        this.tsharkRuntime = tsharkRuntime;
        this.installStreamRegistry = installStreamRegistry;
           this.retentionProperties = retentionProperties;

    /**
     * æ£€æŸ¥ tshark å¯ç”¨æ€§
     *
     * @return tshark æ˜¯å¦å¯ç”¨
     */
    @Operation(summary = "æ£€æŸ¥ tshark å¯ç”¨æ€§")
    @GetMapping("/check")
    public ReturnResult<Boolean> check() {
        return this.tsharkCaptureService.checkTshark();
    }

    /**
     * tshark è¿è¡Œæ—¶çŠ¶æ€ã€‚
     *
     * <p>æ¯” {@code /check} å¤šå¸¦ã€Œå®‰è£…åˆ°å“ªä¸€æ­¥ / èƒ½ä¸èƒ½å®žæ—¶æŠ“åŒ… / ä¸ºä»€ä¹ˆä¸å¯ç”¨ã€ï¼Œ
     * é¡µé¢æ®æ­¤å†³å®šæ˜¯æ˜¾ç¤ºè¿›åº¦ã€æç¤ºè£…é©±åŠ¨ï¼Œè¿˜æ˜¯ç›´æŽ¥æ”¾è¡Œã€‚</p>
     *
     * @return çŠ¶æ€å¿«ç…§
     */
    @Operation(summary = "tshark è¿è¡Œæ—¶çŠ¶æ€")
    @GetMapping("/status")
    public ReturnResult<TsharkRuntimeStatus> status() {
        return ReturnResult.ok(this.tsharkRuntime.status());
    }

    /**
     * è§¦å‘ä¸€æ¬¡å¼‚æ­¥è¡¥é½ï¼ˆä¸‹è½½ / è§£åŽ‹ / è‡ªæ£€ï¼‰ã€‚
     *
     * <p>å¹‚ç­‰ï¼šæ­£åœ¨å®‰è£…æ—¶ç›´æŽ¥è¿”å›žå½“å‰çŠ¶æ€ï¼Œä¸ä¼šé‡å¤ä¸‹è½½ã€‚é¡µé¢ä¸Šçš„ã€Œé‡è¯•ã€ä¹Ÿèµ°è¿™ä¸ªæŽ¥å£ï¼Œ
     * å¤±è´¥åŽä¸éœ€è¦é‡å¯æœåŠ¡ã€‚</p>
     *
     * @return è§¦å‘åŽçš„çŠ¶æ€å¿«ç…§
     */
    @Operation(summary = "å®‰è£… / é‡è¯•è¡¥é½ tshark")
    @PostMapping("/install")
    public ReturnResult<TsharkRuntimeStatus> install() {
        this.tsharkRuntime.ensureAsync();
        return ReturnResult.ok(this.tsharkRuntime.status());
    }

    /**
     * å®‰è£…è¿›åº¦æŽ¨é€ï¼ˆSSEï¼‰ã€‚
     *
     * <p>äº‹ä»¶å {@code progress}ï¼Œè½½è·å°±æ˜¯ {@link TsharkRuntimeStatus}ï¼›è¿žä¸Šå…ˆå›žä¸€å¸§å½“å‰çŠ¶æ€ï¼Œ
     * å› æ­¤ä¸­é€”æ‰“å¼€é¡µé¢ä¹Ÿèƒ½çœ‹åˆ°çœŸå®žè¿›åº¦è€Œä¸æ˜¯ä»Ž 0 å¼€å§‹ã€‚</p>
     *
     * @return SSE è¿žæŽ¥
     */
    @Operation(summary = "tshark å®‰è£…è¿›åº¦")
    @GetMapping("/install/stream")
    public ResponseEntity<SseEmitter> installStream() {
        SseEmitter emitter = this.installStreamRegistry.subscribe(this.tsharkRuntime.status());
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    /**
     * åˆ—å‡ºå¯ç”¨ç½‘å¡
     *
     * <p>å¸¦å‹å¥½åä¸Žæœ¬æœº IPv4ï¼Œå¹¶æŠŠæ‰¿è½½é»˜è®¤å‡ºå£çš„é‚£å¼ æŽ’åœ¨æœ€å‰ â€”â€” Windows ä¸Š tshark åªç»™
     * {@code \Device\NPF_{GUID}}ï¼Œä¸åŠ å·¥çš„è¯é¡µé¢ä¸Šæ˜¯ä¸€ä¸²è¯»ä¸æ‡‚çš„ GUIDã€‚</p>
     *
     * @return ç½‘å¡åˆ—è¡¨ï¼ˆè®¾å¤‡å + å‹å¥½å + IPv4 + æ˜¯å¦æœ¬æœºå‡ºå£ï¼‰
     */
    @Operation(summary = "åˆ—å‡ºå¯ç”¨ç½‘å¡")
    @GetMapping("/interfaces")
    public ReturnResult<List<TsharkInterfaceVO>> interfaces() {
        return this.tsharkCaptureService.listInterfaces();
    }

    /**
     * å¯åŠ¨æŠ“åŒ…ä¼šè¯
     *
     * @param body æŠ“åŒ…è¯·æ±‚ï¼ˆname/captureInterface/captureFilter/filterType/count/timeoutï¼‰
     * @return æ–°å»ºçš„æŠ“åŒ…ä¼šè¯
     */
    @Operation(summary = "å¯åŠ¨æŠ“åŒ…ä¼šè¯")
    @PostMapping("/capture")
    public ReturnResult<List<MsTsharkSession>> start(@RequestBody TsharkCaptureStartDTO body) {
        if (body.interfaces() != null && !body.interfaces().isEmpty()) {
            return this.tsharkCaptureService.startCapture(body.name(), body.interfaces(),
                    body.captureFilter(), body.filterType(), body.count(), body.timeout());
        }
        return this.tsharkCaptureService.startCapture(body.name(), body.captureInterface(),
                body.captureFilter(), body.filterType(), body.count(), body.timeout());
    }

    /**
     * åœæ­¢æŠ“åŒ…ä¼šè¯
     *
     * @param id ä¼šè¯ ID
     * @return æ“ä½œç»“æžœ
     */
    @Operation(summary = "åœæ­¢æŠ“åŒ…ä¼šè¯")
    @PostMapping("/capture/{id}/stop")
    public ReturnResult<Void> stop(@PathVariable Long id) {
        return this.tsharkCaptureService.stopCapture(id);
    }

    /**
     * æš‚åœæŠ“åŒ…ä¼šè¯çš„å®žæ—¶æŽ¨é€
     *
     * <p>åªæŒ‚èµ·æŽ¨ç»™å‰ç«¯çš„å®žæ—¶æµï¼ˆsocket.io å¹¿æ’­ä¸Ž SSE åŒæ—¶æŽæ–­ï¼‰ï¼›æŠ“åŒ…è¿›ç¨‹ã€pcap å†™å…¥ä¸Ž
     * å¢žé‡è§£æžå…¥åº“ä¸å—å½±å“ï¼Œæ¢å¤åŽä»Žæ–­ç‚¹ç»§ç»­æŽ¨ï¼Œä¸ä¸¢åŒ…ã€‚</p>
     *
     * @param id ä¼šè¯ ID
     * @return æ“ä½œç»“æžœ
     */
    @Operation(summary = "æš‚åœæŠ“åŒ…ä¼šè¯")
    @PostMapping("/capture/{id}/pause")
    public ReturnResult<Void> pause(@PathVariable Long id) {
        return this.tsharkCaptureService.pauseSession(id);
    }

    /**
     * æ¢å¤æŠ“åŒ…ä¼šè¯çš„å®žæ—¶æŽ¨é€
     *
     * @param id ä¼šè¯ ID
     * @return æ“ä½œç»“æžœ
     */
    @Operation(summary = "æ¢å¤æŠ“åŒ…ä¼šè¯")
    @PostMapping("/capture/{id}/resume")
    public ReturnResult<Void> resume(@PathVariable Long id) {
        return this.tsharkCaptureService.resumeSession(id);
    }

    /**
     * å¯¼å…¥ pcap æ–‡ä»¶ï¼ˆPCAP åˆ†æžï¼‰
     *
     * <p>ä¸Šä¼ çš„ pcap è½åˆ°æŠ“åŒ…ç›®å½•ã€ä»¥ {@code source=import} å»ºä¸€æ¡ä¼šè¯ï¼ŒéšåŽåœ¨åŽå°ç”¨
     * {@code tshark -r} æ•´ä»½è§£æžå…¥åº“ã€‚è§£æžå‡ºçš„æŠ¥æ–‡å› æ­¤å¤ç”¨åŒä¸€å¥—æŽ¥å£ï¼ˆåˆ†é¡µ / è¯¦æƒ… /
     * åè®®åˆ†å¸ƒ / Top æµï¼‰ï¼ŒåŽŸå§‹æ–‡ä»¶ç”¨ {@link #pcap(Long)} ä¸‹å›žåŽ»ã€‚</p>
     *
     * <p>è¿”å›žæ—¶è§£æžé€šå¸¸è¿˜æ²¡ç»“æŸï¼ˆä¼šè¯çŠ¶æ€ 1 = è§£æžä¸­ï¼‰ï¼ŒåŒ…æ•°åœ¨åŽå°ç´¯åŠ å¹¶é€šè¿‡ä¼šè¯æŽ¨é€
     * æŠµè¾¾å‰ç«¯ï¼Œé¡µé¢æ®æ­¤æ˜¾ç¤ºè¿›åº¦ã€‚</p>
     *
     * @param file pcap æ–‡ä»¶ï¼ˆmultipart å­—æ®µå {@code file}ï¼‰
     * @param name ä¼šè¯åï¼ˆå¯ç©ºï¼Œé»˜è®¤å–ä¸Šä¼ çš„åŽŸå§‹æ–‡ä»¶åï¼‰
     * @return æ–°å»ºçš„ä¼šè¯
     */
    @Operation(summary = "å¯¼å…¥ pcap æ–‡ä»¶")
    @PostMapping("/pcap/import")
    public ReturnResult<MsTsharkSession> importPcap(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(required = false) String name) {
        return this.tsharkCaptureService.importPcap(name, file);
    }

    /**
     * åˆ é™¤æŠ“åŒ…ä¼šè¯ï¼ˆå«æ•°æ®åŒ…ï¼‰
     *
     * @param id ä¼šè¯ ID
     * @return æ“ä½œç»“æžœ
     */
    @Operation(summary = "åˆ é™¤æŠ“åŒ…ä¼šè¯(å«æ•°æ®åŒ…)")
    @DeleteMapping("/sessions/{id}")
    public ReturnResult<Void> delete(@PathVariable Long id) {
        return this.tsharkCaptureService.deleteSession(id);
    }

    /**
     * æŠ“åŒ…ä¼šè¯åˆ†é¡µåˆ—è¡¨
     *
     * @param page é¡µç 
     * @param size æ¯é¡µå¤§å°
     * @param source æ¥æºè¿‡æ»¤ï¼ˆlive å®žæ—¶æŠ“åŒ… / import å¯¼å…¥ pcapï¼Œå¯ç©ºè¡¨ç¤ºä¸è¿‡æ»¤ï¼‰
     * @return åˆ†é¡µç»“æžœ
     */
    @Operation(summary = "æŠ“åŒ…ä¼šè¯åˆ†é¡µåˆ—è¡¨")
    @GetMapping("/sessions")
    public ReturnResult<IPage<MsTsharkSession>> sessions(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String source) {
        return this.tsharkCaptureService.pageSessions(page, size, source);
    }

    /**
     * æ•°æ®åŒ…åˆ†é¡µåˆ—è¡¨
     *
     * @param id ä¼šè¯ ID
     * @param page é¡µç 
     * @param size æ¯é¡µå¤§å°
     * @param keyword å…³é”®å­—ï¼ˆå¯ç©ºï¼‰ï¼Œåœ¨åè®®/IP/ç«¯å£/æ‘˜è¦é‡Œæ¨¡ç³ŠåŒ¹é…
     * @return åˆ†é¡µç»“æžœ
     */
    @Operation(summary = "æ•°æ®åŒ…åˆ†é¡µåˆ—è¡¨")
    @GetMapping("/sessions/{id}/packets")
    public ReturnResult<IPage<MsTsharkPacket>> packets(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String startTime,
                                                        @RequestParam(required = false) String endTime,
                                                        @RequestParam(required = false) String displayFilter) {
        return this.tsharkCaptureService.pagePackets(id, page, size, keyword, startTime, endTime, displayFilter);
    }

    /**
     * è¿˜åŽŸæŸä¸ªåŒ…æ‰€å±ž TCP æµçš„æ¡æ‰‹ / å…³é—­ç”Ÿå‘½å‘¨æœŸ
     *
     * <p>æŠ“åŒ…é¡µåœ¨åŒ…ä¸Šå³é”® â†’ ã€ŒæŸ¥çœ‹ç”Ÿå‘½å‘¨æœŸã€å°±è°ƒè¿™é‡Œï¼Œè¿”å›žè¯¥åŒ…çš„æ•´æ¡æµæŒ‰æ—¶åºæŽ’å¥½çš„
     * æŠ¥æ–‡åºåˆ—ä¸Žæ¡æ‰‹åˆ¤å®šï¼ˆä¸‰æ¬¡ / å››æ¬¡ / æœªå®Œæˆï¼‰ï¼Œå‰ç«¯æ®æ­¤ç”»æ—¶åºå›¾ã€‚</p>
     *
     * <p>HTTP / TLS / SSH ç­‰è·‘åœ¨ TCP ä¸Šçš„åè®®åŒæ ·æ”¯æŒï¼šæ•°æ®å¸§çš„åè®®ä¸Žæ¡æ‰‹å¸§çš„åè®®
     * ä¸åŒï¼ŒåŒ¹é…åªæŒ‰å››å…ƒç»„ï¼Œä¸æŒ‰åè®®åˆ—è¿‡æ»¤ã€‚</p>
     *
     * @param sessionId ä¼šè¯ ID
     * @param packetId æ•°æ®åŒ… ID
     * @return ç”Ÿå‘½å‘¨æœŸï¼›åŒ…ä¸å­˜åœ¨æˆ–åè®®ä¸è·‘åœ¨ TCP ä¸Šæ—¶ 404
     */
    @Operation(summary = "æŸ¥çœ‹ TCP æ¡æ‰‹ç”Ÿå‘½å‘¨æœŸ")
    @GetMapping("/sessions/{sessionId}/packets/{packetId}/handshake")
    public ResponseEntity<TsharkHandshakeVO> handshake(@PathVariable Long sessionId,
                                                       @PathVariable Long packetId) {
        TsharkHandshakeVO vo = this.tsharkCaptureService.handshakeOf(sessionId, packetId);
        return vo == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(vo);
    }

    /**
     * è®¢é˜…æ•°æ®åŒ…çš„å®žæ—¶æŽ¨é€ï¼ˆSSEï¼‰
     *
     * <p>å»ºè¿žå³å›ž {@code ready}ï¼Œä¹‹åŽï¼šæ¯å…¥åº“ä¸€ä¸ªåŒ…æŽ¨ä¸€ä¸ª {@code packet}ï¼Œ
     * pcap è§£æžæ”¶å°¾æŽ¨ä¸€ä¸ª {@code session}ï¼ˆå‰ç«¯æ®æ­¤é‡æ‹‰åè®®åˆ†å¸ƒä¸Ž Top æµï¼‰ã€‚
     * å¦æœ‰ 20s ä¸€æ¬¡çš„æ³¨é‡Šå¸§ä¿æ´»ï¼Œå‰ç«¯ä¸ä¼šæŠŠå®ƒå½“äº‹ä»¶ã€‚</p>
     *
     * <p><strong>é¦–å±åŽ†å²ä¸åœ¨è¿™é‡Œç»™</strong>ï¼šSSE åªåšå¢žé‡ï¼ŒåŽ†å²èµ°
     * {@code /sessions/{id}/packets}ã€‚å¦åˆ™å»ºè¿žé‚£ä¸€åˆ»å°±è¦æŽ¨ä¸Šç™¾ä¸ªåŒ…ã€‚</p>
     *
     * @param id ä¼šè¯ ID
     * @return SSE è¿žæŽ¥ï¼›ä¼šè¯ä¸å­˜åœ¨æ—¶ 404
     */
    @Operation(summary = "æ•°æ®åŒ…å®žæ—¶æŽ¨é€ï¼ˆSSEï¼‰", description = "æŒ‰æŠ“åŒ…ä¼šè¯åˆ†ç»„æŽ¨é€ï¼Œé€åŒ…å¢žé‡")
    @GetMapping("/sessions/{id}/packets/stream")
    public ResponseEntity<SseEmitter> packetStream(@PathVariable Long id) {
        if (this.tsharkCaptureService.getSession(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(this.tsharkCaptureService.subscribePacketStream(id));
    }

    /**
     * ä¸‹è½½ä¼šè¯çš„åŽŸå§‹ pcap æ–‡ä»¶
     *
     * <p>é¡µé¢ä¸Šçš„åè®®æ‘˜è¦åªæ˜¯ tshark è§£æžåŽçš„ç»“æžœï¼Œè¦åšæ›´æ·±çš„åˆ†æžï¼ˆé‡ç»„ã€ä¸“å®¶ä¿¡æ¯ã€
     * è‡ªå®šä¹‰è¿‡æ»¤ï¼‰å¾—æŠŠåŽŸå§‹ pcap æ‹¿åŽ» Wireshark æ‰“å¼€ï¼Œæ‰€ä»¥æä¾›åŽŸä»¶ä¸‹è½½ã€‚</p>
     *
     * @param id ä¼šè¯ ID
     * @return pcap æ–‡ä»¶æµï¼›ä¼šè¯ä¸å­˜åœ¨æˆ–æ–‡ä»¶å·²æ¸…ç†æ—¶è¿”å›ž 404
     */
    @Operation(summary = "ä¸‹è½½åŽŸå§‹ pcap")
    @GetMapping("/sessions/{id}/pcap")
    public ResponseEntity<Resource> pcap(@PathVariable Long id) {
        Resource resource = this.tsharkCaptureService.pcapResource(id);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String name = this.tsharkCaptureService.pcapFileName(id);
        return ResponseEntity.ok()
                // æ–‡ä»¶åå«ä¸­æ–‡ï¼ŒASCII æ®µå…œåº•ä¸€ä¸ª pcap.pcapï¼Œé¿å…è€å®¢æˆ·ç«¯æ‹¿åˆ°ä¹±ç å
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"capture.pcap\"; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * æŒ‰æ˜¾ç¤ºè¿‡æ»¤å¯¼å‡º pcap å­é›†
     *
     * <p>è°ƒç”¨æ–¹è¡¥ä¸Šæ˜¾ç¤ºè¿‡æ»¤åŽï¼ŒæœåŠ¡ç«¯ç”¨ tshark åšä¸€æ¬¡å›žæ”¾è¿‡æ»¤ï¼ˆ-r åŽŸç‰ˆ + -w è¿‡æ»¤åŽ pcapï¼‰ï¼Œ
     * ç›´æŽ¥è¿”å›žæ–‡ä»¶ï¼Œå‰ç«¯ä»¥ä¸‹è½½æ–‡ä»¶çš„å½¢å¼æ‹¿å›žåŽ»ã€‚</p>
     *
     * @param id ä¼šè¯ ID
     * @param displayFilter æ˜¾ç¤ºè¿‡æ»¤ï¼Œç©ºä¸²åˆ™å¯¼å‡ºå®Œæ•´ pcap
     * @return å¯¼å‡ºåŽçš„èµ„æº
     */
    @Operation(summary = "æŒ‰è¿‡æ»¤å™¨å¯¼å‡º pcap")
    @GetMapping("/sessions/{id}/packets/export")
    public ResponseEntity<Resource> exportPcap(@PathVariable Long id,
                                                @RequestParam(required = false) String displayFilter) {
        ReturnResult<FileSystemResource> r = this.tsharkCaptureService.exportPcap(id, displayFilter);
        if (r == null || !r.isOk()) {
            return ResponseEntity.badRequest().build();
        }
        FileSystemResource res = r.getData();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"export.pcap\"; filename*=UTF-8''export.pcap")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
    }

    /**
     * Follow TCP Streamï¼šæŒ‰æŠ¥æ–‡æ‰€åœ¨è¿žæŽ¥è¿˜åŽŸåŒå‘è½½è·å¸§
     *
     * @param sessionId ä¼šè¯ ID
     * @param packetId  æ•°æ®åŒ… ID
     * @return åŒå‘é‡æž„æµ
     */
    @Operation(summary = "Follow TCP Stream")
    @GetMapping("/sessions/{sessionId}/packets/{packetId}/stream")
    public ReturnResult<TsharkTcpStreamVO> stream(@PathVariable Long sessionId,
                                                   @PathVariable Long packetId) {
        return this.tsharkCaptureService.followTcpStream(sessionId, packetId);
    }

    /**
     * æ•°æ®åŒ…è¯¦æƒ…
     *
     * @param sessionId ä¼šè¯ ID
     * @param id æ•°æ®åŒ… ID
     * @return æ•°æ®åŒ…è¯¦æƒ…
     */
    @Operation(summary = "æ•°æ®åŒ…è¯¦æƒ…")
    @GetMapping("/sessions/{sessionId}/packets/{id}")
    public ReturnResult<MsTsharkPacket> packet(@PathVariable Long sessionId,
                                               @PathVariable Long id) {
        return this.tsharkCaptureService.getPacket(sessionId, id);
    }

    /**
     * åè®®åˆ†å¸ƒç»Ÿè®¡
     *
     * @param id ä¼šè¯ ID
     * @param displayFilter æ˜¾ç¤ºè¿‡æ»¤ï¼ˆå¯ç©ºï¼‰ï¼ŒæŠ¥æ–‡é¡µ / ç»Ÿè®¡ / ä¼šè¯æµ / è¯Šæ–­åŒä¸€å¥—è¯æ³•
     */
    @Operation(summary = "åè®®åˆ†å¸ƒç»Ÿè®¡")
    @GetMapping("/sessions/{id}/stats/protocol")
    public ReturnResult<List<TsharkProtocolStatVO>> protocolStats(@PathVariable Long id, @RequestParam(required = false) String displayFilter) {
        return this.tsharkCaptureService.protocolStats(id, displayFilter);
    }

    /**
     * ä¼šè¯ Top æµç»Ÿè®¡
     *
     * @param displayFilter æ˜¾ç¤ºè¿‡æ»¤ï¼ˆå¯ç©ºï¼‰
     * @param limit è¿”å›žæ¡æ•°
     * @return Top æµåˆ—è¡¨
     */
    @Operation(summary = "ä¼šè¯ Top æµç»Ÿè®¡")
    @GetMapping("/sessions/{id}/stats/top")
    public ReturnResult<List<TsharkFlowVO>> topFlows(@PathVariable Long id, @RequestParam(defaultValue = "10") int limit, @RequestParam(required = false) String displayFilter) {
        return this.tsharkCaptureService.topFlows(id, limit, displayFilter);
    }

    /**
     * æ—¶é—´è½´ç»Ÿè®¡ï¼šæŒ‰æ—¶é—´æ¡¶èšåˆæŠ¥æ–‡çš„æ•°é‡ä¸Žæ€»å­—èŠ‚æ•°ã€‚
     *
     * <p>ç”¨äºŽå‰ç«¯ç»˜åˆ¶ã€ŒæŠ¥æ–‡é‡éšæ—¶é—´å˜åŒ–ã€çš„æŠ˜çº¿å›¾ã€‚</p>
     *
     * @param id            ä¼šè¯ ID
     * @param bucketSeconds æ¡¶å¤§å°ï¼ˆç§’ï¼‰ï¼Œé»˜è®¤ 10
     * @param displayFilter æ˜¾ç¤ºè¿‡æ»¤ï¼ˆå¯ç©ºï¼‰
     */
    @Operation(summary = "æ—¶é—´è½´ç»Ÿè®¡")
    @GetMapping("/sessions/{id}/stats/timeline")
    public ReturnResult<List<Map<String, Object>>> timeline(@PathVariable Long id, @RequestParam(defaultValue = "10") Integer bucketSeconds, @RequestParam(required = false) String displayFilter) {
        return ReturnResult.success(this.tsharkCaptureService.timeline(id, bucketSeconds, displayFilter));
    }

    /**
     * å¼‚å¸¸è¯Šæ–­ï¼šè¿žæŽ¥é‡ç½® / ICMP ä¸å¯è¾¾ / é‡ä¼  / ä¹±åº / é›¶çª—å£ / DNS å¤±è´¥ /
     * HTTP 4xx/5xx / TLS å‘Šè­¦ç­‰å¸¸è§é—®é¢˜çš„å‘½ä¸­è®¡æ•°ã€‚
     *
     * <p>æ¯é¡¹å¸¦ä¸€æ¡å¯å›žå¡«æ˜¾ç¤ºè¿‡æ»¤æ¡çš„ç­‰ä»·è¡¨è¾¾å¼ï¼›è®¡æ•°ä¸º 0 çš„é¡¹ä¸è¿”å›žã€‚</p>
     *
     * @param id ä¼šè¯ ID
     * @param displayFilter æ˜¾ç¤ºè¿‡æ»¤ï¼ˆå¯ç©ºï¼‰
     */
    @Operation(summary = "å¼‚å¸¸è¯Šæ–­")
    /**
     * ç«‹å³æ¸…ç†è¿‡æœŸå’Œè¶…ç©ºé—´çš„æŠ“åŒ…æ•°æ®ï¼ˆä¸ŽåŽå°æ¸…ç†ä»»åŠ¡ä¸€è‡´ï¼Œæ‰‹åŠ¨è§¦å‘ï¼‰ã€‚
     *
     * @return åˆ é™¤çš„ä¼šè¯æ•°
     */
    @Operation(summary = "ç«‹å³æ¸…ç†è¿‡æœŸæŠ“åŒ…æ•°æ®")
    @GetMapping("/retention/cleanup")
    public ReturnResult<Integer> cleanupRetention() {
        return ReturnResult.success(this.tsharkCaptureService.cleanupRetention());
    }
}
