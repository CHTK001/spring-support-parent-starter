mod protocol;
mod server;

use dashmap::DashMap;
use protocol::{RequestMessage, ResponseMessage};
use std::ffi::{CStr, CString, c_char, c_void};
use std::os::raw::c_int;
use std::sync::{Arc, Mutex, Once};
use tokio::runtime::Runtime;
use tokio::sync::oneshot;
use tracing::{error, info};
use tracing_subscriber::FmtSubscriber;

// 回调函数类型定义
// Java 调用此函数来处理 HTTP 请求
type RequestCallback = extern "C" fn(
    request_id: u64,
    method: *const c_char,
    uri: *const c_char,
    headers_json: *const c_char,
    body_data: *const u8,
    body_len: usize,
    user_data: *mut c_void,
) -> c_int;

// 全局状态
struct ServerState {
    runtime: Option<Runtime>,
    callback: Option<RequestCallback>,
    user_data: *mut c_void,
    // 等待响应的请求：request_id -> response_sender
    pending_responses: Arc<DashMap<u64, oneshot::Sender<ResponseMessage>>>,
    server_handle: Option<tokio::task::JoinHandle<()>>,
}

static mut SERVER_STATE: Option<Mutex<ServerState>> = None;
static INIT: Once = Once::new();

fn get_server_state() -> &'static Mutex<ServerState> {
    unsafe {
        INIT.call_once(|| {
            SERVER_STATE = Some(Mutex::new(ServerState {
                runtime: None,
                callback: None,
                user_data: std::ptr::null_mut(),
                pending_responses: Arc::new(DashMap::new()),
                server_handle: None,
            }));
        });
        SERVER_STATE.as_ref().unwrap()
    }
}

/// 启动 HTTP 服务器
/// 
/// # 参数
/// - host: 监听地址，如 "0.0.0.0"
/// - port: 监听端口
/// - callback: Java 回调函数指针
/// - user_data: 传递给回调的用户数据指针
/// 
/// # 返回值
/// - 0: 成功
/// - -1: 失败
#[no_mangle]
pub extern "C" fn rust_server_start(
    host: *const c_char,
    port: u16,
    callback: RequestCallback,
    user_data: *mut c_void,
) -> c_int {
    // 初始化日志
    let _ = FmtSubscriber::builder()
        .with_max_level(tracing::Level::INFO)
        .with_target(false)
        .with_thread_ids(false)
        .compact()
        .try_init();

    info!("[Rust FFI] Starting server on {}:{}", 
        unsafe { CStr::from_ptr(host).to_str().unwrap_or("unknown") }, 
        port
    );

    let state_mutex = get_server_state();
    let mut state = match state_mutex.lock() {
        Ok(s) => s,
        Err(_) => {
            error!("[Rust FFI] Failed to lock server state");
            return -1;
        }
    };

    // 检查是否已启动
    if state.runtime.is_some() {
        error!("[Rust FFI] Server already running");
        return -1;
    }

    // 创建 Runtime
    let runtime = match Runtime::new() {
        Ok(rt) => rt,
        Err(e) => {
            error!("[Rust FFI] Failed to create runtime: {}", e);
            return -1;
        }
    };

    // 保存回调和用户数据
    state.callback = Some(callback);
    state.user_data = user_data;
    let pending_responses = state.pending_responses.clone();

    // 解析参数
    let host_str = unsafe { 
        CStr::from_ptr(host).to_string_lossy().into_owned() 
    };

    // 启动服务器
    let server_handle = runtime.spawn(async move {
        if let Err(e) = server::run_with_callback(
            host_str,
            port,
            pending_responses,
        ).await {
            error!("[Rust FFI] Server error: {}", e);
        }
    });

    state.server_handle = Some(server_handle);
    state.runtime = Some(runtime);

    info!("[Rust FFI] Server started successfully");
    0
}

/// 停止 HTTP 服务器
/// 
/// # 返回值
/// - 0: 成功
/// - -1: 失败
#[no_mangle]
pub extern "C" fn rust_server_stop() -> c_int {
    info!("[Rust FFI] Stopping server");

    let state_mutex = get_server_state();
    let mut state = match state_mutex.lock() {
        Ok(s) => s,
        Err(_) => {
            error!("[Rust FFI] Failed to lock server state");
            return -1;
        }
    };

    // 取消服务器任务
    if let Some(handle) = state.server_handle.take() {
        handle.abort();
    }

    // 关闭 Runtime
    if let Some(runtime) = state.runtime.take() {
        runtime.shutdown_background();
    }

    state.callback = None;
    state.user_data = std::ptr::null_mut();
    state.pending_responses.clear();

    info!("[Rust FFI] Server stopped");
    0
}

/// Java 调用此函数返回 HTTP 响应
/// 
/// # 参数
/// - request_id: 请求 ID
/// - status: HTTP 状态码
/// - headers_json: 响应头 JSON 字符串
/// - body_data: 响应体数据
/// - body_len: 响应体长度
/// 
/// # 返回值
/// - 0: 成功
/// - -1: 失败
#[no_mangle]
pub extern "C" fn rust_server_send_response(
    request_id: u64,
    status: u16,
    headers_json: *const c_char,
    body_data: *const u8,
    body_len: usize,
) -> c_int {
    let state_mutex = get_server_state();
    let state = match state_mutex.lock() {
        Ok(s) => s,
        Err(_) => {
            error!("[Rust FFI] Failed to lock server state");
            return -1;
        }
    };

    // 解析响应头
    let headers = if headers_json.is_null() {
        std::collections::HashMap::new()
    } else {
        let headers_str = unsafe { 
            CStr::from_ptr(headers_json).to_string_lossy() 
        };
        match serde_json::from_str(&headers_str) {
            Ok(h) => h,
            Err(e) => {
                error!("[Rust FFI] Failed to parse headers JSON: {}", e);
                return -1;
            }
        }
    };

    // 复制响应体
    let body = if body_len > 0 && !body_data.is_null() {
        let slice = unsafe { std::slice::from_raw_parts(body_data, body_len) };
        Some(slice.to_vec())
    } else {
        None
    };

    let response = ResponseMessage {
        request_id,
        status,
        headers,
        body,
    };

    // 发送响应到等待的请求
    if let Some((_, sender)) = state.pending_responses.remove(&request_id) {
        if sender.send(response).is_err() {
            error!("[Rust FFI] Failed to send response for request {}", request_id);
            return -1;
        }
    } else {
        error!("[Rust FFI] No pending request for ID {}", request_id);
        return -1;
    }

    0
}

/// 内部函数：调用 Java 回调处理请求
pub(crate) fn invoke_java_callback(request: &RequestMessage) -> anyhow::Result<()> {
    let state_mutex = get_server_state();
    let state = state_mutex.lock().map_err(|_| {
        anyhow::anyhow!("Failed to lock server state")
    })?;

    let callback = state.callback.ok_or_else(|| {
        anyhow::anyhow!("No callback registered")
    })?;

    // 转换参数为 C 字符串
    let method = CString::new(request.method.as_str())?;
    let uri = CString::new(request.uri.as_str())?;
    let headers_json = CString::new(serde_json::to_string(&request.headers)?)?;

    let body_ptr = request.body.as_ref()
        .map(|b| b.as_ptr())
        .unwrap_or(std::ptr::null());
    let body_len = request.body.as_ref()
        .map(|b| b.len())
        .unwrap_or(0);

    // 调用 Java 回调
    let result = callback(
        request.request_id,
        method.as_ptr(),
        uri.as_ptr(),
        headers_json.as_ptr(),
        body_ptr,
        body_len,
        state.user_data,
    );

    if result != 0 {
        anyhow::bail!("Java callback returned error: {}", result);
    }

    Ok(())
}

/// 内部函数：等待 Java 返回响应
pub(crate) async fn wait_for_response(request_id: u64) -> anyhow::Result<ResponseMessage> {
    let (tx, rx) = oneshot::channel();
    
    {
        let state_mutex = get_server_state();
        let state = state_mutex.lock().map_err(|_| {
            anyhow::anyhow!("Failed to lock server state")
        })?;
        state.pending_responses.insert(request_id, tx);
    }

    rx.await.map_err(|_| {
        anyhow::anyhow!("Failed to receive response for request {}", request_id)
    })
}
