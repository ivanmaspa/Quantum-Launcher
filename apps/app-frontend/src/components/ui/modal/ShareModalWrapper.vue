<script setup lang="ts">
import { ref } from 'vue'
import { ShareModal } from '@modrinth/ui'

import { useTheming } from '@/store/theme.js'

const themeStore = useTheming()

defineProps({
  header: {
    type: String,
    default: 'Share',
  },
  shareTitle: {
    type: String,
    default: 'Modrinth',
  },
  shareText: {
    type: String,
    default: null,
  },
  link: {
    type: Boolean,
    default: false,
  },
  openInNewTab: {
    type: Boolean,
    default: true,
  },
})

const modal = ref(null)

defineExpose({
  show: (passedContent) => {
    modal.value.show(passedContent)
  },
  hide: () => {
    onModalHide()
    modal.value.hide()
  },
})
</script>

<template>
  <ShareModal ref="modal" :header="header" :share-title="shareTitle" :share-text="shareText" :link="link"
    :open-in-new-tab="openInNewTab" :on-hide="onModalHide" :noblur="!themeStore.advancedRendering" />
</template>
