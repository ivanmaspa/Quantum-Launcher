use crate::api::Result;
use tauri::plugin::TauriPlugin;
use tauri::Runtime;

use theseus::state::discord_link::{
    check_bot_token, create_voice_channel, load, move_member_to_voice, save, DiscordLinkConfig,
};

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    tauri::plugin::Builder::<R>::new("discord_link")
        .invoke_handler(tauri::generate_handler![
            discord_link_get,
            discord_link_set,
            discord_link_check_token,
            discord_create_voice_channel,
            discord_move_to_voice,
        ])
        .build()
}

#[tauri::command]
pub async fn discord_link_get() -> Result<DiscordLinkConfig> {
    Ok(load())
}

#[tauri::command]
pub async fn discord_link_set(
    bot_token: Option<String>,
    guild_id: Option<String>,
    user_id: Option<String>,
    linked: Option<bool>,
    auto_voice: Option<bool>,
) -> Result<()> {
    let mut cfg = load();
    if let Some(v) = bot_token {
        cfg.bot_token = if v.is_empty() { None } else { Some(v) };
    }
    if let Some(v) = guild_id {
        cfg.guild_id = if v.is_empty() { None } else { Some(v) };
    }
    if let Some(v) = user_id {
        cfg.user_id = if v.is_empty() { None } else { Some(v) };
    }
    if let Some(v) = linked {
        cfg.linked = v;
    }
    if let Some(v) = auto_voice {
        cfg.auto_voice = v;
    }
    save(&cfg)?;
    Ok(())
}

#[tauri::command]
pub async fn discord_link_check_token(bot_token: String) -> Result<String> {
    Ok(check_bot_token(&bot_token).await?)
}

#[tauri::command]
pub async fn discord_create_voice_channel(name: String) -> Result<String> {
    let cfg = load();
    let token = cfg
        .bot_token
        .ok_or_else(|| theseus::Error::from(theseus::ErrorKind::OtherError("Не задан токен бота Discord".to_string())))?;
    let guild = cfg
        .guild_id
        .ok_or_else(|| theseus::Error::from(theseus::ErrorKind::OtherError("Не задан ID сервера Discord".to_string())))?;
    Ok(create_voice_channel(&token, &guild, &name).await?)
}

#[tauri::command]
pub async fn discord_move_to_voice(channel_id: String) -> Result<()> {
    let cfg = load();
    let token = cfg
        .bot_token
        .ok_or_else(|| theseus::Error::from(theseus::ErrorKind::OtherError("Не задан токен бота Discord".to_string())))?;
    let guild = cfg
        .guild_id
        .ok_or_else(|| theseus::Error::from(theseus::ErrorKind::OtherError("Не задан ID сервера Discord".to_string())))?;
    let user = cfg
        .user_id
        .ok_or_else(|| theseus::Error::from(theseus::ErrorKind::OtherError("Не привязан пользователь Discord".to_string())))?;
    Ok(move_member_to_voice(&token, &guild, &user, &channel_id).await?)
}
