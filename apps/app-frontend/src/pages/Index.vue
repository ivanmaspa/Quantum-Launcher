<script setup>
import { ref, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import RowDisplay from '@/components/RowDisplay.vue'
import { list, run } from '@/helpers/profile.js'
import { profile_listener } from '@/helpers/events'
import { useBreadcrumbs } from '@/store/breadcrumbs'
import { handleError } from '@/store/notifications.js'
import dayjs from 'dayjs'
import { get_search_results } from '@/helpers/cache.js'
import { get as getCreds } from '@/helpers/mr_auth.js'
import { PlayIcon, PlusIcon, CompassIcon } from '@modrinth/assets'
import { ButtonStyled } from '@modrinth/ui'

const featuredModpacks = ref({})
const featuredMods = ref({})
const filter = ref('')

const route = useRoute()
const breadcrumbs = useBreadcrumbs()

breadcrumbs.setRootContext({ name: 'Home', link: route.path })

const recentInstances = ref([])
const userName = ref('')

const getUserName = async () => {
  const creds = await getCreds().catch(() => null)
  if (creds && creds.user && creds.user.username) {
    userName.value = creds.user.username
  }
}
getUserName()

const offset = ref(1)
const scrolling = ref(false)
const heroRef = ref(null)

const handleScroll = () => {
  const el = document.querySelector('.app-viewport')
  if (!el) return
  if (el.scrollTop < 60) {
    offset.value = 1
    scrolling.value = false
  } else {
    scrolling.value = true
    if (!scrolling.value) offset.value = 1
    offset.value = Math.max(0.88, 1 - Math.min(el.scrollTop - 60, 300) / 300)
  }
}

const playLast = async () => {
  if (recentInstances.value.length > 0) {
    await run(recentInstances.value[0].path).catch(handleError)
  } else {
    window.location.hash = '#/browse/modpack'
  }
}

const createInstance = () => {
  document.dispatchEvent(new CustomEvent('open-instance-creation'))
}

const offline = ref(!navigator.onLine)
window.addEventListener('offline', () => {
  offline.value = true
})
window.addEventListener('online', () => {
  offline.value = false
})

const getInstances = async () => {
  const profiles = await list().catch(handleError)

  recentInstances.value = profiles
    .filter((x) => x.last_played)
    .sort((a, b) => {
      const dateA = dayjs(a.last_played)
      const dateB = dayjs(b.last_played)

      if (dateA.isSame(dateB)) {
        return a.name.localeCompare(b.name)
      }

      return dateB - dateA
    })

  const filters = []
  for (const instance of profiles) {
    if (instance.linked_data && instance.linked_data.project_id) {
      filters.push(`NOT"project_id"="${instance.linked_data.project_id}"`)
    }
  }
  filter.value = filters.join(' AND ')
}

const getFeaturedModpacks = async () => {
  const response = await get_search_results(
    `?facets=[["project_type:modpack"]]&limit=10&index=follows&filters=${filter.value}`,
  )

  if (response) {
    featuredModpacks.value = response.result.hits
  } else {
    featuredModpacks.value = []
  }
}
const getFeaturedMods = async () => {
  const response = await get_search_results('?facets=[["project_type:mod"]]&limit=10&index=follows')

  if (response) {
    featuredMods.value = response.result.hits
  } else {
    featuredModpacks.value = []
  }
}

await getInstances()

await Promise.all([getFeaturedModpacks(), getFeaturedMods()])

const unlistenProfile = await profile_listener(async (e) => {
  await getInstances()

  if (e.event === 'added' || e.event === 'created' || e.event === 'removed') {
    await Promise.all([getFeaturedModpacks(), getFeaturedMods()])
  }
})

// computed sums of recentInstances, featuredModpacks, featuredMods, treating them as arrays if they are not
const total = computed(() => {
  return (
    (recentInstances.value?.length ?? 0) +
    (featuredModpacks.value?.length ?? 0) +
    (featuredMods.value?.length ?? 0)
  )
})

onUnmounted(() => {
  unlistenProfile()
  document.removeEventListener('scroll', handleScroll, true)
})
</script>

<template>
  <div class="p-6 flex flex-col gap-4">
    <section ref="heroRef" class="hero-card">
      <div class="hero-glow" style="background: var(--brand-gradient-bg)"></div>
      <div class="hero-content relative">
        <div class="flex items-center gap-3 mb-3">
          <div class="hero-badge">
            <PlayIcon class="w-5 h-5" />
          </div>
          <span class="hero-eyebrow">Quantum Launcher</span>
        </div>
        <h1 class="m-0 hero-title">
          <span v-if="userName">С возвращением, {{ userName }}!</span>
          <span v-else-if="recentInstances.length">С возвращением!</span>
          <span v-else>Добро пожаловать в Quantum Launcher!</span>
        </h1>
        <p class="hero-subtitle m-0">
          Лёгкий и быстрый лаунчер Minecraft с поддержкой оффлайн-режима и модов.
        </p>
        <div class="flex items-center gap-3 mt-5">
          <ButtonStyled color="brand" type="standard">
            <button class="m-0" @click="playLast">
              <PlayIcon class="w-5 h-5" />
              <span>{{ recentInstances.length ? 'Играть' : 'Найти модпаки' }}</span>
            </button>
          </ButtonStyled>
          <ButtonStyled color="brand" type="outlined">
            <button class="m-0" @click="createInstance">
              <PlusIcon class="w-5 h-5" />
              <span>Новый инстанс</span>
            </button>
          </ButtonStyled>
          <ButtonStyled color="brand" type="transparent">
            <button class="m-0" @click="window.location.hash = '#/library'">
              <CompassIcon class="w-5 h-5" />
              <span>Библиотека</span>
            </button>
          </ButtonStyled>
        </div>
      </div>
    </section>

    <RowDisplay
      v-if="total > 0"
      :instances="[
        {
          label: 'Недавно играли',
          route: '/library',
          instances: recentInstances,
          instance: true,
          downloaded: true,
          compact: true,
        },
        {
          label: 'Откройте модпаки',
          route: '/browse/modpack',
          instances: featuredModpacks,
          downloaded: false,
        },
        {
          label: 'Откройте моды',
          route: '/browse/mod',
          instances: featuredMods,
          downloaded: false,
        },
      ]"
      :can-paginate="true"
    />
  </div>
</template>

<style scoped>
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-xl);
  border: 1px solid var(--brand-gradient-border);
  background: var(--color-raised-bg);
}
.hero-glow {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.hero-content {
  padding: 2rem 2.25rem;
}
.hero-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: var(--radius-md);
  color: var(--color-accent-contrast);
  background: var(--color-brand);
  box-shadow: 0 0 24px var(--color-brand-shadow);
}
.hero-eyebrow {
  font-size: 0.85rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-brand);
}
.hero-title {
  font-size: 2rem;
  font-weight: 800;
  line-height: 1.15;
  color: var(--color-contrast);
}
.hero-subtitle {
  color: var(--color-secondary);
  max-width: 34rem;
}
</style>
