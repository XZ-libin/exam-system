<script setup>
import { useRouter } from 'vue-router'
import { useAccountStore } from '../stores/account'

const router = useRouter()
const account = useAccountStore()

function backHome() {
  router.replace(account.homePath)
}
</script>

<template>
  <div class="page forbidden">
    <section class="card forbidden-card">
      <span class="tag tag-quiet">403</span>
      <h1 class="forbidden-title">这个页面不在你的权限范围内</h1>
      <p class="forbidden-sub">换回你自己的页面，继续手上的事情。</p>
      <el-button type="primary" size="large" @click="backHome">回到我的工作台</el-button>
    </section>
  </div>
</template>

<style scoped>
.forbidden {
  display: grid;
  place-items: center;
  min-height: 70vh;
}

.forbidden-card {
  width: 100%;
  max-width: 440px;
  text-align: center;
  padding: 40px 32px 34px;
}

.forbidden-title {
  margin-top: 16px;
  font-size: 22px;
  line-height: 1.3;
}

.forbidden-sub {
  margin: 8px 0 24px;
  font-size: 14px;
  color: var(--ink-3);
}
</style>
