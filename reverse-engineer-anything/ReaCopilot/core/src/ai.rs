use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::settings::Settings;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMessage {
    pub role: String,
    pub content: String,
}

pub fn system_prompt(context: &str) -> String {
    format!(
        "{}\n\n{}\n{}",
        "You are ReaCopilot, a reverse-engineering copilot embedded in a static-analysis workbench. \
You are given a factual triage report extracted from a binary. Base every statement on that evidence. \
Clearly separate observations (what the data shows) from hypotheses (what it might mean). \
Never invent symbols, addresses, imports, or behavior. Do not claim to have executed or run the binary. \
Prefer concrete, testable next steps: which imports, functions, strings, sections or offsets to inspect. \
If the evidence is insufficient, say exactly what additional data is needed. \
Answer in compact sections with short bullet lists. Address the analyst directly.",
        "=== BINARY CONTEXT ===",
        context
    )
}

pub async fn chat(
    settings: &Settings,
    messages: Vec<ChatMessage>,
    context: &str,
) -> Result<String, String> {
    if messages.is_empty() {
        return Err("No messages were provided.".to_string());
    }
    if context.trim().is_empty() {
        return Err("Analyze a binary before asking the copilot.".to_string());
    }

    let base = settings.base_url.trim().trim_end_matches('/');
    let url = if base.ends_with("/chat/completions") {
        base.to_string()
    } else {
        format!("{base}/chat/completions")
    };

    let mut payload_messages = vec![json!({
        "role": "system",
        "content": system_prompt(context),
    })];
    let history: Vec<&ChatMessage> = messages
        .iter()
        .filter(|m| m.role == "user" || m.role == "assistant")
        .rev()
        .take(16)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect();
    for message in history {
        payload_messages.push(json!({
            "role": message.role,
            "content": message.content,
        }));
    }

    let body = json!({
        "model": settings.model.trim(),
        "messages": payload_messages,
        "temperature": settings.temperature,
        "max_tokens": settings.max_tokens,
        "stream": false,
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(180))
        .build()
        .map_err(|e| format!("Unable to create HTTP client: {e}"))?;

    let mut request = client.post(&url).json(&body);
    let key = settings.api_key.trim();
    if !key.is_empty() {
        request = request.bearer_auth(key);
    }

    let response = request
        .send()
        .await
        .map_err(|e| format!("Could not reach the AI endpoint at {url}: {e}"))?;
    let status = response.status();
    let text = response
        .text()
        .await
        .map_err(|e| format!("Could not read the AI response: {e}"))?;

    if !status.is_success() {
        return Err(format!(
            "AI request failed with HTTP {}: {}",
            status.as_u16(),
            truncate(&text, 700)
        ));
    }

    let value: Value = serde_json::from_str(&text)
        .map_err(|e| format!("AI endpoint returned invalid JSON: {e}"))?;

    if let Some(content) = value["choices"][0]["message"]["content"].as_str() {
        if !content.trim().is_empty() {
            return Ok(content.to_string());
        }
    }
    if let Some(content) = value["message"]["content"].as_str() {
        if !content.trim().is_empty() {
            return Ok(content.to_string());
        }
    }
    if let Some(error) = value["error"]["message"].as_str() {
        return Err(format!("AI endpoint error: {error}"));
    }

    Err(format!(
        "The AI response did not contain any text. Raw response: {}",
        truncate(&text, 500)
    ))
}

fn truncate(text: &str, max: usize) -> String {
    if text.chars().count() <= max {
        return text.to_string();
    }
    let mut out: String = text.chars().take(max).collect();
    out.push_str("...");
    out
}
