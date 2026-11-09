/*
 * rdpj.dll —— RDP 直连原生桥（FreeRDP 3.x client，C ABI，供 JNA 调用）
 *
 * 设计口径：
 *  - 句柄内持有 FreeRDP 客户端上下文、事件循环线程、RGBA 帧缓存与输入队列；
 *  - 帧同步在 EndPaint 之后按行 diff，只回推变化的行区间，避免整屏 3MB 帧刷网络；
 *  - 键鼠事件由 Java 侧投递到队列，事件循环线程统一提交给 FreeRDP，
 *    避免 FreeRDP 输入 API 的跨线程竞争；
 *  - 不使用 JNI：导出纯 C 符号，由 JNA 按符号名绑定。
 */
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* windows.h 会连带引入 shellapi.h，把 NIIF_* 定义成宏；
 * 而 freerdp/rail.h 用同名枚举成员，两者在一个编译单元里冲突，
 * 这里先把宏撤掉，让 rail.h 的枚举正常展开。 */
#ifdef NIIF_NONE
#undef NIIF_NONE
#undef NIIF_INFO
#undef NIIF_WARNING
#undef NIIF_ERROR
#undef NIIF_NOSOUND
#undef NIIF_LARGE_ICON
#endif

#include <freerdp/freerdp.h>
#include <freerdp/client.h>
#include <freerdp/settings.h>
#include <freerdp/update.h>
#include <freerdp/input.h>
#include <freerdp/gdi/gdi.h>
#include <freerdp/codec/color.h>

#define RDPJ_OK 0
#define RDPJ_ERR_PARAM -1
#define RDPJ_ERR_CONTEXT -2
#define RDPJ_ERR_GDI -3
#define RDPJ_ERR_CONNECT -4

#define RDPJ_QUEUE_SIZE 512

typedef struct RdpjInputEvent {
    int type; /* 1=mouse 2=key */
    int a;
    int b;
    int c;
    int d;
} RdpjInputEvent;

typedef struct RdpjHandle {
    rdpContext* context;
    freerdp* instance;
    HANDLE thread;
    volatile LONG running;
    volatile LONG state; /* 0=连接中 1=已连接 2=已关闭 -1=失败 */
    volatile LONG closed;

    CRITICAL_SECTION lock; /* 保护 fb / header / 脏行区间 */
    BYTE* fb;
    int fbCap;
    int width;
    int height;
    int stride;
    int header[8];
    int dirtyTop;
    int dirtyBottom;

    CRITICAL_SECTION inLock;
    RdpjInputEvent queue[RDPJ_QUEUE_SIZE];
    int qHead;
    int qTail;

    pEndPaint origEndPaint;
    pDesktopResize origDesktopResize;

    int reqWidth;
    int reqHeight;
    int port;
    char host[256];
    char user[256];
    char pass[512];
    char domain[128];
    char locale[64];
    char error[1024];
} RdpjHandle;

typedef struct RdpjContext {
    /* FreeRDP 3.32 起 client-common 要求实例上下文至少是 rdpClientContext 那么大
     * （freerdp_client_context_new 会校验 instance->ContextSize），
     * 所以这里以 rdpClientContext 打头，再挂我们自己的句柄指针。 */
    rdpClientContext client;
    RdpjHandle* handle;
} RdpjContext;

/* FreeRDP 客户端自身不初始化 Winsock（桌面客户端 wf_client.c 是自己调 WSAStartup），
 * 由 JVM 承载时必须由我们把 Winsock 拉起来，否则 getaddrinfo 连字面 IP 都会失败。
 * 进程级只需一次，之后不做 WSACleanup（DLL 卸载不影响 JVM 其它网络栈）。 */
static volatile LONG g_wsaReady = 0;

static void rdpj_wsa_init(void) {
    WSADATA data;
    if (InterlockedCompareExchange(&g_wsaReady, 1, 0) != 0) {
        return;
    }
    if (WSAStartup(MAKEWORD(2, 2), &data) != 0) {
        InterlockedExchange(&g_wsaReady, 0);
    }
}

static void rdpj_set_error(RdpjHandle* h, const char* text) {
    if (h == NULL || text == NULL) {
        return;
    }
    strncpy(h->error, text, sizeof(h->error) - 1);
    h->error[sizeof(h->error) - 1] = '\0';
}

static void rdpj_set_error_from_freeerdp(RdpjHandle* h, const char* stage) {
    char detail[768];
    DWORD code = 0;
    const char* text = NULL;
    detail[0] = '\0';
    if (h != NULL && h->context != NULL) {
        code = freerdp_get_last_error(h->context);
        text = freerdp_get_last_error_string(code);
    }
    if (text != NULL && text[0] != '\0') {
        _snprintf(detail, sizeof(detail) - 1, "%s失败: %s (code=%lu)", stage, text, (unsigned long) code);
    } else {
        _snprintf(detail, sizeof(detail) - 1, "%s失败 (code=%lu)", stage, (unsigned long) code);
    }
    detail[sizeof(detail) - 1] = '\0';
    rdpj_set_error(h, detail);
}

/* 把 GDI 主缓冲按行 diff 到帧缓存，并扩展脏行区间 */
static BOOL rdpj_sync_frame(rdpContext* ctx) {
    RdpjContext* mc = NULL;
    RdpjHandle* h = NULL;
    rdpGdi* gdi = NULL;
    int y;
    int top = -1;
    int bottom = -1;
    int width;
    int height;
    int stride;

    if (ctx == NULL) {
        return FALSE;
    }
    mc = (RdpjContext*) ctx;
    h = mc->handle;
    gdi = ctx->gdi;
    if (h == NULL || gdi == NULL || gdi->primary_buffer == NULL) {
        return TRUE;
    }
    width = gdi->width;
    height = gdi->height;
    stride = (int) gdi->stride;
    if (width <= 0 || height <= 0 || stride <= 0) {
        return TRUE;
    }

    EnterCriticalSection(&h->lock);
    if (h->fb == NULL || h->fbCap < stride * height) {
        BYTE* next = (BYTE*) realloc(h->fb, (size_t) stride * (size_t) height);
        if (next == NULL) {
            LeaveCriticalSection(&h->lock);
            return TRUE;
        }
        h->fb = next;
        h->fbCap = stride * height;
        memset(h->fb, 0, (size_t) h->fbCap);
        top = 0;
        bottom = height - 1;
        memcpy(h->fb, gdi->primary_buffer, (size_t) stride * (size_t) height);
    } else if (h->width != width || h->height != height || h->stride != stride) {
        memset(h->fb, 0, (size_t) h->fbCap);
        top = 0;
        bottom = height - 1;
        memcpy(h->fb, gdi->primary_buffer, (size_t) stride * (size_t) height);
    } else {
        for (y = 0; y < height; y++) {
            const BYTE* src = gdi->primary_buffer + (size_t) y * (size_t) stride;
            BYTE* dst = h->fb + (size_t) y * (size_t) stride;
            if (memcmp(src, dst, (size_t) stride) != 0) {
                memcpy(dst, src, (size_t) stride);
                if (top < 0) {
                    top = y;
                }
                bottom = y;
            }
        }
    }
    h->width = width;
    h->height = height;
    h->stride = stride;
    h->header[0] = width;
    h->header[1] = height;
    h->header[2] = stride;
    h->header[3] = 4;
    if (top >= 0) {
        if (h->dirtyTop < 0 || top < h->dirtyTop) {
            h->dirtyTop = top;
        }
        if (h->dirtyBottom < bottom) {
            h->dirtyBottom = bottom;
        }
    }
    LeaveCriticalSection(&h->lock);
    return TRUE;
}

static BOOL rdpj_end_paint(rdpContext* ctx) {
    RdpjContext* mc = (RdpjContext*) ctx;
    RdpjHandle* h = mc == NULL ? NULL : mc->handle;
    BOOL ret = TRUE;
    if (h != NULL && h->origEndPaint != NULL) {
        ret = h->origEndPaint(ctx);
    }
    rdpj_sync_frame(ctx);
    return ret;
}

static BOOL rdpj_desktop_resize(rdpContext* ctx) {
    RdpjContext* mc = (RdpjContext*) ctx;
    RdpjHandle* h = mc == NULL ? NULL : mc->handle;
    BOOL ret = TRUE;
    rdpSettings* settings = ctx == NULL ? NULL : ctx->settings;
    rdpGdi* gdi = ctx == NULL ? NULL : ctx->gdi;
    if (h != NULL && h->origDesktopResize != NULL) {
        ret = h->origDesktopResize(ctx);
    }
    if (gdi != NULL && settings != NULL) {
        (void) gdi_resize(gdi, freerdp_settings_get_uint32(settings, FreeRDP_DesktopWidth),
                          freerdp_settings_get_uint32(settings, FreeRDP_DesktopHeight));
        rdpj_sync_frame(ctx);
    }
    return ret;
}

static BOOL rdpj_play_sound(rdpContext* ctx, const PLAY_SOUND_UPDATE* playSound) {
    (void) ctx;
    (void) playSound;
    return TRUE;
}

static BOOL rdpj_keyboard_indicators(rdpContext* ctx, UINT16 ledFlags) {
    (void) ctx;
    (void) ledFlags;
    return TRUE;
}

static BOOL rdpj_keyboard_ime(rdpContext* ctx, UINT16 imeId, UINT32 imeState, UINT32 imeConvMode) {
    (void) ctx;
    (void) imeId;
    (void) imeState;
    (void) imeConvMode;
    return TRUE;
}

/* 接受自签证书（内网远控常态；无 CA 体系可用） */
static DWORD rdpj_verify_certificate_ex(freerdp* instance, const char* host, UINT16 port,
                                        const char* commonName, const char* subject,
                                        const char* issuer, const char* fingerprint,
                                        DWORD flags) {
    (void) instance;
    (void) host;
    (void) port;
    (void) commonName;
    (void) subject;
    (void) issuer;
    (void) fingerprint;
    (void) flags;
    return 1;
}

static void rdpj_queue_push(RdpjHandle* h, int type, int a, int b, int c, int d) {
    int next;
    EnterCriticalSection(&h->inLock);
    next = (h->qTail + 1) % RDPJ_QUEUE_SIZE;
    if (next != h->qHead) {
        h->queue[h->qTail].type = type;
        h->queue[h->qTail].a = a;
        h->queue[h->qTail].b = b;
        h->queue[h->qTail].c = c;
        h->queue[h->qTail].d = d;
        h->qTail = next;
    }
    LeaveCriticalSection(&h->inLock);
}

/* 在事件循环线程里提交排队中的键鼠事件 */
static void rdpj_drain_input(RdpjHandle* h) {
    RdpjInputEvent event;
    rdpInput* input;
    if (h == NULL || h->context == NULL) {
        return;
    }
    input = h->context->input;
    if (input == NULL) {
        return;
    }
    for (;;) {
        EnterCriticalSection(&h->inLock);
        if (h->qHead == h->qTail) {
            LeaveCriticalSection(&h->inLock);
            break;
        }
        event = h->queue[h->qHead];
        h->qHead = (h->qHead + 1) % RDPJ_QUEUE_SIZE;
        LeaveCriticalSection(&h->inLock);

        if (event.type == 1) {
            UINT16 flags = 0;
            int x = event.a;
            int y = event.b;
            int button = event.c;
            int down = event.d;
            switch (button) {
                case 1: flags |= PTR_FLAGS_BUTTON1; break;
                case 2: flags |= PTR_FLAGS_BUTTON2; break;
                case 3: flags |= PTR_FLAGS_BUTTON3; break;
                default: flags |= PTR_FLAGS_MOVE; break;
            }
            if (down) {
                flags |= PTR_FLAGS_DOWN;
            }
            (void) freerdp_input_send_mouse_event(input, flags, (UINT16) x, (UINT16) y);
        } else if (event.type == 2) {
            UINT16 flags = event.d ? 0 : KBD_FLAGS_RELEASE;
            int code = event.a;
            if ((code & 0x100) != 0) {
                flags |= KBD_FLAGS_EXTENDED;
            }
            (void) freerdp_input_send_keyboard_event(input, flags, (UINT8) (code & 0xFF));
        } else if (event.type == 3) {
            /* 滚轮：正数向上（0x78），负数向下（0x88 = NEGATIVE | 0x100-0x78） */
            int delta = event.a;
            UINT16 flags = PTR_FLAGS_WHEEL;
            if (delta < 0) {
                flags |= PTR_FLAGS_WHEEL_NEGATIVE | (UINT16) (0x100 - ((-delta) & 0xFF));
            } else {
                flags |= (UINT16) (delta & 0xFF);
            }
            (void) freerdp_input_send_mouse_event(input, flags, (UINT16) event.b, (UINT16) event.c);
        }
    }
}

static int rdpj_apply_settings(RdpjHandle* h) {
    rdpSettings* s;
    if (h == NULL || h->context == NULL) {
        return RDPJ_ERR_CONTEXT;
    }
    s = h->context->settings;
    if (s == NULL) {
        return RDPJ_ERR_CONTEXT;
    }
    if (!freerdp_settings_set_string(s, FreeRDP_ServerHostname, h->host)) {
        return RDPJ_ERR_PARAM;
    }
    freerdp_settings_set_uint32(s, FreeRDP_ServerPort, (UINT32) h->port);
    freerdp_settings_set_string(s, FreeRDP_Username, h->user);
    freerdp_settings_set_string(s, FreeRDP_Password, h->pass);
    freerdp_settings_set_string(s, FreeRDP_Domain, h->domain);
    freerdp_settings_set_uint32(s, FreeRDP_DesktopWidth, (UINT32) h->reqWidth);
    freerdp_settings_set_uint32(s, FreeRDP_DesktopHeight, (UINT32) h->reqHeight);
    freerdp_settings_set_uint32(s, FreeRDP_ColorDepth, 32);
    /* 只取原始位图：关闭依赖 ffmpeg 的 H.264 / AVC444 与 RemoteFX 编解码 */
    freerdp_settings_set_bool(s, FreeRDP_RemoteFxCodec, FALSE);
    freerdp_settings_set_bool(s, FreeRDP_GfxH264, FALSE);
    freerdp_settings_set_bool(s, FreeRDP_GfxAVC444, FALSE);
    freerdp_settings_set_bool(s, FreeRDP_SmartSizing, FALSE);
    freerdp_settings_set_bool(s, FreeRDP_NetworkAutoDetect, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_SupportDisplayControl, FALSE);
    freerdp_settings_set_bool(s, FreeRDP_IgnoreCertificate, TRUE);
    freerdp_settings_set_bool(s, FreeRDP_AutoReconnectionEnabled, FALSE);
    /* 键盘布局：中文区域走 0x804，其余按美式 0x409 */
    if (h->locale[0] != '\0' && _strnicmp(h->locale, "zh", 2) == 0) {
        freerdp_settings_set_uint32(s, FreeRDP_KeyboardLayout, 0x804);
    } else {
        freerdp_settings_set_uint32(s, FreeRDP_KeyboardLayout, 0x409);
    }
    return RDPJ_OK;
}

static BOOL rdpj_pre_connect(freerdp* instance) {
    (void) instance;
    return TRUE;
}

static BOOL rdpj_post_connect(freerdp* instance) {
    rdpContext* ctx;
    RdpjContext* mc;
    RdpjHandle* h;
    if (instance == NULL || instance->context == NULL) {
        return FALSE;
    }
    ctx = instance->context;
    mc = (RdpjContext*) ctx;
    h = mc->handle;
    /* 像素格式名即内存字节序：canvas ImageData 需要 R,G,B,A 顺序，
     * 若用 BGRA32 会红蓝互换（FreeRDP 的命名遵循 X11 一类的内存布局约定）。 */
    if (!gdi_init(instance, PIXEL_FORMAT_RGBA32)) {
        rdpj_set_error(h, "FreeRDP GDI 初始化失败");
        return FALSE;
    }
    if (ctx->update == NULL) {
        rdpj_set_error(h, "FreeRDP 更新通道未就绪");
        return FALSE;
    }
    h->origEndPaint = ctx->update->EndPaint;
    h->origDesktopResize = ctx->update->DesktopResize;
    ctx->update->EndPaint = rdpj_end_paint;
    ctx->update->DesktopResize = rdpj_desktop_resize;
    ctx->update->PlaySound = rdpj_play_sound;
    ctx->update->SetKeyboardIndicators = rdpj_keyboard_indicators;
    ctx->update->SetKeyboardImeStatus = rdpj_keyboard_ime;
    return TRUE;
}

static void rdpj_post_disconnect(freerdp* instance) {
    if (instance == NULL || instance->context == NULL) {
        return;
    }
    gdi_free(instance);
}

static BOOL rdpj_client_new(freerdp* instance, rdpContext* context) {
    if (instance == NULL || context == NULL) {
        return FALSE;
    }
    instance->PreConnect = rdpj_pre_connect;
    instance->PostConnect = rdpj_post_connect;
    instance->PostDisconnect = rdpj_post_disconnect;
    instance->VerifyCertificateEx = rdpj_verify_certificate_ex;
    return TRUE;
}

static void rdpj_client_free(freerdp* instance, rdpContext* context) {
    (void) instance;
    (void) context;
}

static int rdpj_client_start(rdpContext* context) {
    (void) context;
    return 0;
}

static int rdpj_client_stop(rdpContext* context) {
    (void) context;
    return 0;
}

static DWORD WINAPI rdpj_thread_main(LPVOID user) {
    RdpjHandle* h = (RdpjHandle*) user;
    freerdp* instance;
    BOOL connected = FALSE;
    if (h == NULL) {
        return 1;
    }
    if (rdpj_apply_settings(h) != RDPJ_OK) {
        rdpj_set_error(h, "RDP 参数初始化失败");
        InterlockedExchange(&h->state, -1);
        InterlockedExchange(&h->running, 0);
        return 1;
    }
    if (freerdp_client_start(h->context) != 0) {
        rdpj_set_error(h, "RDP 客户端启动失败");
        InterlockedExchange(&h->state, -1);
        InterlockedExchange(&h->running, 0);
        return 1;
    }
    instance = h->instance;
    connected = freerdp_connect(instance);
    if (!connected) {
        rdpj_set_error_from_freeerdp(h, "RDP 连接");
        InterlockedExchange(&h->state, -1);
        InterlockedExchange(&h->running, 0);
        freerdp_client_stop(h->context);
        return 1;
    }
    InterlockedExchange(&h->state, 1);
    while (InterlockedCompareExchange(&h->running, 1, 1) != 0) {
        HANDLE handles[MAXIMUM_WAIT_OBJECTS];
        DWORD count;
        DWORD wait;
        BOOL alive;
        rdpj_drain_input(h);
        count = freerdp_get_event_handles(h->context, handles, MAXIMUM_WAIT_OBJECTS);
        if (count == 0) {
            rdpj_set_error(h, "RDP 事件句柄获取失败");
            break;
        }
        wait = WaitForMultipleObjects(count, handles, FALSE, 20);
        if (wait == WAIT_FAILED) {
            rdpj_set_error(h, "RDP 事件等待失败");
            break;
        }
        alive = freerdp_check_event_handles(h->context);
        if (!alive) {
            if (h->error[0] == '\0') {
                rdpj_set_error_from_freeerdp(h, "RDP 连接中断");
            }
            break;
        }
        if (freerdp_shall_disconnect_context(h->context)) {
            break;
        }
    }
    freerdp_disconnect(instance);
    freerdp_client_stop(h->context);
    InterlockedExchange(&h->running, 0);
    if (InterlockedCompareExchange(&h->closed, 1, 1) == 0) {
        InterlockedExchange(&h->state, -1);
        if (h->error[0] == '\0') {
            rdpj_set_error(h, "RDP 连接已断开");
        }
    }
    return 0;
}

static void rdpj_entry_points(RDP_CLIENT_ENTRY_POINTS* entry) {
    ZeroMemory(entry, sizeof(RDP_CLIENT_ENTRY_POINTS));
    entry->Size = sizeof(RDP_CLIENT_ENTRY_POINTS);
    entry->Version = RDP_CLIENT_INTERFACE_VERSION;
    entry->ContextSize = sizeof(RdpjContext);
    entry->ClientNew = rdpj_client_new;
    entry->ClientFree = rdpj_client_free;
    entry->ClientStart = rdpj_client_start;
    entry->ClientStop = rdpj_client_stop;
}

__declspec(dllexport) void* rdpj_create(void) {
    RdpjHandle* h = (RdpjHandle*) calloc(1, sizeof(RdpjHandle));
    RDP_CLIENT_ENTRY_POINTS entry = { 0 };
    RdpjContext* mc = NULL;
    if (h == NULL) {
        return NULL;
    }
    rdpj_wsa_init();
    InitializeCriticalSection(&h->lock);
    InitializeCriticalSection(&h->inLock);
    h->dirtyTop = -1;
    h->dirtyBottom = -1;
    h->reqWidth = 1024;
    h->reqHeight = 768;
    h->state = 0;

    rdpj_entry_points(&entry);
    h->context = freerdp_client_context_new(&entry);
    if (h->context == NULL) {
        rdpj_set_error(h, "FreeRDP 实例创建失败");
        DeleteCriticalSection(&h->lock);
        DeleteCriticalSection(&h->inLock);
        free(h);
        return NULL;
    }
    h->instance = h->context->instance;
    mc = (RdpjContext*) h->context;
    mc->handle = h;
    return h;
}

__declspec(dllexport) int rdpj_connect(void* handle, const char* host, int port,
                                      const char* user, const char* password, const char* locale) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL || host == NULL || host[0] == '\0' || port <= 0 || port > 65535) {
        return RDPJ_ERR_PARAM;
    }
    if (h->thread != NULL) {
        return RDPJ_ERR_PARAM;
    }
    strncpy(h->host, host, sizeof(h->host) - 1);
    if (user != NULL) {
        strncpy(h->user, user, sizeof(h->user) - 1);
    }
    if (password != NULL) {
        strncpy(h->pass, password, sizeof(h->pass) - 1);
    }
    if (locale != NULL && locale[0] != '\0') {
        strncpy(h->locale, locale, sizeof(h->locale) - 1);
    }
    strncpy(h->domain, ".", sizeof(h->domain) - 1);
    h->port = port;
    h->error[0] = '\0';
    InterlockedExchange(&h->running, 1);
    InterlockedExchange(&h->state, 0);
    h->thread = CreateThread(NULL, 0, rdpj_thread_main, h, 0, NULL);
    if (h->thread == NULL) {
        InterlockedExchange(&h->running, 0);
        InterlockedExchange(&h->state, -1);
        rdpj_set_error(h, "RDP 事件线程创建失败");
        return RDPJ_ERR_CONNECT;
    }
    return RDPJ_OK;
}

__declspec(dllexport) int rdpj_state(void* handle) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return -2;
    }
    return (int) InterlockedCompareExchange(&h->state, 0, 0);
}

__declspec(dllexport) const char* rdpj_last_error(void* handle) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return "RDP 句柄为空";
    }
    return h->error;
}

__declspec(dllexport) void* rdpj_last_frame(void* handle) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL || InterlockedCompareExchange(&h->state, 1, 1) != 1) {
        return NULL;
    }
    if (h->header[0] <= 0 || h->header[1] <= 0) {
        return NULL;
    }
    return h->header;
}

__declspec(dllexport) int rdpj_frame(void* handle, void* buffer, int size, int* outLen) {
    RdpjHandle* h = (RdpjHandle*) handle;
    int top;
    int bottom;
    int rows;
    int bytes;
    if (h == NULL || buffer == NULL || size <= 0 || outLen == NULL) {
        return 0;
    }
    *outLen = 0;
    EnterCriticalSection(&h->lock);
    top = h->dirtyTop;
    bottom = h->dirtyBottom;
    if (top < 0 || bottom < top || h->fb == NULL) {
        LeaveCriticalSection(&h->lock);
        return 0;
    }
    rows = bottom - top + 1;
    bytes = 32 + rows * h->stride;
    if (bytes > size) {
        rows = (size - 32) / (h->stride > 0 ? h->stride : 1);
        if (rows <= 0) {
            LeaveCriticalSection(&h->lock);
            return 0;
        }
        bytes = 32 + rows * h->stride;
    }
    {
        BYTE* out = (BYTE*) buffer;
        int header[8];
        header[0] = h->width;
        header[1] = h->height;
        header[2] = h->stride;
        header[3] = 4;
        header[4] = 0;
        header[5] = top;
        header[6] = h->width;
        header[7] = rows;
        memcpy(out, header, 32);
        memcpy(out + 32, h->fb + (size_t) top * (size_t) h->stride, (size_t) rows * (size_t) h->stride);
    }
    h->dirtyTop = -1;
    h->dirtyBottom = -1;
    LeaveCriticalSection(&h->lock);
    *outLen = bytes;
    return bytes;
}

__declspec(dllexport) int rdpj_mouse(void* handle, int x, int y, int button, int down) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return RDPJ_ERR_PARAM;
    }
    rdpj_queue_push(h, 1, x, y, button, down);
    return 0;
}

__declspec(dllexport) int rdpj_key(void* handle, short code, int down) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return RDPJ_ERR_PARAM;
    }
    rdpj_queue_push(h, 2, (int) (unsigned short) code, 0, 0, down);
    return 0;
}

__declspec(dllexport) int rdpj_wheel(void* handle, int x, int y, int delta) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return RDPJ_ERR_PARAM;
    }
    rdpj_queue_push(h, 3, delta, x, y, 0);
    return 0;
}

__declspec(dllexport) int rdpj_resize(void* handle, int width, int height) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL || width <= 0 || height <= 0) {
        return RDPJ_ERR_PARAM;
    }
    /* 动态尺寸需要 display control 通道，当前版本不启用：记录目标尺寸后忽略 */
    h->reqWidth = width;
    h->reqHeight = height;
    return 0;
}

__declspec(dllexport) void rdpj_close(void* handle) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return;
    }
    InterlockedExchange(&h->running, 0);
    if (h->thread != NULL) {
        WaitForSingleObject(h->thread, 5000);
        CloseHandle(h->thread);
        h->thread = NULL;
    }
    InterlockedExchange(&h->closed, 1);
    InterlockedExchange(&h->state, 2);
}

__declspec(dllexport) void rdpj_destroy(void* handle) {
    RdpjHandle* h = (RdpjHandle*) handle;
    if (h == NULL) {
        return;
    }
    rdpj_close(h);
    if (h->context != NULL) {
        freerdp_client_context_free(h->context);
        h->context = NULL;
        h->instance = NULL;
    }
    EnterCriticalSection(&h->lock);
    if (h->fb != NULL) {
        free(h->fb);
        h->fb = NULL;
        h->fbCap = 0;
    }
    LeaveCriticalSection(&h->lock);
    DeleteCriticalSection(&h->lock);
    DeleteCriticalSection(&h->inLock);
    free(h);
}
