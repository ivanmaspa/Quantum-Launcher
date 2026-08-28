//! Discord linkage config + Discord API helpers (voice channels, member move)
//! Stored as a JSON file in the launcher settings dir to avoid DB migrations.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

use crate::state::dirs::DirectoryInfo;
use crate::util::fetch::REQWEST_CLIENT;

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct DiscordLinkConfig {
    pub bot_token: Option<String>,
    pub guild_id: Option<String>,
    pub user_id: Option<String>,
    pub linked: bool,
    pub auto_voice: bool,
    pub voice_channel_id: Option<String>,
}

const FILE: &str = "discord_link.json";

fn path() -> Option<PathBuf> {
    DirectoryInfo::get_initial_settings_dir().map(|d| d.join(FILE))
}

pub fn load() -> DiscordLinkConfig {
    if let Some(p) = path() {
        if let Ok(s) = std::fs::read_to_string(&p) {
            if let Ok(c) = serde_json::from_str::<DiscordLinkConfig>(&s) {
                return c;
            }
        }
    }
    DiscordLinkConfig::default()
}

pub fn save(cfg: &DiscordLinkConfig) -> crate::Result<()> {
    if let Some(p) = path() {
        if let Some(parent) = p.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::write(&p, serde_json::to_string_pretty(cfg)?)?;
    }
    Ok(())
}

/// Создать голосовой канал на сервере Discord. Возвращает id канала.
pub async fn create_voice_channel(
    bot_token: &str,
    guild_id: &str,
    name: &str,
) -> crate::Result<String> {
    let res = REQWEST_CLIENT
        .post(format!(
            "https://discord.com/api/v10/guilds/{guild_id}/channels"
        ))
        .header("Authorization", format!("Bot {bot_token}"))
        .json(&serde_json::json!({ "name": name, "type": 2 }))
        .send()
        .await
        .map_err(|e| crate::ErrorKind::OtherError(format!("Discord API error: {e}")))?;

    let status = res.status();
    let body: serde_json::Value = res
        .json()
        .await
        .map_err(|e| crate::ErrorKind::OtherError(format!("Discord API parse error: {e}")))?;

    if !status.is_success() {
        let msg = body
            .get("message")
            .and_then(|m| m.as_str())
            .unwrap_or("unknown error")
            .to_string();
        return Err(crate::ErrorKind::OtherError(format!(
            "Не удалось создать канал ({status}): {msg}"
        ))
        .into());
    }

    body.get("id")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| crate::ErrorKind::OtherError("Discord не вернул id канала".to_string()).into())
}

/// Переместить пользователя в голосовой канал (нужен бот с правом Move Members).
pub async fn move_member_to_voice(
    bot_token: &str,
    guild_id: &str,
    user_id: &str,
    channel_id: &str,
) -> crate::Result<()> {
    let res = REQWEST_CLIENT
        .patch(format!(
            "https://discord.com/api/v10/guilds/{guild_id}/members/{user_id}"
        ))
        .header("Authorization", format!("Bot {bot_token}"))
        .json(&serde_json::json!({ "channel_id": channel_id }))
        .send()
        .await
        .map_err(|e| crate::ErrorKind::OtherError(format!("Discord API error: {e}")))?;

    if !res.status().is_success() {
        let body: serde_json::Value = res
            .json()
            .await
            .unwrap_or(serde_json::Value::Null);
        let msg = body
            .get("message")
            .and_then(|m| m.as_str())
            .unwrap_or("unknown error")
            .to_string();
        return Err(crate::ErrorKind::OtherError(format!(
            "Не удалось переместить в войс: {msg}"
        ))
        .into());
    }

    Ok(())
}

/// Получить информацию о боте (проверка токена).
pub async fn check_bot_token(bot_token: &str) -> crate::Result<String> {
    let res = REQWEST_CLIENT
        .get("https://discord.com/api/v10/users/@me")
        .header("Authorization", format!("Bot {bot_token}"))
        .send()
        .await
        .map_err(|e| crate::ErrorKind::OtherError(format!("Discord API error: {e}")))?;

    let body: serde_json::Value = res
        .json()
        .await
        .map_err(|e| crate::ErrorKind::OtherError(format!("Discord API parse error: {e}")))?;

    body.get("username")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| {
            crate::ErrorKind::OtherError("Неверный токен бота Discord".to_string()).into()
        })
}
