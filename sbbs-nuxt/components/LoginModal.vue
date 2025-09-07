<template>
  <div v-if="show" class="login-modal-overlay" @click="handleOverlayClick" @mousedown="handleMouseDown" @mousemove="handleMouseMove" @mouseup="handleMouseUp">
    <div class="login-modal" @click.stop>
      <!-- 关闭按钮 -->
      <button class="close-btn" @click="closeModal">
        <i class="ri-close-line"></i>
      </button>
      
      <!-- 头部 -->
      <div class="modal-header">
        <h2 class="modal-title">
          <i class="ri-lock-line"></i>
          请先登录
        </h2>
        <p class="modal-subtitle">登录后即可进行操作</p>
      </div>
      
      <!-- 登录表单 -->
      <form @submit.prevent="handleLogin" class="login-form">
        <!-- 邮箱 -->
        <div class="form-group">
          <label class="form-label">
            <i class="ri-mail-line"></i>
            邮箱
          </label>
          <input 
            v-model="email"
            type="email" 
            class="form-input"
            :class="{ error: error && error.includes('邮箱') }"
            placeholder="请输入邮箱"
            required
          />
        </div>
        
        <!-- 密码 -->
        <div class="form-group">
          <label class="form-label">
            <i class="ri-lock-line"></i>
            密码
          </label>
          <div class="password-wrapper">
            <input 
              v-model="password"
              :type="showPassword ? 'text' : 'password'" 
              class="form-input"
              :class="{ error: error && error.includes('密码') }"
              placeholder="请输入密码"
              required
            />
            <button
              type="button"
              @click="showPassword = !showPassword"
              class="password-toggle"
            >
              <i :class="showPassword ? 'ri-eye-off-line' : 'ri-eye-line'"></i>
            </button>
          </div>
        </div>
        
        <!-- 验证码 -->
        <div class="form-group">
          <label class="form-label">
            <i class="ri-shield-check-line"></i>
            验证码
          </label>
          <div class="captcha-wrapper">
            <input 
              v-model="verificationCode"
              type="text" 
              class="form-input captcha-input"
              :class="{ error: error && error.includes('验证码') }"
              placeholder="请输入验证码"
              required
            />
            <div class="captcha-image-container">
              <!-- 加载状态 -->
              <div v-if="captchaLoading" class="captcha-loading">
                <span class="captcha-spinner"></span>
              </div>
              <!-- 验证码图片 -->
              <img 
                v-else-if="captchaUrl"
                :src="captchaUrl" 
                alt="验证码" 
                class="captcha-image" 
                @click="refreshCaptcha"
                title="点击刷新验证码"
              />
              <!-- 刷新按钮 -->
              <button
                v-else
                type="button"
                @click="refreshCaptcha"
                class="captcha-reload"
                title="获取验证码"
              >
                <i class="ri-refresh-line"></i>
              </button>
            </div>
          </div>
        </div>
        
        <!-- 错误信息 -->
        <div v-if="error" class="error-message">
          <i class="ri-error-warning-line"></i>
          {{ error }}
        </div>
        
        <!-- 登录按钮 -->
        <button 
          type="submit" 
          :disabled="isLoading || !email || !password || !verificationCode"
          class="login-btn"
          :class="{ loading: isLoading }"
        >
          <span v-if="isLoading" class="btn-spinner"></span>
          <i v-else class="ri-login-circle-line"></i>
          {{ isLoading ? '登录中...' : '立即登录' }}
        </button>
      </form>
      
      <!-- 底部链接 -->
      <div class="modal-footer">
        <div class="footer-links">
          <p>还没有账户？ 
            <a href="/register" class="register-link" @click="closeModal">
              立即注册
            </a>
          </p>
          <button 
            class="later-btn" 
            @click="remindLater"
            title="1小时内不再提醒登录">
            <i class="ri-time-line"></i>
            稍后提醒
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import { useUserStore } from '~/stores/user'

const props = defineProps({
  show: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'success'])

const userStore = useUserStore()
const API_BASE_URL = useApiBaseUrl()

// 表单数据
const email = ref('')
const password = ref('')
const verificationCode = ref('')
const showPassword = ref(false)
const error = ref('')
const isLoading = ref(false)

// 验证码相关
const captchaUrl = ref('')
const captchaId = ref('')
const captchaLoading = ref(false)

// 监听弹窗显示状态
watch(() => props.show, (newVal) => {
  if (newVal) {
    refreshCaptcha()
    // 清空表单
    email.value = ''
    password.value = ''
    verificationCode.value = ''
    error.value = ''
  }
})

// 刷新验证码
const refreshCaptcha = async (clearError = true) => {
  if (!process.client) return
  
  captchaLoading.value = true
  if (clearError) {
    error.value = '' // 只在需要时清除错误信息
  }
  
  try {
    const response = await fetch(`${API_BASE_URL}/v1/captcha`, {
      method: 'GET',
      responseType: 'arraybuffer'
    })
    
    // 从响应头获取Captcha-Id
    const captchaIdHeader = response.headers.get('Captcha-Id') || 
                           response.headers.get('captcha-id') || 
                           response.headers.get('CaptchaID')
    
    if (captchaIdHeader) {
      captchaId.value = captchaIdHeader
      console.log('验证码UUID已获取:', captchaId.value) // 调试日志
    } else {
      console.error('未能获取验证码UUID')
    }
    
    // 获取验证码图片
    const blob = await response.blob()
    captchaUrl.value = URL.createObjectURL(blob)
  } catch (err) {
    console.error('获取验证码失败', err)
    error.value = '获取验证码失败，请刷新重试'
  } finally {
    captchaLoading.value = false
  }
}

// 处理登录
const handleLogin = async () => {
  if (isLoading.value) return
  
  error.value = ''
  isLoading.value = true
  
  try {
    const loginData = {
      email: email.value,
      password: password.value,
      verificationCode: verificationCode.value,
      uuid: captchaId.value
    }
    
    console.log('登录数据:', { ...loginData, password: '***' }) // 调试日志（隐藏密码）
    
    // 发送登录请求
    const response = await fetch(`${API_BASE_URL}/v1/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(loginData)
    })
    
    const data = await response.json()
    
    if (data.code === 200 && data.data) {
      // 登录成功，更新用户状态
      userStore.setUser(data.data, data.data.token)
      
      // 触发成功事件
      emit('success')
      closeModal()
      
      // 登录成功后稍微延迟，确保状态同步完成
      await nextTick()
      console.log('🎉 登录成功，用户状态已更新')
      
      // 如果在帖子详情页，刷新页面确保评论区正常显示
      if (process.client && window.location.pathname.includes('/post/')) {
        console.log('🔄 在帖子详情页登录成功，刷新页面')
        
        // 保存当前滚动位置
        const scrollPosition = window.pageYOffset || document.documentElement.scrollTop
        sessionStorage.setItem('loginScrollPosition', scrollPosition.toString())
        
        setTimeout(() => {
          window.location.reload()
        }, 300) // 缩短延迟，提升体验
      }
    } else {
      // 登录失败，显示错误信息
      error.value = data.msg || '登录失败，请重试'
      refreshCaptcha(false) // 刷新验证码但不清除错误信息
    }
  } catch (err) {
    console.error('登录出错', err)
    error.value = '登录失败，请稍后重试'
    refreshCaptcha(false) // 刷新验证码但不清除错误信息
  } finally {
    isLoading.value = false
  }
}

// 关闭弹窗
const closeModal = () => {
  emit('close')
}

// 防止文本选择时意外关闭弹窗
let isTextSelecting = false
let mouseDownTarget = null

const handleMouseDown = (event) => {
  mouseDownTarget = event.target
  // 检查是否在可选择文本的元素上按下鼠标
  const isSelectableElement = event.target.tagName === 'INPUT' || 
                             event.target.tagName === 'TEXTAREA' || 
                             event.target.closest('.login-modal')
  isTextSelecting = isSelectableElement
}

const handleMouseMove = () => {
  // 如果鼠标移动且之前在模态框内按下，说明可能在选择文本
  if (mouseDownTarget && mouseDownTarget.closest('.login-modal')) {
    isTextSelecting = true
  }
}

const handleMouseUp = () => {
  // 延迟重置状态，避免立即触发点击事件
  setTimeout(() => {
    isTextSelecting = false
    mouseDownTarget = null
  }, 10)
}

// 点击遮罩关闭（防止文本选择时关闭）
const handleOverlayClick = (event) => {
  // 只有点击在覆盖层本身，且不在文本选择过程中才关闭
  if (event.target === event.currentTarget && !isTextSelecting) {
    closeModal()
  }
}

// 稍后提醒功能
const remindLater = () => {
  if (typeof window === 'undefined') return
  
  // 设置1小时后再提醒
  const oneHourLater = Date.now() + (60 * 60 * 1000)
  try {
    localStorage.setItem('loginRemindLater', oneHourLater.toString())
    console.log('⏰ 已设置1小时内不再提醒登录')
  } catch (error) {
    console.error('无法保存提醒时间:', error)
  }
  
  closeModal()
}

// 键盘事件处理
const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    closeModal()
  }
}

// 监听键盘事件
watch(() => props.show, (newVal) => {
  if (newVal) {
    document.addEventListener('keydown', handleKeydown)
  } else {
    document.removeEventListener('keydown', handleKeydown)
  }
})
</script>

<style scoped>
.login-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(10px);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.login-modal {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  box-shadow: 
    0 20px 60px rgba(0, 0, 0, 0.2),
    0 8px 32px rgba(0, 0, 0, 0.1),
    0 0 0 1px rgba(255, 255, 255, 0.2);
  width: 100%;
  max-width: 420px;
  max-height: 90vh;
  overflow-y: auto;
  position: relative;
  animation: modalSlideIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  z-index: 10;
}

.close-btn:hover {
  background: rgba(0, 0, 0, 0.2);
  transform: scale(1.1);
}

.close-btn i {
  font-size: 16px;
  color: #666;
}

.modal-header {
  padding: 32px 32px 16px;
  text-align: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

.modal-title {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.modal-title i {
  font-size: 20px;
  color: #3b82f6;
}

.modal-subtitle {
  color: #666;
  font-size: 14px;
  margin: 0;
}

.login-form {
  padding: 24px 32px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.form-label i {
  font-size: 14px;
  color: #6b7280;
}

.form-input {
  width: 100%;
  height: 44px;
  padding: 0 12px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.8);
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #3b82f6;
  background: white;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-input.error {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.05);
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
}

.form-input.error {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
}

.password-wrapper {
  position: relative;
}

.password-toggle {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: none;
  cursor: pointer;
  color: #6b7280;
  transition: color 0.2s ease;
}

.password-toggle:hover {
  color: #374151;
}

.captcha-wrapper {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-image-container {
  flex-shrink: 0;
  width: 100px;
  height: 44px;
  border: 1.5px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
}

.captcha-reload {
  width: 100%;
  height: 100%;
  border: none;
  background: none;
  cursor: pointer;
  color: #6b7280;
  font-size: 16px;
  transition: color 0.2s ease;
}

.captcha-reload:hover {
  color: #374151;
}

.captcha-loading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
}

.captcha-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e5e7eb;
  border-top: 2px solid #3b82f6;
  border-radius: 50%;
  animation: captcha-spin 1s linear infinite;
}

@keyframes captcha-spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-message {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #ef4444;
  font-size: 14px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 6px;
}

.error-message i {
  font-size: 14px;
}

.login-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: white;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 16px;
}

.login-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #2563eb, #1e40af);
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.login-btn.loading {
  pointer-events: none;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.modal-footer {
  padding: 0 32px 32px;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  padding-top: 20px;
}

.footer-links {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.modal-footer p {
  color: #666;
  font-size: 14px;
  margin: 0;
}

.later-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: transparent;
  color: #666;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.later-btn:hover {
  background: #f9fafb;
  border-color: #d1d5db;
  color: #374151;
}

.later-btn i {
  font-size: 12px;
}

.register-link {
  color: #3b82f6;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease;
}

.register-link:hover {
  color: #2563eb;
  text-decoration: underline;
}

/* 移动端适配 */
@media (max-width: 480px) {
  .login-modal {
    margin: 10px;
    max-width: none;
  }
  
  .modal-header {
    padding: 24px 24px 16px;
  }
  
  .login-form {
    padding: 24px;
  }
  
  .modal-footer {
    padding: 0 24px 24px;
  }
  
  .footer-links {
    flex-direction: column;
    gap: 12px;
    text-align: center;
  }
  
  .later-btn {
    align-self: center;
  }
  
  .captcha-wrapper {
    flex-direction: column;
    align-items: stretch;
  }
  
  .captcha-image-container {
    width: 100%;
    height: 50px;
  }
}
</style> 