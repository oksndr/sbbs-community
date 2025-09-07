<template>
  <div v-if="isVisible" id="page-transition-loading">
    <div class="loader-container">
      <div class="loader">
        <div class="ph1">
          <div class="record"></div>
          <div class="record-text">REC</div>
        </div>
        <div class="ph2">
          <div class="laptop-b"></div>
          <svg class="laptop-t" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 42 30">
            <path d="M21 1H5C2.78 1 1 2.78 1 5V25a4 4 90 004 4H37a4 4 90 004-4V5c0-2.22-1.8-4-4-4H21" pathLength="100" stroke-width="2" stroke="currentColor" fill="none"></path>
          </svg>
        </div>
        <div class="icon"></div>
      </div>
      <div class="loading-text">正在加载...</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from '#app'

const isVisible = ref(false)
const router = useRouter()

// 显示加载动画
const show = () => {
  isVisible.value = true
  // 禁止页面滚动
  if (process.client) {
    document.body.classList.add('loading-active')
    document.documentElement.classList.add('loading-active')
  }
}

// 隐藏加载动画
const hide = () => {
  isVisible.value = false
  // 恢复页面滚动
  if (process.client) {
    document.body.classList.remove('loading-active')
    document.documentElement.classList.remove('loading-active')
  }
}

// 页面跳转函数，显示动画然后跳转
const navigateWithAnimation = (url, delay = 800) => {
  return new Promise((resolve) => {
    show()
    
    setTimeout(() => {
      if (process.client) {
        window.location.href = url
      }
      resolve()
    }, delay)
  })
}

// 暴露给外部使用的方法
defineExpose({
  show,
  hide,
  navigateWithAnimation,
  isVisible
})

// 自动监听页面跳转和加载状态
onMounted(() => {
  if (process.client) {
    // 监听 Nuxt 路由变化事件（用于 router.push 等）
    const router = useRouter()
    
    // 全局标志用于控制是否跳过动画
    window._skipNextTransition = false
    
    // 监听路由开始变化
    router.beforeEach((to, from) => {
      if (to.path !== from.path) {
        // 检查是否应该跳过这次动画
        if (!window._skipNextTransition) {
          show()
        }
        // 重置标志
        window._skipNextTransition = false
      }
    })
    
    // 监听路由变化完成
    router.afterEach(() => {
      // 延迟一下确保页面渲染完成
      setTimeout(hide, 100)
    })
    
    // 监听所有页面跳转开始事件（用于 window.location.href）
    window.addEventListener('beforeunload', () => {
      // 检查是否应该跳过动画
      if (!window._skipNextTransition) {
        show()
      }
      // 重置标志
      window._skipNextTransition = false
    })
    
    // 监听页面加载完成事件，自动隐藏动画
    window.addEventListener('load', hide)
    window.addEventListener('DOMContentLoaded', hide)
    
    // 监听页面显示事件（从其他页面返回时）
    window.addEventListener('pageshow', (event) => {
      // 如果是从缓存恢复的页面，立即隐藏动画
      if (event.persisted) {
        hide()
      }
    })
    
    // 监听所有链接点击事件，自动显示动画和设置跳过标志
    document.addEventListener('click', (event) => {
      const link = event.target.closest('a')
      if (link && link.href && !link.href.startsWith('#') && !link.href.includes('javascript:')) {
        // 检查是否是外部链接
        const isExternal = link.hostname !== window.location.hostname
        // 检查是否有特殊属性跳过动画
        const skipAnimation = link.hasAttribute('data-no-transition')
        
        if (!isExternal) {
          if (skipAnimation) {
            // 设置全局标志，下次路由跳转将跳过动画
            window._skipNextTransition = true
          } else {
            show()
          }
        }
      }
    })
    
    // 设置全局方法（保留兼容性）
    window.showPageTransitionLoader = show
    window.hidePageTransitionLoader = hide
    window.navigateWithPageTransition = navigateWithAnimation
    
    // 页面加载完成后，确保隐藏动画
    if (document.readyState === 'complete') {
      hide()
    }
  }
})

onUnmounted(() => {
  if (process.client) {
    window.removeEventListener('beforeunload', show)
    window.removeEventListener('load', hide)
    window.removeEventListener('DOMContentLoaded', hide)
    window.removeEventListener('pageshow', () => {})
    hide() // 确保清理
  }
})
</script>

<style scoped>
#page-transition-loading {
  z-index: 9999999999999;
  background: var(--background, #ffffff);
  position: fixed;
  width: 100vw;
  height: 100vh;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
}

.loader-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

/* From Uiverse.io by SelfMadeSystem */
#page-transition-loading .loader {
  --c: #f7971d;
  position: relative;
  width: 11em;
  height: 7em;
}

#page-transition-loading .ph1 {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  animation: ph1 3s ease infinite;
  clip-path: polygon(-4em -1em, 4em -1em, 4em 1em, -4em 1em);
  z-index: 10;
}

#page-transition-loading .record {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 2em;
  height: 2em;
  background: var(--c);
  border-radius: 999px;
  animation: blink 1s step-end infinite;
  transform: translate(-3.5em, -50%);
}

#page-transition-loading .record-text {
  position: absolute;
  color: var(--c);
  font-size: 2.2em;
  font-weight: 700;
  left: 50%;
  top: 50%;
  transform: translate(-0.5em, -50%);
  width: 2em;
  height: 1.5em;
}

@keyframes blink {
  50% {
    opacity: 0;
  }

  75% {
    opacity: 1;
  }
}

@keyframes ph1 {
  25.5% {
    translate: 0 0;
    clip-path: polygon(-4em -1em, 4em -1em, 4em 1em, -4em 1em);
  }

  30%,
  to {
    opacity: 1;
    translate: 0 3em;
    clip-path: polygon(-4em 1em, 4em 1em, 4em 1em, -4em 1em);
  }

  30.1% {
    opacity: 0;
    translate: 0 3em;
  }

  92.4%,
  to {
    translate: 0 0;
    opacity: 0;
    clip-path: polygon(-4em -1em, 4em -1em, 4em 1em, -4em 1em);
  }

  92.5% {
    opacity: 1;
    clip-path: polygon(-4em -1em, -0.5em -1em, -0.5em 1em, -4em 1em);
  }

  to {
    opacity: 1;
    clip-path: polygon(-4em -1em, 4em -1em, 4em 1em, -4em 1em);
  }
}

#page-transition-loading .ph2 {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -4em);
  width: 11em;
  height: 7em;
  perspective: 150px;
  perspective-origin: 50% 0%;
  transform-style: preserve-3d;
  animation: ph2 3s ease-in-out infinite;
  z-index: 5;
}

@keyframes ph2 {

  0%,
  15% {
    translate: 0 4em;
  }

  0%,
  29% {
    opacity: 0;
  }

  30% {
    opacity: 1;
  }

  40% {
    translate: 0 0;
  }

  50% {
    translate: 0 0.5em;
    opacity: 1;
  }

  50.1%,
  to {
    opacity: 0;
  }
}

#page-transition-loading .laptop-b {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 0.5em;
  background: var(--c);
  border-bottom-left-radius: 2em;
  border-bottom-right-radius: 2em;
  animation: ph2b 3s ease infinite;
}

#page-transition-loading .laptop-t {
  margin: 0 1.25em;
  color: var(--c);
  transform-origin: 50% 100%;
  animation: ph2t 3s ease infinite;
}

@keyframes ph2t {

  0%,
  29% {
    transform: rotateX(-10deg);
  }

  0%,
  41.9% {
    stroke-dasharray: unset;
  }

  42% {
    transform: rotateX(4deg);
    stroke-dasharray: 0 0 100;
  }

  50% {
    transform: rotateX(-20deg);
    stroke-dasharray: 0 50 0 100;
  }
}

@keyframes ph2b {
  42% {
    scale: 1 1;
  }

  50% {
    scale: 0 1;
  }
}

#page-transition-loading .icon {
  position: absolute;
  width: 4em;
  height: 4em;
  background: var(--c);
  border-radius: 999px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  transform-origin: center;
  animation: icon 3s ease-in-out infinite;
  isolation: isolate;
  border-color: var(--c);
  border-style: solid;
  z-index: -1;
}

@keyframes icon {

  0%,
  15% {
    translate: 0 4.5em;
    width: 0;
    height: 0;
  }

  0%,
  29% {
    opacity: 0;
  }

  30% {
    opacity: 1;
  }

  40% {
    translate: 0 -0.75em;
    width: 4em;
    height: 4em;
  }

  50% {
    translate: 0 0em;
    opacity: 1;
    background: var(--c);
  }

  50.1% {
    border-width: 2em;
    background: black;
  }

  65% {
    width: 4em;
    height: 4em;
    transform: translate(-50%, -50%);
    border-width: 4px;
  }

  80%,
  to {
    width: 2em;
    height: 2em;
    translate: 0 0;
    transform: translate(-3.5em, -50%);
    border-width: 1em;
    background: black;
  }

  80.1%,
  to {
    background: var(--c);
  }

  84.9% {
    opacity: 1;
  }

  85%,
  to {
    opacity: 0;
  }
}

#page-transition-loading .icon::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  border: 0.8em solid black;
  box-sizing: border-box;
  border-left-color: transparent !important;
  border-bottom-color: transparent !important;
  transform: translate(-50%, 2.5em) rotate(-45deg);
  transform-origin: center;
  animation: iconb 3s ease-in-out infinite;
  z-index: -1;
}

@keyframes iconb {
  20% {
    transform: translate(-50%, 2.5em) rotate(-45deg);
  }

  50% {
    transform: translate(-50%, -25%) rotate(-45deg);
    border-color: black;
  }

  65%,
  to {
    transform: translateY(0) scale(1) scaleX(1.5) translate(-60%, -50%) rotate(45deg);
    border-color: var(--c);
  }

  85%,
  to {
    transform: translate(-40%, -50%) scale(0) scaleX(1.5) translate(-75%, -50%) rotate(45deg);
  }
}

#page-transition-loading .icon::after {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  background: black;
  width: 1em;
  height: 2em;
  box-sizing: border-box;
  border-left-color: transparent;
  border-bottom-color: transparent;
  animation: icona 3s ease-in-out infinite;
}

@keyframes icona {
  20% {
    transform: translate(-50%, 2.5em);
  }

  50% {
    transform: translate(-50%, 0.4em);
  }

  65%,
  to {
    transform: translate(-50%, 2.5em);
  }
}

.loading-text {
  margin-top: 6rem;
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--c, #f7971d);
  animation: textPulse 2s ease-in-out infinite alternate;
}

@keyframes textPulse {
  0% {
    opacity: 0.6;
  }
  100% {
    opacity: 1;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  #page-transition-loading {
    background: #1a202c;
  }
  
  .loading-text {
    color: #f7971d;
  }
}

/* 确保在任何情况下都完全覆盖屏幕 */
html.loading-active,
body.loading-active {
  overflow: hidden !important;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .loader-container {
    transform: scale(0.8);
  }
  
  .loading-text {
    font-size: 1rem;
    margin-top: 3rem;
  }
}
</style> 