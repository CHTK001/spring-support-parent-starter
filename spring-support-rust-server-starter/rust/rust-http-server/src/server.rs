use crate::protocol::{RequestMessage, ResponseMessage};
use anyhow::Result;
use bytes::Bytes;
use dashmap::DashMap;
use http_body_util::{BodyExt, Full};
use hyper::body::Incoming;
use hyper::server::conn::http1;
use hyper::service::service_fn;
use hyper::{Request, Response, StatusCode};
use hyper_util::rt::TokioIo;
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use tokio::net::TcpListener;
use tokio::sync::oneshot;
use tracing::{debug, error, info};

/// Global request ID counter
static REQUEST_ID: AtomicU64 = AtomicU64::new(1);

/*
// Commented out: Old IPC-based implementation
/// Run the HTTP server
pub async fn run(host: String, port: u16, ipc_addr: String, _max_connections: usize) -> Result<()> {
    // Connect to Java IPC
    let ipc = IpcClient::connect(&ipc_addr).await?;

    // Bind TCP listener
    let addr: SocketAddr = format!("{}:{}", host, port).parse()?;
    let listener = TcpListener::bind(addr).await?;
    info!("HTTP server listening on http://{}", addr);

    let local_addr = listener.local_addr()?.to_string();

    loop {
        let (stream, remote_addr) = listener.accept().await?;
        let ipc = ipc.clone();
        let local_addr = local_addr.clone();

        tokio::spawn(async move {
            let io = TokioIo::new(stream);
            let remote = remote_addr.to_string();
            let local = local_addr.clone();

            let service = service_fn(move |req| {
                let ipc = ipc.clone();
                let remote = remote.clone();
                let local = local.clone();
                async move { handle_request(req, ipc, remote, local).await }
            });

            if let Err(e) = http1::Builder::new()
                .serve_connection(io, service)
                .await
            {
                debug!("Connection error: {}", e);
            }
        });
    }
}

/// Handle a single HTTP request
async fn handle_request(
    req: Request<Incoming>,
    ipc: IpcClient,
    remote_addr: String,
    local_addr: String,
) -> Result<Response<Full<Bytes>>, hyper::Error> {
    let request_id = REQUEST_ID.fetch_add(1, Ordering::Relaxed);

    // Convert Hyper request to IPC message
    let method = req.method().to_string();
    let uri = req.uri().to_string();
    let protocol = format!("{:?}", req.version());

    // Collect headers
    let mut headers: HashMap<String, Vec<String>> = HashMap::new();
    for (name, value) in req.headers() {
        let name = name.to_string();
        let value = value.to_str().unwrap_or("").to_string();
        headers.entry(name).or_default().push(value);
    }

    // Read body
    let body = req.collect().await?.to_bytes();
    let body = if body.is_empty() {
        None
    } else {
        Some(body.to_vec())
    };

    let request_msg = RequestMessage {
        request_id,
        method,
        uri,
        protocol,
        headers,
        body,
        remote_addr,
        local_addr,
    };

    // Send to Java and get response
    match ipc.send_request(request_msg).await {
        Ok(response) => {
            let mut builder = Response::builder().status(StatusCode::from_u16(response.status).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR));

            // Set response headers
            for (name, values) in response.headers {
                for value in values {
                    builder = builder.header(&name, value);
                }
            }

            // Set response body
            let body = response.body.map(Bytes::from).unwrap_or_default();
            Ok(builder.body(Full::new(body)).unwrap())
        }
        Err(e) => {
            error!("Failed to process request {}: {}", request_id, e);
            Ok(Response::builder()
                .status(StatusCode::INTERNAL_SERVER_ERROR)
                .body(Full::new(Bytes::from("Internal Server Error")))
                .unwrap())
        }
    }
}
*/

/// Run the HTTP server with callback mechanism (for FFI)
pub async fn run_with_callback(
    host: String,
    port: u16,
    pending_responses: Arc<DashMap<u64, oneshot::Sender<ResponseMessage>>>,
) -> Result<()> {
    // Bind TCP listener
    let addr: SocketAddr = format!("{}:{}", host, port).parse()?;
    let listener = TcpListener::bind(addr).await?;
    info!("HTTP server listening on http://{}", addr);

    let local_addr = listener.local_addr()?.to_string();

    loop {
        let (stream, remote_addr) = listener.accept().await?;
        let local_addr = local_addr.clone();
        let pending_responses = pending_responses.clone();

        tokio::spawn(async move {
            let io = TokioIo::new(stream);
            let remote = remote_addr.to_string();
            let local = local_addr.clone();

            let service = service_fn(move |req| {
                let remote = remote.clone();
                let local = local.clone();
                let pending_responses = pending_responses.clone();
                async move { handle_request_with_callback(req, remote, local, pending_responses).await }
            });

            if let Err(e) = http1::Builder::new()
                .serve_connection(io, service)
                .await
            {
                debug!("Connection error: {}", e);
            }
        });
    }
}

/// Handle a single HTTP request with callback
async fn handle_request_with_callback(
    req: Request<Incoming>,
    remote_addr: String,
    local_addr: String,
    pending_responses: Arc<DashMap<u64, oneshot::Sender<ResponseMessage>>>,
) -> Result<Response<Full<Bytes>>, hyper::Error> {
    let request_id = REQUEST_ID.fetch_add(1, Ordering::Relaxed);

    // Convert Hyper request to message
    let method = req.method().to_string();
    let uri = req.uri().to_string();
    let protocol = format!("{:?}", req.version());

    // Collect headers
    let mut headers: HashMap<String, Vec<String>> = HashMap::new();
    for (name, value) in req.headers() {
        let name = name.to_string();
        let value = value.to_str().unwrap_or("").to_string();
        headers.entry(name).or_default().push(value);
    }

    // Read body
    let body = req.collect().await?.to_bytes();
    let body = if body.is_empty() {
        None
    } else {
        Some(body.to_vec())
    };

    let request_msg = RequestMessage {
        request_id,
        method,
        uri,
        protocol,
        headers,
        body,
        remote_addr,
        local_addr,
    };

    // Call Java callback
    match crate::invoke_java_callback(&request_msg) {
        Ok(_) => {
            // Wait for Java to send response back
            match crate::wait_for_response(request_id).await {
                Ok(response) => {
                    let mut builder = Response::builder()
                        .status(StatusCode::from_u16(response.status)
                        .unwrap_or(StatusCode::INTERNAL_SERVER_ERROR));

                    // Set response headers
                    for (name, values) in response.headers {
                        for value in values {
                            builder = builder.header(&name, value);
                        }
                    }

                    // Set response body
                    let body = response.body.map(Bytes::from).unwrap_or_default();
                    Ok(builder.body(Full::new(body)).unwrap())
                }
                Err(e) => {
                    error!("Failed to get response for request {}: {}", request_id, e);
                    Ok(Response::builder()
                        .status(StatusCode::INTERNAL_SERVER_ERROR)
                        .body(Full::new(Bytes::from("Internal Server Error")))
                        .unwrap())
                }
            }
        }
        Err(e) => {
            error!("Failed to invoke callback for request {}: {}", request_id, e);
            Ok(Response::builder()
                .status(StatusCode::INTERNAL_SERVER_ERROR)
                .body(Full::new(Bytes::from("Internal Server Error")))
                .unwrap())
        }
    }
}
