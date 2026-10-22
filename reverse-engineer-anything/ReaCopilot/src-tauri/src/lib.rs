use std::path::{Path, PathBuf};

use reacopilot_core::ai::ChatMessage;
use reacopilot_core::analysis::BinaryReport;
use reacopilot_core::settings::{self, Settings};
use tauri::Manager;

fn config_dir(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app
        .path()
        .app_config_dir()
        .map_err(|e| format!("Unable to resolve the config directory: {e}"))?;
    std::fs::create_dir_all(&dir)
        .map_err(|e| format!("Unable to create the config directory: {e}"))?;
    Ok(dir)
}

#[tauri::command]
async fn browse_binary() -> Option<String> {
    tauri::async_runtime::spawn_blocking(|| {
        rfd::FileDialog::new()
            .set_title("Open a binary for analysis")
            .add_filter(
                "Executables and libraries",
                &[
                    "exe", "dll", "sys", "so", "elf", "dylib", "bin", "o", "apk", "jar", "com",
                    "scr",
                ],
            )
            .add_filter("All files", &["*"])
            .pick_file()
            .map(|path| path.to_string_lossy().to_string())
    })
    .await
    .ok()
    .flatten()
}

#[tauri::command]
async fn analyze_file(path: String) -> Result<BinaryReport, String> {
    tauri::async_runtime::spawn_blocking(move || {
        reacopilot_core::analysis::analyze_path(Path::new(&path))
    })
    .await
    .map_err(|e| format!("Analysis task failed: {e}"))?
}

#[tauri::command]
fn build_ai_context(report: BinaryReport) -> String {
    reacopilot_core::analysis::build_context(&report)
}

#[tauri::command]
async fn ai_chat(
    app: tauri::AppHandle,
    messages: Vec<ChatMessage>,
    context: String,
) -> Result<String, String> {
    let current = settings::load(&config_dir(&app)?);
    reacopilot_core::ai::chat(&current, messages, &context).await
}

#[tauri::command]
fn get_settings(app: tauri::AppHandle) -> Result<Settings, String> {
    Ok(settings::load(&config_dir(&app)?))
}

#[tauri::command]
fn save_settings(app: tauri::AppHandle, settings: Settings) -> Result<(), String> {
    settings::save(&config_dir(&app)?, &settings)
}

#[tauri::command]
async fn export_report(default_name: String, content: String) -> Result<Option<String>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let target = rfd::FileDialog::new()
            .set_title("Export ReaCopilot report")
            .set_file_name(&default_name)
            .add_filter("Markdown", &["md"])
            .save_file();
        match target {
            Some(path) => {
                std::fs::write(&path, content)
                    .map_err(|e| format!("Unable to write the report: {e}"))?;
                Ok::<Option<String>, String>(Some(path.to_string_lossy().to_string()))
            }
            None => Ok::<Option<String>, String>(None),
        }
    })
    .await
    .map_err(|e| format!("Export task failed: {e}"))?
}

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            browse_binary,
            analyze_file,
            build_ai_context,
            ai_chat,
            get_settings,
            save_settings,
            export_report
        ])
        .run(tauri::generate_context!())
        .expect("error while running ReaCopilot");
}
