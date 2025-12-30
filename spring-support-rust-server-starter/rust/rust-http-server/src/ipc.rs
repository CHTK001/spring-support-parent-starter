use crate::protocol::{RequestMessage, ResponseMessage};
use anyhow::{anyhow, Result};
use dashmap::DashMap;
use std::sync::Arc;
use tokio::io::{AsyncReadExt, AsyncWriteExt, BufReader, BufWriter};
use tokio::net::TcpStream;
use tokio::sync::{oneshot, Mutex};
use tracing::{debug, error, info};

/// IPC Client for communicating with Java
pub struct IpcClient {
    writer: Arc<Mutex<BufWriter<tokio::io::WriteHalf<TcpStream>>>>,
    pending_requests: Arc<DashMap<u64, oneshot::Sender<ResponseMessage>>>,
}

impl IpcClient {
    /// Connect to Java IPC server
    pub async fn connect(ipc_addr: &str) -> Result<Self> {
        // Parse IPC address (e.g., "tcp://127.0.0.1:12345")
        let addr = ipc_addr
            .strip_prefix("tcp://")
            .ok_or_else(|| anyhow!("Invalid IPC address format: {}", ipc_addr))?;

        info!("Connecting to Java IPC server at {}...", addr);

        let stream = TcpStream::connect(addr).await?;
        stream.set_nodelay(true)?;

        let (reader, writer) = tokio::io::split(stream);
        let reader = BufReader::new(reader);
        let writer = BufWriter::new(writer);

        let pending_requests: Arc<DashMap<u64, oneshot::Sender<ResponseMessage>>> =
            Arc::new(DashMap::new());

        let client = Self {
            writer: Arc::new(Mutex::new(writer)),
            pending_requests: pending_requests.clone(),
        };

        // Start response reader task
        let pending = pending_requests.clone();
        tokio::spawn(async move {
            if let Err(e) = Self::read_responses(reader, pending).await {
                error!("IPC response reader error: {}", e);
            }
        });

        info!("Connected to Java IPC server");
        Ok(client)
    }

    /// Send request to Java and wait for response
    pub async fn send_request(&self, request: RequestMessage) -> Result<ResponseMessage> {
        let request_id = request.request_id;

        // Create response channel
        let (tx, rx) = oneshot::channel();
        self.pending_requests.insert(request_id, tx);

        // Serialize and send request
        let data = request.to_bytes()?;
        let len_bytes = (data.len() as u32).to_le_bytes();

        {
            let mut writer = self.writer.lock().await;
            writer.write_all(&len_bytes).await?;
            writer.write_all(&data).await?;
            writer.flush().await?;
        }

        debug!("Sent request {} to Java", request_id);

        // Wait for response
        let response = rx.await.map_err(|_| anyhow!("Response channel closed"))?;
        
        debug!("Received response for request {}", request_id);
        Ok(response)
    }

    /// Read responses from Java
    async fn read_responses(
        mut reader: BufReader<tokio::io::ReadHalf<TcpStream>>,
        pending: Arc<DashMap<u64, oneshot::Sender<ResponseMessage>>>,
    ) -> Result<()> {
        loop {
            // Read message length (4 bytes, little-endian)
            let mut len_bytes = [0u8; 4];
            if reader.read_exact(&mut len_bytes).await.is_err() {
                info!("IPC connection closed");
                break;
            }
            let len = u32::from_le_bytes(len_bytes) as usize;

            if len == 0 || len > 10 * 1024 * 1024 {
                error!("Invalid message length: {}", len);
                continue;
            }

            // Read message body
            let mut data = vec![0u8; len];
            reader.read_exact(&mut data).await?;

            // Deserialize response
            match ResponseMessage::from_bytes(&data) {
                Ok(response) => {
                    let request_id = response.request_id;
                    if let Some((_, tx)) = pending.remove(&request_id) {
                        let _ = tx.send(response);
                    } else {
                        debug!("No pending request for response {}", request_id);
                    }
                }
                Err(e) => {
                    error!("Failed to deserialize response: {}", e);
                }
            }
        }
        Ok(())
    }
}

impl Clone for IpcClient {
    fn clone(&self) -> Self {
        Self {
            writer: self.writer.clone(),
            pending_requests: self.pending_requests.clone(),
        }
    }
}
