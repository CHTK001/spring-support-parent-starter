use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// Request message sent from Rust to Java
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RequestMessage {
    pub request_id: u64,
    pub method: String,
    pub uri: String,
    pub protocol: String,
    pub headers: HashMap<String, Vec<String>>,
    pub body: Option<Vec<u8>>,
    pub remote_addr: String,
    pub local_addr: String,
}

/// Response message received from Java
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResponseMessage {
    pub request_id: u64,
    pub status: u16,
    pub headers: HashMap<String, Vec<String>>,
    pub body: Option<Vec<u8>>,
}

impl RequestMessage {
    pub fn new(request_id: u64) -> Self {
        Self {
            request_id,
            method: String::new(),
            uri: String::new(),
            protocol: "HTTP/1.1".to_string(),
            headers: HashMap::new(),
            body: None,
            remote_addr: String::new(),
            local_addr: String::new(),
        }
    }

    /// Serialize to MessagePack bytes
    pub fn to_bytes(&self) -> anyhow::Result<Vec<u8>> {
        Ok(rmp_serde::to_vec(self)?)
    }

    /// Deserialize from MessagePack bytes
    pub fn from_bytes(data: &[u8]) -> anyhow::Result<Self> {
        Ok(rmp_serde::from_slice(data)?)
    }
}

impl ResponseMessage {
    /// Serialize to MessagePack bytes
    pub fn to_bytes(&self) -> anyhow::Result<Vec<u8>> {
        Ok(rmp_serde::to_vec(self)?)
    }

    /// Deserialize from MessagePack bytes
    pub fn from_bytes(data: &[u8]) -> anyhow::Result<Self> {
        Ok(rmp_serde::from_slice(data)?)
    }
}
