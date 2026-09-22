<template>
  <!-- 不用 out-in：页面在后台标签里 transitionend 可能不触发，会把新页面卡在挂载前 -->
  <router-view v-slot="{ Component }">
    <transition name="fade">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
