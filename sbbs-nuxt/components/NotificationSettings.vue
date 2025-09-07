<template>
  <div v-if="show" class="notification-settings-overlay" @click="handleOverlayClick">
    <div class="notification-settings-modal" @click.stop>
      <div class="modal-header">
        <h3>设置</h3>
        <button class="close-btn" @click="$emit('close')" title="关闭">
          <i class="ri-close-line"></i>
        </button>
      </div>
      
      <div class="modal-content">
        <div v-if="loading" class="loading-state">
          <i class="ri-loader-4-line rotating"></i>
          <span>加载中...</span>
        </div>
        
        <div v-else class="settings-list">
          <!-- 通知设置分组 -->
          <div class="settings-group">
            <div class="group-header">
              <h4 class="group-title">通知偏好</h4>
              <button 
                class="btn btn-save-small" 
                @click="saveSettings"
                :disabled="updating || loading"
              >
                <i v-if="updating" class="ri-loader-4-line rotating"></i>
                <i v-else class="ri-save-line"></i>
                {{ updating ? '保存中...' : '保存' }}
              </button>
            </div>
            
            <div class="setting-item">
            <div class="setting-info">
              <h4>点赞通知</h4>
              <p>当有人点赞你的帖子或评论时通知你</p>
            </div>
            <label class="switch">
              <input 
                type="checkbox" 
                v-model="settings.enableLikeNotification"
                :disabled="updating"
              >
              <span class="slider"></span>
            </label>
          </div>
          
          <div class="setting-item">
            <div class="setting-info">
              <h4>其他通知</h4>
              <p>接收系统消息、回复、提及等所有其他通知</p>
            </div>
            <label class="switch">
              <input 
                type="checkbox" 
                v-model="settings.enableOtherNotification"
                :disabled="updating"
              >
              <span class="slider"></span>
            </label>
          </div>
          
          <div v-if="error" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ error }}
          </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useUserStore } from '~/stores/user'

const props = defineProps({
  show: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'updated'])

const userStore = useUserStore()
const { $api } = useNuxtApp()

// 响应式数据
const settings = ref({
  enableLikeNotification: true,
  enableOtherNotification: true
})

const originalSettings = ref({
  enableLikeNotification: true,
  enableOtherNotification: true
})

const loading = ref(false)
const updating = ref(false)
const error = ref('')

// 获取通知设置
const fetchSettings = async () => {
  if (!userStore.token) {
    error.value = '用户未登录'
    return
  }
  
  loading.value = true
  error.value = ''
  
  try {
    const response = await fetch('/api/api/user/notifications/settings', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${userStore.token}`,
        'Content-Type': 'application/json'
      }
    })
    
    const data = await response.json()
    
    if (data.code === 200) {
      settings.value = {
        enableLikeNotification: data.data.enableLikeNotification,
        enableOtherNotification: data.data.enableOtherNotification
      }
      // 保存原始设置
      originalSettings.value = { ...settings.value }
    } else {
      error.value = data.msg || '获取设置失败'
    }
  } catch (err) {
    console.error('获取通知设置失败:', err)
    error.value = '网络错误，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 保存通知设置
const saveSettings = async () => {
  if (!userStore.token || updating.value) {
    return
  }
  
  updating.value = true
  error.value = ''
  
  try {
    const response = await fetch('/api/api/user/notifications/settings', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${userStore.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        enableLikeNotification: settings.value.enableLikeNotification,
        enableOtherNotification: settings.value.enableOtherNotification
      })
    })
    
    const data = await response.json()
    
    if (data.code === 200) {
      // 成功更新
      originalSettings.value = { ...settings.value }
      emit('updated', settings.value)
      
      // 显示成功提示
      if (window.showToast) {
        window.showToast('通知设置已更新', 'success')
      }
    } else {
      error.value = data.msg || '更新设置失败'
    }
  } catch (err) {
    console.error('更新通知设置失败:', err)
    error.value = '网络错误，请稍后重试'
  } finally {
    updating.value = false
  }
}



// 处理点击遮罩层关闭
const handleOverlayClick = () => {
  emit('close')
}

// 监听显示状态变化
watch(() => props.show, (newShow) => {
  if (newShow) {
    fetchSettings()
  }
})

// 组件挂载时如果已经显示，则获取设置
onMounted(() => {
  if (props.show) {
    fetchSettings()
  }
})
</script>

<style scoped>
.notification-settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 1rem;
}

.notification-settings-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  overflow: hidden;
  animation: modalSlideIn 0.3s ease-out;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem;
  border-bottom: 1px solid #e5e7eb;
  background-color: #fafafa;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: #1a1a1a;
}

.close-btn {
  padding: 0.5rem;
  border: none;
  background: none;
  color: #6b7280;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background-color: #f3f4f6;
  color: #374151;
}

.close-btn i {
  font-size: 1.25rem;
}

.modal-content {
  padding: 1.25rem;
  max-height: 60vh;
  overflow-y: auto;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem;
  color: #6b7280;
}

.rotating {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.settings-list {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.settings-group {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.625rem;
  padding-bottom: 0.375rem;
  border-bottom: 1px solid #f3f4f6;
}

.group-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.875rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background-color: #ffffff;
  transition: all 0.2s ease;
}

.setting-item:hover {
  border-color: #d1d5db;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.setting-info h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #1a1a1a;
}

.setting-info p {
  margin: 0;
  font-size: 0.875rem;
  color: #6b7280;
  line-height: 1.4;
}

/* 开关样式 */
.switch {
  position: relative;
  display: inline-block;
  width: 52px;
  height: 28px;
  flex-shrink: 0;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #cbd5e1;
  transition: 0.3s ease;
  border-radius: 28px;
}

.slider:before {
  position: absolute;
  content: "";
  height: 20px;
  width: 20px;
  left: 4px;
  bottom: 4px;
  background-color: white;
  transition: 0.3s ease;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

input:checked + .slider {
  background-color: #3b82f6;
}

input:checked + .slider:before {
  transform: translateX(24px);
}

input:disabled + .slider {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 0.75rem;
  background-color: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 4px;
  color: #dc2626;
  font-size: 0.75rem;
  margin: 0.5rem 0 0 0;
}

.error-message i {
  font-size: 1rem;
}



.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.5rem 0.875rem;
  border-radius: 6px;
  font-weight: 500;
  font-size: 0.8125rem;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  min-width: 80px;
  justify-content: center;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel {
  background-color: #f8fafc;
  color: #64748b;
  border-color: #e2e8f0;
}

.btn-cancel:hover:not(:disabled) {
  background-color: #f1f5f9;
  border-color: #cbd5e1;
}

.btn-save {
  background-color: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

.btn-save:hover:not(:disabled) {
  background-color: #2563eb;
  border-color: #2563eb;
}

.btn-save-small {
  background-color: #3b82f6;
  color: white;
  border-color: #3b82f6;
  padding: 0.375rem 0.75rem;
  font-size: 0.75rem;
  min-width: auto;
}

.btn-save-small:hover:not(:disabled) {
  background-color: #2563eb;
  border-color: #2563eb;
}

.btn i {
  font-size: 1rem;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .notification-settings-modal {
    margin: 1rem;
    max-width: none;
  }
  
  .setting-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }
  
  .switch {
    align-self: flex-end;
  }
  
  .group-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .btn-save-small {
    align-self: stretch;
    text-align: center;
  }
}
</style> 