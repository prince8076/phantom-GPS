use std::path::Path;

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    pub base_url: String,
    pub model: String,
    pub api_key: String,
    pub max_tokens: u32,
    pub temperature: f32,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            base_url: "https://api.openai.com/v1".to_string(),
            model: "gpt-4o-mini".to_string(),
            api_key: String::new(),
            max_tokens: 2048,
            temperature: 0.2,
        }
    }
}

pub fn load(dir: &Path) -> Settings {
    let path = dir.join("settings.json");
    match std::fs::read(&path) {
        Ok(bytes) => serde_json::from_slice(&bytes).unwrap_or_default(),
        Err(_) => Settings::default(),
    }
}

pub fn save(dir: &Path, settings: &Settings) -> Result<(), String> {
    let base = settings.base_url.trim();
    if !(base.starts_with("http://") || base.starts_with("https://")) {
        return Err("Base URL must start with http:// or https://".to_string());
    }
    if settings.model.trim().is_empty() {
        return Err("Model name cannot be empty.".to_string());
    }
    std::fs::create_dir_all(dir).map_err(|e| format!("Unable to create settings folder: {e}"))?;
    let path = dir.join("settings.json");
    let body = serde_json::to_vec_pretty(settings).map_err(|e| e.to_string())?;
    std::fs::write(&path, body).map_err(|e| format!("Unable to save settings: {e}"))
}
