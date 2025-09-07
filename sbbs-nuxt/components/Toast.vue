<template>
  <teleport to="body">
    <div v-if="toasts.length > 0" class="toast-container">
      <div 
        v-for="toast in toasts" 
        :key="toast.id"
        class="toast"
        :class="[
          `toast-${toast.type}`, 
          { 
            'toast-entering': toast.entering, 
            'toast-leaving': toast.leaving,
            'toast-special': toast.special 
          }
        ]">
        <div class="toast-glow"></div>
        <div class="toast-content">
          <div class="toast-icon-wrapper">
            <i :class="getIcon(toast.type)" class="toast-icon"></i>
          </div>
          <div class="toast-text">
            <span class="toast-message">{{ toast.message }}</span>
          </div>
          <button @click="removeToast(toast.id)" class="toast-close">
            <i class="ri-close-line"></i>
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const toasts = ref([])
let toastId = 0

const getIcon = (type) => {
  const icons = {
    success: 'ri-check-circle-fill',
    error: 'ri-error-warning-fill',
    warning: 'ri-alert-fill',
    info: 'ri-information-fill'
  }
  return icons[type] || icons.info
}

const addToast = async (message, type = 'info', options = {}) => {
  const duration = options.duration || 4000
  const special = options.special || false
  
  const id = ++toastId
  const toast = {
    id,
    message,
    type,
    entering: true,
    leaving: false,
    special
  }
  
  toasts.value.push(toast)
  
  // 动画进入完成
  await nextTick()
  setTimeout(() => {
    toast.entering = false
  }, 150)
  
  // 自动移除
  setTimeout(() => {
    removeToast(id)
  }, duration)
}

const removeToast = async (id) => {
  const toastIndex = toasts.value.findIndex(t => t.id === id)
  if (toastIndex === -1) return
  
  const toast = toasts.value[toastIndex]
  toast.leaving = true
  
  // 等待退出动画完成
  setTimeout(() => {
    toasts.value.splice(toastIndex, 1)
  }, 400)
}

// 导出方法给外部使用
const showToast = {
  success: (message, options) => addToast(message, 'success', options),
  error: (message, options) => addToast(message, 'error', options),
  warning: (message, options) => addToast(message, 'warning', options),
  info: (message, options) => addToast(message, 'info', options)
}

// 全局注册
if (process.client) {
  window.$toast = showToast
}

defineExpose({
  showToast,
  addToast
})
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 10000;
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: none;
  max-width: 420px;
}

.toast {
  position: relative;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(40px) saturate(200%);
  border-radius: 16px;
  box-shadow: 
    0 20px 50px rgba(0, 0, 0, 0.08),
    0 8px 32px rgba(0, 0, 0, 0.04),
    0 4px 16px rgba(0, 0, 0, 0.03),
    0 0 0 1px rgba(255, 255, 255, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  min-width: 340px;
  max-width: 400px;
  pointer-events: auto;
  transform: translateX(calc(100% + 32px)) scale(0.85) rotate(2deg);
  opacity: 0;
  transition: all 0.45s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.toast-glow {
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  border-radius: 18px;
  opacity: 0;
  transition: all 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  pointer-events: none;
  filter: blur(8px);
}

.toast.toast-entering {
  animation: toastSlideIn 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

.toast.toast-leaving {
  animation: toastSlideOut 0.5s cubic-bezier(0.55, 0.055, 0.675, 0.19) forwards;
}

.toast-content {
  display: flex;
  align-items: center;
  padding: 20px 22px;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.toast-icon-wrapper {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  box-shadow: 
    0 4px 12px rgba(0, 0, 0, 0.15),
    0 2px 6px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.toast-icon-wrapper::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 14px;
  opacity: 1;
  background-size: 200% 200%;
  animation: gradientShift 3s ease-in-out infinite;
}

.toast-icon {
  font-size: 20px;
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.2));
}

.toast-text {
  flex: 1;
}

.toast-message {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #1f2937;
  display: block;
  letter-spacing: -0.01em;
}

.toast-close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  cursor: pointer;
  color: #6b7280;
  transition: all 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.toast-close:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #374151;
  transform: scale(1.1) rotate(90deg);
  box-shadow: 
    0 4px 12px rgba(0, 0, 0, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.toast-close:active {
  transform: scale(0.95) rotate(90deg);
}

.toast-close i {
  font-size: 16px;
}

/* 成功类型样式 - 现代化设计 */
.toast-success {
  background: linear-gradient(135deg, 
    rgba(16, 185, 129, 0.03) 0%, 
    rgba(255, 255, 255, 0.98) 25%, 
    rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(16, 185, 129, 0.15);
}

.toast-success .toast-glow {
  background: radial-gradient(circle at 50% 50%, 
    rgba(16, 185, 129, 0.2) 0%, 
    rgba(16, 185, 129, 0.05) 40%, 
    transparent 100%);
}

.toast-success .toast-icon-wrapper::before {
  background: linear-gradient(135deg, 
    #10b981 0%, 
    #059669 50%, 
    #047857 100%);
}

.toast-success .toast-icon {
  color: #ffffff;
}

.toast-success .toast-message {
  color: #047857;
}

.toast-success:hover .toast-glow {
  opacity: 0.8;
}

/* 错误类型样式 - 现代化设计 */
.toast-error {
  background: linear-gradient(135deg, 
    rgba(239, 68, 68, 0.03) 0%, 
    rgba(255, 255, 255, 0.98) 25%, 
    rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(239, 68, 68, 0.15);
}

.toast-error .toast-glow {
  background: radial-gradient(circle at 50% 50%, 
    rgba(239, 68, 68, 0.2) 0%, 
    rgba(239, 68, 68, 0.05) 40%, 
    transparent 100%);
}

.toast-error .toast-icon-wrapper::before {
  background: linear-gradient(135deg, 
    #ef4444 0%, 
    #dc2626 50%, 
    #b91c1c 100%);
}

.toast-error .toast-icon {
  color: #ffffff;
}

.toast-error .toast-message {
  color: #b91c1c;
}

.toast-error:hover .toast-glow {
  opacity: 0.8;
}

/* 警告类型样式 - 现代化设计 */
.toast-warning {
  background: linear-gradient(135deg, 
    rgba(245, 158, 11, 0.03) 0%, 
    rgba(255, 255, 255, 0.98) 25%, 
    rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(245, 158, 11, 0.15);
}

.toast-warning .toast-glow {
  background: radial-gradient(circle at 50% 50%, 
    rgba(245, 158, 11, 0.2) 0%, 
    rgba(245, 158, 11, 0.05) 40%, 
    transparent 100%);
}

.toast-warning .toast-icon-wrapper::before {
  background: linear-gradient(135deg, 
    #f59e0b 0%, 
    #d97706 50%, 
    #b45309 100%);
}

.toast-warning .toast-icon {
  color: #ffffff;
}

.toast-warning .toast-message {
  color: #b45309;
}

.toast-warning:hover .toast-glow {
  opacity: 0.8;
}

/* 信息类型样式 - 现代化设计 */
.toast-info {
  background: linear-gradient(135deg, 
    rgba(59, 130, 246, 0.03) 0%, 
    rgba(255, 255, 255, 0.98) 25%, 
    rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(59, 130, 246, 0.15);
}

.toast-info .toast-glow {
  background: radial-gradient(circle at 50% 50%, 
    rgba(59, 130, 246, 0.2) 0%, 
    rgba(59, 130, 246, 0.05) 40%, 
    transparent 100%);
}

.toast-info .toast-icon-wrapper::before {
  background: linear-gradient(135deg, 
    #3b82f6 0%, 
    #2563eb 50%, 
    #1d4ed8 100%);
}

.toast-info .toast-icon {
  color: #ffffff;
}

.toast-info .toast-message {
  color: #1d4ed8;
}

.toast-info:hover .toast-glow {
  opacity: 0.8;
}

/* 特殊样式（积分奖励等） - 超现代化设计 */
.toast-special {
  background: linear-gradient(135deg, 
    rgba(251, 191, 36, 0.08) 0%,
    rgba(255, 255, 255, 0.95) 20%,
    rgba(255, 255, 255, 0.98) 100%);
  border: 2px solid;
  border-image: linear-gradient(135deg, #fbbf24, #f59e0b, #d97706) 1;
  box-shadow: 
    0 32px 64px rgba(251, 191, 36, 0.15),
    0 16px 32px rgba(0, 0, 0, 0.06),
    0 8px 16px rgba(0, 0, 0, 0.04),
    0 0 0 1px rgba(251, 191, 36, 0.2),
    inset 0 2px 0 rgba(255, 255, 255, 0.8),
    inset 0 0 20px rgba(251, 191, 36, 0.05);
  animation: specialGlow 2s ease-in-out infinite alternate;
}

.toast-special .toast-glow {
  background: radial-gradient(circle at 40% 30%, 
    rgba(251, 191, 36, 0.4) 0%, 
    rgba(251, 191, 36, 0.2) 30%, 
    rgba(251, 191, 36, 0.1) 50%, 
    transparent 100%);
  opacity: 0.7;
  animation: glowPulse 3s ease-in-out infinite;
}

.toast-special .toast-icon-wrapper::before {
  background: linear-gradient(135deg, 
    #fbbf24 0%, 
    #f59e0b 30%, 
    #d97706 60%, 
    #b45309 100%);
  background-size: 300% 300%;
  animation: gradientShift 2s ease-in-out infinite;
}

.toast-special .toast-icon {
  color: #ffffff;
  animation: iconBounce 2s ease-in-out infinite;
}

.toast-special .toast-message {
  background: linear-gradient(135deg, 
    #d97706 0%, 
    #f59e0b 50%, 
    #fbbf24 100%);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  font-weight: 700;
  background-size: 200% 200%;
  animation: gradientShift 3s ease-in-out infinite;
}

/* 核心动画效果 */
@keyframes toastSlideIn {
  0% {
    transform: translateX(calc(100% + 32px)) scale(0.85) rotate(2deg);
    opacity: 0;
  }
  30% {
    transform: translateX(-8px) scale(1.02) rotate(-0.5deg);
    opacity: 0.9;
  }
  70% {
    transform: translateX(2px) scale(1.01) rotate(0.2deg);
    opacity: 0.98;
  }
  100% {
    transform: translateX(0) scale(1) rotate(0deg);
    opacity: 1;
  }
}

@keyframes toastSlideOut {
  0% {
    transform: translateX(0) scale(1) rotate(0deg);
    opacity: 1;
  }
  25% {
    transform: translateX(6px) scale(0.98) rotate(0.5deg);
    opacity: 0.8;
  }
  100% {
    transform: translateX(calc(100% + 32px)) scale(0.85) rotate(2deg);
    opacity: 0;
  }
}

/* 特殊动画效果 */
@keyframes gradientShift {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

@keyframes glowPulse {
  0%, 100% {
    opacity: 0.4;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

@keyframes specialGlow {
  0% {
    box-shadow: 
      0 32px 64px rgba(251, 191, 36, 0.15),
      0 16px 32px rgba(0, 0, 0, 0.06),
      0 8px 16px rgba(0, 0, 0, 0.04),
      0 0 0 1px rgba(251, 191, 36, 0.2),
      inset 0 2px 0 rgba(255, 255, 255, 0.8),
      inset 0 0 20px rgba(251, 191, 36, 0.05);
  }
  100% {
    box-shadow: 
      0 40px 80px rgba(251, 191, 36, 0.2),
      0 20px 40px rgba(0, 0, 0, 0.08),
      0 10px 20px rgba(0, 0, 0, 0.06),
      0 0 0 1px rgba(251, 191, 36, 0.3),
      inset 0 2px 0 rgba(255, 255, 255, 0.9),
      inset 0 0 30px rgba(251, 191, 36, 0.1);
  }
}

@keyframes iconBounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0) scale(1);
  }
  40% {
    transform: translateY(-2px) scale(1.05);
  }
  60% {
    transform: translateY(-1px) scale(1.02);
  }
}

/* 响应式设计 */
@media (max-width: 640px) {
  .toast-container {
    right: 16px;
    left: 16px;
    top: 16px;
    max-width: none;
  }
  
  .toast {
    min-width: auto;
    width: 100%;
    transform: translateY(-120%) scale(0.9);
  }
  
  .toast.toast-entering {
    animation: toastSlideInMobile 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
  }
  
  .toast.toast-leaving {
    animation: toastSlideOutMobile 0.3s cubic-bezier(0.55, 0.055, 0.675, 0.19) forwards;
  }
  
  .toast-content {
    padding: 16px;
    gap: 12px;
  }
  
  .toast-icon-wrapper {
    width: 40px;
    height: 40px;
  }
  
  .toast-icon {
    font-size: 20px;
  }
  
  .toast-message {
    font-size: 14px;
  }
}

@keyframes toastSlideInMobile {
  0% {
    transform: translateY(-120%) scale(0.9);
    opacity: 0;
  }
  60% {
    transform: translateY(8px) scale(1.02);
    opacity: 0.9;
  }
  100% {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
}

@keyframes toastSlideOutMobile {
  0% {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
  100% {
    transform: translateY(-120%) scale(0.9);
    opacity: 0;
  }
}
</style> 