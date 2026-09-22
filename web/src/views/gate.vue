<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAccountStore } from '../stores/account'

const router = useRouter()
const account = useAccountStore()

onMounted(() => {
  router.replace(account.homePath)
})
</script>

<template>
  <div class="page" style="padding-top: 96px; color: var(--ink-3)">正在进入…</div>
</template>
