<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' }
})

const holder = ref()
const chart = shallowRef()

// 全站统一的图表基调：墨色文字 + 浅灰网格，不使用蓝色块面
const base = {
  animationDuration: 500,
  color: ['#1d1d1f', '#86868b', '#c7c7cc', '#48484a', '#aeaeb2'],
  textStyle: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif',
    color: '#1d1d1f'
  },
  grid: { left: 8, right: 16, top: 28, bottom: 8, containLabel: true },
  tooltip: {
    backgroundColor: 'rgba(255,255,255,.94)',
    borderColor: '#d2d2d7',
    borderWidth: 1,
    textStyle: { color: '#1d1d1f', fontSize: 12 },
    extraCssText: 'border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,.08);'
  }
}

function render() {
  if (!chart.value) return
  chart.value.setOption({ ...base, ...props.option }, true)
}

function resize() {
  chart.value && chart.value.resize()
}

onMounted(() => {
  chart.value = echarts.init(holder.value)
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart.value && chart.value.dispose()
})

watch(() => props.option, render, { deep: true })
</script>

<template>
  <div ref="holder" :style="{ width: '100%', height }" />
</template>
