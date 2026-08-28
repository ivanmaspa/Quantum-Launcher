<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { DiscordIcon } from '@modrinth/assets'
import { ButtonStyled } from '@modrinth/ui'
import { invoke } from '@tauri-apps/api/core'
import { handleError } from '@/store/notifications.js'

interface DiscordLinkConfig {
  bot_token: string | null
  guild_id: string | null
  user_id: string | null
  linked: boolean
  auto_voice: boolean
  voice_channel_id: string | null
}

const cfg = ref<DiscordLinkConfig>({
  bot_token: null,
  guild_id: null,
  user_id: null,
  linked: false,
  auto_voice: false,
  voice_channel_id: null,
})

const botUsername = ref('')
const message = ref('')

async function load() {
  try {
    cfg.value = await invoke('discord_link_get')
  } catch (e) {
    handleError(e)
  }
}

async function save() {
  try {
    await invoke('discord_link_set', {
      botToken: cfg.value.bot_token,
      guildId: cfg.value.guild_id,
      userId: cfg.value.user_id,
      linked: cfg.value.linked,
      autoVoice: cfg.value.auto_voice,
    })
    message.value = 'Сохранено'
  } catch (e) {
    handleError(e)
  }
}

async function checkToken() {
  if (!cfg.value.bot_token) return
  try {
    botUsername.value = await invoke('discord_link_check_token', {
      botToken: cfg.value.bot_token,
    })
    cfg.value.linked = true
    await save()
    message.value = `Бот подключён: ${botUsername.value}`
  } catch (e) {
    handleError(e)
    message.value = ''
  }
}

async function createChannel() {
  try {
    const id = await invoke('discord_create_voice_channel', {
      name: 'Quantum Launcher',
    })
    cfg.value.voice_channel_id = id as string
    await invoke('discord_link_set', {
      botToken: cfg.value.bot_token,
      guildId: cfg.value.guild_id,
      userId: cfg.value.user_id,
      linked: cfg.value.linked,
      autoVoice: cfg.value.auto_voice,
      voiceChannelId: id,
    })
    message.value = `Голосовой канал создан: ${id}`
  } catch (e) {
    handleError(e)
  }
}

async function moveMe() {
  if (!cfg.value.voice_channel_id) {
    message.value = 'Сначала создайте голосовой канал'
    return
  }
  try {
    await invoke('discord_move_to_voice', {
      channelId: cfg.value.voice_channel_id,
    })
    message.value = 'Вы перемещены в голосовой канал'
  } catch (e) {
    handleError(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="flex flex-col gap-4">
    <p class="m-0 text-sm text-secondary">
      Привяжите Discord-бота, чтобы при запуске сервера игроки автоматически
      попадали в голосовой канал. Нужен бот с правами
      <b>Manage Channels</b> и <b>Move Members</b> на вашем сервере.
    </p>

    <div class="flex flex-col gap-1">
      <label class="text-sm font-semibold">Токен бота Discord</label>
      <input
        v-model="cfg.bot_token"
        type="password"
        class="input"
        placeholder="MTk4N...ваш_токен"
        @change="save"
      />
    </div>

    <div class="flex flex-col gap-1">
      <label class="text-sm font-semibold">ID сервера (guild)</label>
      <input v-model="cfg.guild_id" class="input" placeholder="123456789012345678" @change="save" />
    </div>

    <div class="flex flex-col gap-1">
      <label class="text-sm font-semibold">Ваш Discord ID</label>
      <input v-model="cfg.user_id" class="input" placeholder="987654321098765432" @change="save" />
      <span class="text-xs text-secondary">Включите режим разработчика в Discord и ПКМ по себе → CopUser ID.</span>
    </div>

    <div class="flex items-center gap-2">
      <ButtonStyled color="brand" type="standard" @click="checkToken">
        <DiscordIcon /> Проверить и привязать
      </ButtonStyled>
      <span v-if="botUsername" class="text-sm text-brand">✓ {{ botUsername }}</span>
    </div>

    <hr class="border-button-bg" />

    <div class="flex items-center justify-between">
      <div>
        <p class="m-0 font-semibold">Авто-голос при запуске</p>
        <p class="m-0 text-xs text-secondary">
          При запуске инстанса автоматически кидать вас в голосовой канал.
        </p>
      </div>
      <input v-model="cfg.auto_voice" type="checkbox" class="checkbox" @change="save" />
    </div>

    <div class="flex items-center gap-2">
      <ButtonStyled color="brand" type="outlined" @click="createChannel">
        <DiscordIcon /> Создать голосовой канал
      </ButtonStyled>
      <ButtonStyled color="brand" type="transparent" @click="moveMe">
        <DiscordIcon /> Кинуть меня в войс
      </ButtonStyled>
    </div>

    <p v-if="message" class="m-0 text-sm text-brand">{{ message }}</p>
  </div>
</template>
