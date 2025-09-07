<template>
  <div class="register-container">
    <!-- 移动光晕 -->
    <div class="orb1"></div>
    <div class="orb2"></div>
    <div class="orb3"></div>
    
    <!-- 闪烁星星 -->
    <div class="stars">
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
      <div class="star"></div>
    </div>
    
    <!-- 浮动气泡 -->
    <div class="bubble"></div>
    <div class="bubble"></div>
    <div class="bubble"></div>
    <div class="bubble"></div>
    
    <div class="register-card">
      <!-- 头部标题 -->
      <div class="register-header">
        <h1 class="register-title">
          <i class="ri-user-add-line"></i>
          创建账号
        </h1>
        <p class="register-subtitle">加入SBBS社区，开始你的交流之旅</p>
      </div>

      <!-- 注册表单 -->
      <form @submit.prevent="handleRegister" class="register-form">
        <!-- 头像上传 -->
        <div class="form-group avatar-group">
          <label class="form-label">
            <i class="ri-account-circle-line"></i>
            上传头像
          </label>
          <div class="avatar-upload">
            <div 
              class="avatar-preview" 
              @click="triggerFileUpload"
              @drop="handleDrop"
              @dragover="handleDragOver"
              @dragenter="handleDragEnter"
              @dragleave="handleDragLeave"
              :class="{ 'drag-over': isDragOver }"
            >
              <img v-if="form.avatar" :src="form.avatar" alt="头像预览" class="avatar-img">
              <div v-else class="avatar-placeholder">
                <i class="ri-user-line"></i>
              </div>
              <div class="avatar-overlay">
                <i class="ri-camera-line"></i>
                <span>{{ form.avatar ? '更换头像' : '拖拽或点击上传' }}</span>
              </div>
            </div>
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              @change="handleFileUpload"
              style="display: none;"
            >
            <div class="upload-hint">
              <p>支持 JPG、PNG、WEBP 格式</p>
              <p>文件大小不超过 5MB</p>
              <p class="avatar-warning"><i class="ri-alert-line"></i> 注意：注册成功后头像将无法修改，请谨慎选择</p>
            </div>
          </div>
          <div v-if="isUploading" class="upload-progress">
            <div class="progress-bar">
              <div class="progress-fill"></div>
            </div>
            <span>上传中...</span>
          </div>
          <div v-if="errors.avatar" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ errors.avatar }}
          </div>
        </div>

        <!-- 用户名 -->
        <div class="form-group">
          <label for="username" class="form-label">
            <i class="ri-user-line"></i>
            用户名
          </label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            class="form-input"
            :class="{ error: errors.username }"
            placeholder="请输入用户名"
            @blur="validateUsername"
            @input="clearError('username')"
          >
          <div v-if="errors.username" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ errors.username }}
          </div>
        </div>

        <!-- 邮箱 -->
        <div class="form-group">
          <label for="email" class="form-label">
            <i class="ri-mail-line"></i>
            邮箱地址
          </label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            class="form-input"
            :class="{ error: errors.email }"
            placeholder="请输入邮箱地址"
            @blur="validateEmail"
            @input="clearError('email')"
          >
          <div v-if="errors.email" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ errors.email }}
          </div>
        </div>

        <!-- 验证码 -->
        <div class="form-group">
          <label for="verificationCode" class="form-label">
            <i class="ri-shield-check-line"></i>
            邮箱验证码
          </label>
          <div class="verification-group">
            <input
              id="verificationCode"
              v-model="form.verificationCode"
              type="text"
              class="form-input verification-input"
              :class="{ error: errors.verificationCode }"
              placeholder="请输入验证码"
              maxlength="6"
              @input="clearError('verificationCode')"
            >
            <button
              type="button"
              @click="sendVerificationCode"
              :disabled="!canSendCode || isCodeSending"
              class="verification-btn"
              :class="{ 
                sending: isCodeSending,
                countdown: countdown > 0 
              }"
            >
              <span v-if="isCodeSending" class="btn-spinner"></span>
              <span v-else-if="countdown > 0">{{ countdown }}s</span>
              <span v-else>获取验证码</span>
            </button>
          </div>
          <div v-if="errors.verificationCode" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ errors.verificationCode }}
          </div>
        </div>

        <!-- 密码 -->
        <div class="form-group">
          <label for="password" class="form-label">
            <i class="ri-lock-line"></i>
            密码
          </label>
          <div class="password-input-wrapper">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              class="form-input"
              :class="{ error: errors.password }"
              placeholder="请输入密码"
              @blur="validatePassword"
              @input="onPasswordInput"
            >
            <button
              type="button"
              @click="togglePasswordVisibility"
              class="password-toggle-outer"
            >
              <i :class="showPassword ? 'ri-eye-off-line' : 'ri-eye-line'"></i>
            </button>
          </div>
          
          <!-- 密码强度指示器 -->
          <div v-if="form.password" class="password-strength">
            <div class="strength-bar">
              <div 
                class="strength-fill" 
                :class="passwordStrength.class"
                :style="{ width: passwordStrength.percentage + '%' }"
              ></div>
            </div>
            <span class="strength-text" :class="passwordStrength.class">
              {{ passwordStrength.text }}
            </span>
          </div>
          
          <div v-if="errors.password" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ errors.password }}
          </div>
        </div>

        <!-- 确认密码 -->
        <div class="form-group">
          <label for="confirmPassword" class="form-label">
            <i class="ri-lock-2-line"></i>
            确认密码
          </label>
          <div class="password-input-wrapper">
            <input
              id="confirmPassword"
              v-model="form.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              class="form-input"
              :class="{ error: errors.confirmPassword }"
              placeholder="请再次输入密码"
              @blur="validateConfirmPassword"
              @input="clearError('confirmPassword')"
            >
            <button
              type="button"
              @click="showConfirmPassword = !showConfirmPassword"
              class="password-toggle-outer"
            >
              <i :class="showConfirmPassword ? 'ri-eye-off-line' : 'ri-eye-line'"></i>
            </button>
          </div>
          <div v-if="errors.confirmPassword" class="error-message">
            <i class="ri-error-warning-line"></i>
            {{ errors.confirmPassword }}
          </div>
        </div>

        <!-- 注册按钮 -->
        <button
          type="submit"
          :disabled="!isFormValid || isRegistering"
          class="register-btn"
          :class="{ 
            loading: isRegistering,
            valid: isFormValid 
          }"
        >
          <span v-if="isRegistering" class="btn-spinner"></span>
          <i v-else class="ri-user-add-line"></i>
          {{ isRegistering ? '注册中...' : '创建账号' }}
        </button>
      </form>

      <!-- 底部链接 -->
      <div class="register-footer">
        <p>已有账号？ 
          <router-link to="/auth/login" class="login-link">
            立即登录
          </router-link>
        </p>
      </div>
    </div>

    <!-- 成功提示 -->
    <div v-if="showSuccess" class="success-modal" @click="hideSuccess">
      <div class="success-content" @click.stop>
        <div class="success-icon">
          <i class="ri-check-line"></i>
        </div>
        <h3>注册成功！</h3>
        <p>欢迎加入SBBS社区，正在跳转到登录页面...</p>
        <div class="success-progress">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressWidth + '%' }"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
  
  <!-- Toast通知组件 -->
  <Toast ref="toastRef" />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Toast from '~/components/Toast.vue'

const router = useRouter()
const API_BASE_URL = useApiBaseUrl()

// 表单数据
const form = ref({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  verificationCode: '',
  avatar: ''
})

// 错误信息
const errors = ref({})

// 状态管理
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const isRegistering = ref(false)
const isCodeSending = ref(false)
const countdown = ref(0)
const showSuccess = ref(false)
const progressWidth = ref(0)
const isUploading = ref(false)
const isDragOver = ref(false)

// 文件上传引用
const fileInput = ref(null)

// 计算属性
const canSendCode = computed(() => {
  return form.value.email && validateEmailFormat(form.value.email) && countdown.value === 0
})

const passwordStrength = computed(() => {
  const password = form.value.password
  if (!password) return { percentage: 0, text: '', class: '' }
  
  let score = 0
  let text = '弱'
  let className = 'weak'
  
  // 长度
  if (password.length >= 8) score += 25
  if (password.length >= 12) score += 15
  
  // 包含数字
  if (/\d/.test(password)) score += 20
  
  // 包含小写字母
  if (/[a-z]/.test(password)) score += 20
  
  // 包含大写字母
  if (/[A-Z]/.test(password)) score += 20
  
  // 包含特殊字符
  if (/[^A-Za-z0-9]/.test(password)) score += 20
  
  if (score >= 80) {
    text = '强'
    className = 'strong'
  } else if (score >= 60) {
    text = '中'
    className = 'medium'
  }
  
  return {
    percentage: Math.min(score, 100),
    text,
    class: className
  }
})

const isFormValid = computed(() => {
  return form.value.username && 
         form.value.email && 
         form.value.password && 
         form.value.confirmPassword &&
         form.value.verificationCode &&
         form.value.avatar &&
         Object.keys(errors.value).length === 0
})

// 方法
const triggerFileUpload = () => {
  fileInput.value?.click()
}

// 拖拽处理
const handleDragOver = (event) => {
  event.preventDefault()
  event.stopPropagation()
}

const handleDragEnter = (event) => {
  event.preventDefault()
  event.stopPropagation()
  isDragOver.value = true
}

const handleDragLeave = (event) => {
  event.preventDefault()
  event.stopPropagation()
  isDragOver.value = false
}

const handleDrop = (event) => {
  event.preventDefault()
  event.stopPropagation()
  isDragOver.value = false
  
  const files = event.dataTransfer.files
  if (files.length > 0) {
    const file = files[0]
    uploadFile(file)
  }
}

const uploadFile = async (file) => {
  // 验证文件类型
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    errors.value.avatar = '请选择 JPG、PNG 或 WEBP 格式的图片'
    return
  }
  
  // 验证文件大小 (5MB)
  if (file.size > 5 * 1024 * 1024) {
    errors.value.avatar = '图片大小不能超过 5MB'
    return
  }
  
  // 清除错误信息
  delete errors.value.avatar
  isUploading.value = true
  
  try {
    const formData = new FormData()
    formData.append('image', file)
    
    const response = await fetch(`${API_BASE_URL}/v1/image/upload`, {
      method: 'POST',
      body: formData
    })
    
    const data = await response.json()
    
    if (data.code === 200 && data.data?.url) {
      form.value.avatar = data.data.url
      showNotification('头像上传成功', 'success')
    } else {
      throw new Error(data.msg || '上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    errors.value.avatar = error.message || '头像上传失败，请稍后重试'
  } finally {
    isUploading.value = false
  }
}

const handleFileUpload = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  await uploadFile(file)
  
  // 清空文件输入，允许重新选择同一文件
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const togglePasswordVisibility = () => {
  showPassword.value = !showPassword.value
}

const clearError = (field) => {
  if (errors.value[field]) {
    delete errors.value[field]
  }
}

const validateEmailFormat = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

const validateUsername = () => {
  const username = form.value.username.trim()
  if (!username) {
    errors.value.username = '请输入用户名'
  } else if (username.length < 3) {
    errors.value.username = '用户名至少3个字符'
  } else if (username.length > 20) {
    errors.value.username = '用户名不能超过20个字符'
  } else if (!/^[a-zA-Z0-9_\u4e00-\u9fa5]+$/.test(username)) {
    errors.value.username = '用户名只能包含字母、数字、下划线和中文'
  } else {
    delete errors.value.username
  }
}

const validateEmail = () => {
  const email = form.value.email.trim()
  if (!email) {
    errors.value.email = '请输入邮箱地址'
  } else if (!validateEmailFormat(email)) {
    errors.value.email = '请输入有效的邮箱地址'
  } else {
    delete errors.value.email
  }
}

const validatePassword = () => {
  const password = form.value.password
  if (!password) {
    errors.value.password = '请输入密码'
  } else if (password.length < 8) {
    errors.value.password = '密码至少8个字符'
  } else if (password.length > 50) {
    errors.value.password = '密码不能超过50个字符'
  } else {
    delete errors.value.password
    // 重新验证确认密码
    if (form.value.confirmPassword) {
      validateConfirmPassword()
    }
  }
}

const validateConfirmPassword = () => {
  const confirmPassword = form.value.confirmPassword
  if (!confirmPassword) {
    errors.value.confirmPassword = '请确认密码'
  } else if (confirmPassword !== form.value.password) {
    errors.value.confirmPassword = '两次密码输入不一致'
  } else {
    delete errors.value.confirmPassword
  }
}

const onPasswordInput = () => {
  clearError('password')
  // 实时验证确认密码
  if (form.value.confirmPassword) {
    validateConfirmPassword()
  }
}

// 发送验证码
const sendVerificationCode = async () => {
  if (!canSendCode.value) return
  
  isCodeSending.value = true
  
  try {
    const response = await fetch(`${API_BASE_URL}/v1/rcode/${form.value.email}`)
    const data = await response.json()
    
    if (data.code === 200) {
      // 开始倒计时
      countdown.value = 60
      startCountdown()
      
      // 显示成功提示
      showNotification('验证码已发送到您的邮箱', 'success')
    } else {
      throw new Error(data.msg || '发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    showNotification(error.message || '发送验证码失败，请稍后重试', 'error')
  } finally {
    isCodeSending.value = false
  }
}

// 倒计时
const startCountdown = () => {
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

// 注册
const handleRegister = async () => {
  // 验证所有字段
  validateUsername()
  validateEmail()
  validatePassword()
  validateConfirmPassword()
  
  if (!form.value.verificationCode) {
    errors.value.verificationCode = '请输入验证码'
  }
  
  if (Object.keys(errors.value).length > 0) {
    return
  }
  
  isRegistering.value = true
  
  try {
    const response = await fetch(`${API_BASE_URL}/v1/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: form.value.username.trim(),
        password: form.value.password,
        email: form.value.email.trim(),
        verificationCode: form.value.verificationCode.trim(),
        avatar: form.value.avatar
      })
    })
    
    const data = await response.json()
    
    if (data.code === 200) {
      // 显示成功提示并开始进度条动画
      showSuccess.value = true
      isRegistering.value = false // 停止加载状态
      
      // 进度条动画
      const progressInterval = setInterval(() => {
        progressWidth.value += 5
        if (progressWidth.value >= 100) {
          clearInterval(progressInterval)
          // 延迟跳转到登录页面
          setTimeout(() => {
            router.push('/auth/login')
          }, 300)
        }
      }, 40) // 总时长约2.5秒
    } else {
      throw new Error(data.msg || '注册失败')
    }
  } catch (error) {
    console.error('注册失败:', error)
    showNotification(error.message || '注册失败，请稍后重试', 'error')
  } finally {
    isRegistering.value = false
  }
}

// Toast引用
const toastRef = ref(null)

// 通知系统
const showNotification = (message, type = 'info') => {
  if (process.client && window.$toast) {
    window.$toast[type](message)
  } else if (toastRef.value) {
    toastRef.value.addToast(message, type)
  } else {
    // 降级为console
    console.log(`[${type.toUpperCase()}] ${message}`)
  }
}

const hideSuccess = () => {
  showSuccess.value = false
  progressWidth.value = 0
}

const goToLogin = () => {
  router.push('/login')
}

// 页面标题
useHead({
  title: '注册 - SBBS社区',
  meta: [
    { name: 'description', content: '加入SBBS社区，开始你的交流之旅' }
  ]
})

// 设置独立布局，不使用default
definePageMeta({
  layout: false
})
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  background: 
    radial-gradient(ellipse at top, #1e3a8a 0%, #1e1b4b 50%, #0f172a 100%),
    linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #1e3a8a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
  position: relative;
  overflow: hidden;
  animation: bg-color-shift 20s ease-in-out infinite;
}

@keyframes bg-color-shift {
  0%, 100% { 
    filter: hue-rotate(0deg) saturate(1); 
  }
  25% { 
    filter: hue-rotate(10deg) saturate(1.1); 
  }
  50% { 
    filter: hue-rotate(-10deg) saturate(0.9); 
  }
  75% { 
    filter: hue-rotate(5deg) saturate(1.05); 
  }
}

.register-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: 
    radial-gradient(circle at 20% 80%, rgba(120, 119, 198, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(120, 119, 198, 0.2) 0%, transparent 50%);
  pointer-events: none;
  animation: aurora-flow 15s ease-in-out infinite;
}

@keyframes aurora-flow {
  0%, 100% { 
    opacity: 1;
    transform: translateX(0) scale(1);
  }
  33% { 
    opacity: 0.8;
    transform: translateX(10px) scale(1.05);
  }
  66% { 
    opacity: 0.6;
    transform: translateX(-5px) scale(0.95);
  }
}

/* 浮动光点层 */
.register-container::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    radial-gradient(2px 2px at 20px 30px, rgba(96, 165, 250, 0.8), transparent),
    radial-gradient(2px 2px at 40px 70px, rgba(168, 85, 247, 0.8), transparent),
    radial-gradient(1px 1px at 90px 40px, rgba(34, 197, 94, 0.8), transparent),
    radial-gradient(1px 1px at 130px 80px, rgba(251, 191, 36, 0.8), transparent),
    radial-gradient(2px 2px at 160px 30px, rgba(239, 68, 68, 0.8), transparent),
    radial-gradient(1px 1px at 200px 60px, rgba(147, 197, 253, 0.8), transparent),
    radial-gradient(1px 1px at 240px 90px, rgba(196, 181, 253, 0.8), transparent),
    radial-gradient(2px 2px at 280px 20px, rgba(110, 231, 183, 0.8), transparent),
    radial-gradient(1px 1px at 320px 70px, rgba(252, 211, 77, 0.8), transparent),
    radial-gradient(1px 1px at 360px 40px, rgba(248, 113, 113, 0.8), transparent);
  background-repeat: repeat;
  background-size: 400px 400px;
  animation: particles-float 25s linear infinite;
  pointer-events: none;
}

@keyframes particles-float {
  0% { 
    transform: translateY(0) translateX(0) rotate(0deg);
    opacity: 1;
  }
  25% { 
    transform: translateY(-10px) translateX(5px) rotate(90deg);
    opacity: 0.8;
  }
  50% { 
    transform: translateY(-20px) translateX(-5px) rotate(180deg);
    opacity: 0.6;
  }
  75% { 
    transform: translateY(-15px) translateX(8px) rotate(270deg);
    opacity: 0.9;
  }
  100% { 
    transform: translateY(0) translateX(0) rotate(360deg);
    opacity: 1;
  }
}

/* 移动光晕 */
.register-container .orb1,
.register-container .orb2,
.register-container .orb3 {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(40px);
  opacity: 0.3;
}

.register-container .orb1 {
  width: 300px;
  height: 300px;
  background: linear-gradient(45deg, #60a5fa, #3b82f6);
  top: -150px;
  left: -150px;
  animation: orb-move-1 20s ease-in-out infinite;
}

.register-container .orb2 {
  width: 250px;
  height: 250px;
  background: linear-gradient(135deg, #a855f7, #7c3aed);
  top: 50%;
  right: -125px;
  animation: orb-move-2 25s ease-in-out infinite reverse;
}

.register-container .orb3 {
  width: 200px;
  height: 200px;
  background: linear-gradient(225deg, #10b981, #059669);
  bottom: -100px;
  left: 30%;
  animation: orb-move-3 30s ease-in-out infinite;
}

@keyframes orb-move-1 {
  0%, 100% { 
    transform: translate(0, 0) scale(1);
  }
  25% { 
    transform: translate(100px, 50px) scale(1.2);
  }
  50% { 
    transform: translate(200px, -30px) scale(0.8);
  }
  75% { 
    transform: translate(50px, 80px) scale(1.1);
  }
}

@keyframes orb-move-2 {
  0%, 100% { 
    transform: translate(0, 0) scale(1);
  }
  33% { 
    transform: translate(-80px, -60px) scale(1.3);
  }
  66% { 
    transform: translate(-150px, 40px) scale(0.9);
  }
}

@keyframes orb-move-3 {
  0%, 100% { 
    transform: translate(0, 0) scale(1);
  }
  20% { 
    transform: translate(-50px, -80px) scale(1.1);
  }
  40% { 
    transform: translate(80px, -100px) scale(0.7);
  }
  60% { 
    transform: translate(120px, -20px) scale(1.2);
  }
  80% { 
    transform: translate(-20px, 60px) scale(0.9);
  }
}

/* 闪烁星星 */
.register-container .stars {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.register-container .star {
  position: absolute;
  width: 2px;
  height: 2px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  animation: twinkle 3s ease-in-out infinite;
}

.register-container .star:nth-child(1) { top: 20%; left: 10%; animation-delay: 0s; }
.register-container .star:nth-child(2) { top: 30%; left: 20%; animation-delay: 0.5s; }
.register-container .star:nth-child(3) { top: 40%; left: 80%; animation-delay: 1s; }
.register-container .star:nth-child(4) { top: 60%; left: 30%; animation-delay: 1.5s; }
.register-container .star:nth-child(5) { top: 70%; left: 70%; animation-delay: 2s; }
.register-container .star:nth-child(6) { top: 15%; left: 60%; animation-delay: 2.5s; }
.register-container .star:nth-child(7) { top: 85%; left: 15%; animation-delay: 0.8s; }
.register-container .star:nth-child(8) { top: 25%; left: 90%; animation-delay: 1.8s; }
.register-container .star:nth-child(9) { top: 55%; left: 50%; animation-delay: 1.2s; }
.register-container .star:nth-child(10) { top: 75%; left: 85%; animation-delay: 2.2s; }

@keyframes twinkle {
  0%, 100% { 
    opacity: 0.3;
    transform: scale(1);
  }
  50% { 
    opacity: 1;
    transform: scale(1.5);
  }
}

/* 浮动气泡 */
.register-container .bubble {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  pointer-events: none;
  animation: bubble-float 15s ease-in-out infinite;
}

.register-container .bubble:nth-child(11) {
  width: 40px;
  height: 40px;
  left: 10%;
  animation-duration: 12s;
  animation-delay: 0s;
}

.register-container .bubble:nth-child(12) {
  width: 60px;
  height: 60px;
  left: 30%;
  animation-duration: 18s;
  animation-delay: 2s;
}

.register-container .bubble:nth-child(13) {
  width: 30px;
  height: 30px;
  left: 60%;
  animation-duration: 14s;
  animation-delay: 4s;
}

.register-container .bubble:nth-child(14) {
  width: 50px;
  height: 50px;
  left: 80%;
  animation-duration: 16s;
  animation-delay: 1s;
}

@keyframes bubble-float {
  0% {
    bottom: -60px;
    opacity: 0;
    transform: translateX(0) scale(0.5);
  }
  10% {
    opacity: 0.6;
    transform: translateX(10px) scale(0.8);
  }
  50% {
    opacity: 0.8;
    transform: translateX(-20px) scale(1);
  }
  90% {
    opacity: 0.4;
    transform: translateX(15px) scale(0.9);
  }
  100% {
    bottom: 100vh;
    opacity: 0;
    transform: translateX(0) scale(0.3);
  }
}

.register-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  padding: 2.5rem;
  width: 100%;
  max-width: 480px;
  position: relative;
  overflow: hidden;
}

.register-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
}

.register-header {
  text-align: center;
  margin-bottom: 2rem;
}

.register-title {
  font-size: 2rem;
  font-weight: 700;
  color: white;
  margin: 0 0 0.5rem 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.register-title i {
  color: #60a5fa;
}

.register-subtitle {
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
  font-size: 1rem;
  font-weight: 400;
}

.register-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.form-label i {
  color: #60a5fa;
}

.form-input {
  padding: 0.875rem 1rem;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  font-size: 1rem;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.05);
  color: white;
  backdrop-filter: blur(10px);
}

.form-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.form-input:focus {
  outline: none;
  border-color: #60a5fa;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.1);
}

.form-input.error {
  border-color: #f87171;
  background: rgba(248, 113, 113, 0.1);
}

.error-message {
  color: #fca5a5;
  font-size: 0.875rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

/* 头像上传器 - 毛玻璃风格 */
.avatar-group {
  margin-bottom: 1rem;
}

.avatar-upload {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
}

.avatar-preview {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid rgba(96, 165, 250, 0.3);
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-preview:hover {
  transform: scale(1.05);
  border-color: #60a5fa;
  box-shadow: 0 8px 25px rgba(96, 165, 250, 0.3);
}

.avatar-preview.drag-over {
  border-color: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.2);
  transform: scale(1.05);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: rgba(255, 255, 255, 0.4);
}

.avatar-placeholder i {
  font-size: 2.5rem;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
  color: white;
  text-align: center;
  padding: 0.5rem;
  backdrop-filter: blur(5px);
}

.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}

.avatar-overlay i {
  font-size: 1.5rem;
  margin-bottom: 0.25rem;
}

.avatar-overlay span {
  font-size: 0.75rem;
  font-weight: 600;
}

.upload-hint {
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.875rem;
}

.upload-hint p {
  margin: 0.25rem 0;
}

.upload-hint .avatar-warning {
  color: #f87171;
  font-weight: 600;
  margin-top: 0.5rem;
  padding: 0.35rem 0;
  border-top: 1px dashed rgba(248, 113, 113, 0.4);
  border-bottom: 1px dashed rgba(248, 113, 113, 0.4);
  background-color: rgba(248, 113, 113, 0.1);
  border-radius: 4px;
  animation: pulse-warning 2s infinite;
}

.upload-hint .avatar-warning i {
  margin-right: 0.25rem;
  font-size: 1rem;
}

@keyframes pulse-warning {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

/* 验证码组 */
.verification-group {
  display: flex;
  gap: 0.5rem;
}

.verification-input {
  flex: 1;
}

.verification-btn {
  padding: 0.875rem 1.25rem;
  background: rgba(96, 165, 250, 0.2);
  color: white;
  border: 1px solid rgba(96, 165, 250, 0.3);
  border-radius: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  min-width: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  backdrop-filter: blur(10px);
}

.verification-btn:hover:not(:disabled) {
  background: rgba(96, 165, 250, 0.3);
  border-color: #60a5fa;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(96, 165, 250, 0.2);
}

.verification-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

.verification-btn.countdown {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

/* 密码输入框 */
.password-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.password-input-wrapper .form-input {
  flex: 1;
  padding-right: 1rem;
}

.password-toggle-outer {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  padding: 0.875rem;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 3rem;
  height: 3.125rem;
}

.password-toggle-outer:hover {
  color: #60a5fa;
  background: rgba(96, 165, 250, 0.1);
  border-color: rgba(96, 165, 250, 0.3);
  transform: translateY(-1px);
}

.password-toggle-outer i {
  font-size: 1.1rem;
}

/* 密码强度指示器 */
.password-strength {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  transition: all 0.3s ease;
  border-radius: 2px;
}

.strength-fill.weak {
  background: #f87171;
}

.strength-fill.medium {
  background: #fbbf24;
}

.strength-fill.strong {
  background: #34d399;
}

.strength-text {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.strength-text.weak {
  color: #fca5a5;
}

.strength-text.medium {
  color: #fcd34d;
}

.strength-text.strong {
  color: #6ee7b7;
}

/* 注册按钮 */
.register-btn {
  padding: 1rem;
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 1.1rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1rem;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(96, 165, 250, 0.3);
}

.register-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.6s ease;
}

.register-btn:hover:not(:disabled)::before {
  left: 100%;
}

.register-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(96, 165, 250, 0.4);
}

.register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.register-btn.valid:not(:disabled) {
  background: linear-gradient(135deg, #34d399, #10b981);
  box-shadow: 0 4px 15px rgba(52, 211, 153, 0.3);
}

/* 底部 */
.register-footer {
  text-align: center;
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.register-footer p {
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
}

.login-link {
  color: #60a5fa;
  text-decoration: none;
  font-weight: 600;
  transition: color 0.3s ease;
}

.login-link:hover {
  color: #93c5fd;
  text-decoration: underline;
}

/* 上传进度 */
.upload-progress {
  display: flex;
  align-items: center;
  gap: 1rem;
  width: 100%;
  max-width: 300px;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
  border-radius: 2px;
  animation: progress-loading 1.5s ease-in-out infinite;
}

@keyframes progress-loading {
  0% { transform: translateX(-100%); }
  50% { transform: translateX(0); }
  100% { transform: translateX(100%); }
}

.upload-progress span {
  color: #60a5fa;
  font-size: 0.875rem;
  font-weight: 600;
  white-space: nowrap;
}

/* 加载动画 */
.btn-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid transparent;
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 成功弹窗 */
.success-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1rem;
}

.success-content {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  padding: 2.5rem;
  text-align: center;
  max-width: 400px;
  width: 100%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

.success-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #34d399, #10b981);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 1.5rem;
  animation: bounce 0.6s ease-out;
}

.success-icon i {
  font-size: 2.5rem;
  color: white;
}

@keyframes bounce {
  0%, 20%, 53%, 80%, 100% {
    transform: translate3d(0, 0, 0);
  }
  40%, 43% {
    transform: translate3d(0, -10px, 0);
  }
  70% {
    transform: translate3d(0, -5px, 0);
  }
  90% {
    transform: translate3d(0, -2px, 0);
  }
}

.success-content h3 {
  color: white;
  margin: 0 0 1rem 0;
  font-size: 1.5rem;
}

.success-content p {
  color: rgba(255, 255, 255, 0.7);
  margin: 0 0 1.5rem 0;
}

.success-progress {
  width: 100%;
}

.progress-bar {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #34d399, #10b981);
  border-radius: 2px;
  transition: width 0.1s ease-out;
}

.success-btn {
  padding: 0.875rem 2rem;
  background: linear-gradient(135deg, #60a5fa, #3b82f6);
  color: white;
  border: none;
  border-radius: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.success-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(96, 165, 250, 0.4);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .register-container {
    padding: 1rem;
  }
  
  .register-card {
    padding: 2rem 1.5rem;
    max-width: 400px;
  }
  
  .register-title {
    font-size: 1.5rem;
  }
  
  .verification-group {
    flex-direction: column;
  }
  
  .verification-btn {
    min-width: auto;
  }
}
</style> 