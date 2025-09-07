<template>
  <div class="login-container">
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
    
    <div class="login-card">
      <!-- 头部标题 -->
      <div class="login-header">
        <h1 class="login-title">
          <i class="ri-discuss-line"></i>
          欢迎回来
        </h1>
        <p class="login-subtitle">登录您的账户，探索更多社区内容</p>
      </div>
      
      <!-- 登录表单 -->
      <form @submit.prevent="handleLogin" class="login-form">
        <!-- 邮箱 -->
        <div class="form-group">
          <label for="email" class="form-label">
            <i class="ri-mail-line"></i>
            邮箱
          </label>
          <input 
            id="email"
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
          <label for="password" class="form-label">
            <i class="ri-lock-line"></i>
            密码
          </label>
          <div class="password-input-wrapper">
            <input 
              id="password"
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
              class="password-toggle-outer"
            >
              <i :class="showPassword ? 'ri-eye-off-line' : 'ri-eye-line'"></i>
            </button>
          </div>
        </div>
        
        <!-- 验证码 -->
        <div class="form-group">
          <label for="verificationCode" class="form-label">
            <i class="ri-shield-check-line"></i>
            验证码
          </label>
          <div class="captcha-group">
            <input 
              id="verificationCode"
              v-model="verificationCode"
              type="text" 
              class="form-input captcha-input"
              :class="{ error: error && error.includes('验证码') }"
              placeholder="请输入验证码"
              required
            />
            <div class="captcha-image-wrapper">
              <img 
                :src="captchaUrl" 
                alt="验证码" 
                class="captcha-image" 
                @click="refreshCaptcha"
                v-if="captchaUrl"
              />
              <button
                v-else
                type="button"
                @click="refreshCaptcha"
                class="captcha-reload"
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
          :disabled="isLoading"
          class="login-btn"
          :class="{ 
            loading: isLoading,
            valid: email && password && verificationCode 
          }"
        >
          <span v-if="isLoading" class="btn-spinner"></span>
          <i v-else class="ri-login-circle-line"></i>
          {{ isLoading ? '登录中...' : '立即登录' }}
        </button>
        
        <!-- 忘记密码 -->
        <div class="login-options">
          <router-link to="/auth/reset-password" class="forgot-link">
            忘记密码？
          </router-link>
        </div>
      </form>
      
      <!-- 底部链接 -->
      <div class="login-footer">
        <p>还没有账户？ 
          <router-link to="/register" class="register-link">
            立即注册
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
        <h3>登录成功！</h3>
        <p>正在跳转到首页...</p>
        <div class="success-progress">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressWidth + '%' }"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from '#app'
import { useRuntimeConfig } from '#app'
import { useUserStore } from '~/stores/user'

const router = useRouter()
const API_BASE_URL = useApiBaseUrl()

// 表单数据
const email = ref('')
const password = ref('')
const verificationCode = ref('')
const showPassword = ref(false)
const error = ref('')
const isLoading = ref(false)

// 成功提示相关
const showSuccess = ref(false)
const progressWidth = ref(0)

// 验证码相关
const captchaUrl = ref('')
const captchaId = ref('')

// 页面加载时获取验证码
onMounted(() => {
  refreshCaptcha()
})

// 刷新验证码
const refreshCaptcha = async () => {
  if (!process.client) return
  
  isLoading.value = true
  
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
      console.log('获取到的captchaId:', captchaId.value)
    } else {
      console.error('未能获取验证码ID')
    }
    
    // 获取验证码图片
    const blob = await response.blob()
    captchaUrl.value = URL.createObjectURL(blob)
  } catch (err) {
    console.error('获取验证码失败', err)
    error.value = '获取验证码失败，请刷新页面重试'
  } finally {
    isLoading.value = false
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
    
    console.log('发送登录请求数据:', loginData)
    
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
      // 登录成功，使用store设置用户信息（会自动处理localStorage和cookie）
      const userStore = useUserStore()
      // 从返回数据中提取token和用户信息
      const token = data.data.token
      const userData = { ...data.data }
      delete userData.token // 移除token，避免混乱
      
      userStore.setUser(userData, token)
      
      // 显示成功提示并开始进度条动画
      showSuccess.value = true
      isLoading.value = false // 停止加载状态
      
      // 进度条动画
      const progressInterval = setInterval(() => {
        progressWidth.value += 5
        if (progressWidth.value >= 100) {
          clearInterval(progressInterval)
          // 延迟跳转，让用户看到完整的成功反馈
          setTimeout(() => {
            if (process.client && window.navigateWithPageTransition) {
              window.navigateWithPageTransition('/');
            } else {
              router.push('/');
            }
          }, 300)
        }
      }, 30) // 总时长约2秒
    } else {
      // 登录失败，显示错误信息
      error.value = data.msg || '登录失败，请重试'
      refreshCaptcha()
    }
  } catch (err) {
    console.error('登录出错', err)
    error.value = '登录失败，请稍后重试'
    refreshCaptcha()
  } finally {
    isLoading.value = false
  }
}

// 隐藏成功提示
const hideSuccess = () => {
  showSuccess.value = false
  progressWidth.value = 0
}

// 页面元数据
definePageMeta({
  layout: false
})

useHead({
  title: '登录 - SBBS社区',
  meta: [
    { name: 'description', content: '登录SBBS社区账户' }
  ]
})
</script>

<style scoped>
.login-container {
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

.login-container::before {
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
.login-container::after {
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
.login-container .orb1,
.login-container .orb2,
.login-container .orb3 {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(40px);
  opacity: 0.3;
}

.login-container .orb1 {
  width: 300px;
  height: 300px;
  background: linear-gradient(45deg, #60a5fa, #3b82f6);
  top: -150px;
  left: -150px;
  animation: orb-move-1 20s ease-in-out infinite;
}

.login-container .orb2 {
  width: 250px;
  height: 250px;
  background: linear-gradient(135deg, #a855f7, #7c3aed);
  top: 50%;
  right: -125px;
  animation: orb-move-2 25s ease-in-out infinite reverse;
}

.login-container .orb3 {
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
.login-container .stars {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.login-container .star {
  position: absolute;
  width: 2px;
  height: 2px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  animation: twinkle 3s ease-in-out infinite;
}

.login-container .star:nth-child(1) { top: 20%; left: 10%; animation-delay: 0s; }
.login-container .star:nth-child(2) { top: 30%; left: 20%; animation-delay: 0.5s; }
.login-container .star:nth-child(3) { top: 40%; left: 80%; animation-delay: 1s; }
.login-container .star:nth-child(4) { top: 60%; left: 30%; animation-delay: 1.5s; }
.login-container .star:nth-child(5) { top: 70%; left: 70%; animation-delay: 2s; }
.login-container .star:nth-child(6) { top: 15%; left: 60%; animation-delay: 2.5s; }
.login-container .star:nth-child(7) { top: 85%; left: 15%; animation-delay: 0.8s; }
.login-container .star:nth-child(8) { top: 25%; left: 90%; animation-delay: 1.8s; }
.login-container .star:nth-child(9) { top: 55%; left: 50%; animation-delay: 1.2s; }
.login-container .star:nth-child(10) { top: 75%; left: 85%; animation-delay: 2.2s; }

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
.login-container .bubble {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  pointer-events: none;
  animation: bubble-float 15s ease-in-out infinite;
}

.login-container .bubble:nth-child(11) {
  width: 40px;
  height: 40px;
  left: 10%;
  animation-duration: 12s;
  animation-delay: 0s;
}

.login-container .bubble:nth-child(12) {
  width: 60px;
  height: 60px;
  left: 30%;
  animation-duration: 18s;
  animation-delay: 2s;
}

.login-container .bubble:nth-child(13) {
  width: 30px;
  height: 30px;
  left: 60%;
  animation-duration: 14s;
  animation-delay: 4s;
}

.login-container .bubble:nth-child(14) {
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

.login-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  padding: 2.5rem;
  width: 100%;
  max-width: 420px;
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
}

.login-header {
  text-align: center;
  margin-bottom: 2rem;
}

.login-title {
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

.login-title i {
  color: #60a5fa;
}

.login-subtitle {
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
  font-size: 1rem;
  font-weight: 400;
}

.login-form {
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

/* 验证码组 */
.captcha-group {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-image-wrapper {
  position: relative;
  height: 3.125rem;
  min-width: 110px;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 12px;
  cursor: pointer;
  border: 2px solid rgba(96, 165, 250, 0.3);
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.captcha-image:hover {
  border-color: #60a5fa;
  background: rgba(96, 165, 250, 0.08);
  transform: scale(1.02);
  box-shadow: 
    0 4px 12px rgba(96, 165, 250, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
}

.captcha-reload {
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.captcha-reload:hover {
  color: #60a5fa;
  background: rgba(96, 165, 250, 0.1);
  border-color: rgba(96, 165, 250, 0.3);
}

.captcha-reload i {
  font-size: 1.5rem;
}

/* 登录按钮 */
.login-btn {
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

.login-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.6s ease;
}

.login-btn:hover:not(:disabled)::before {
  left: 100%;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(96, 165, 250, 0.4);
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.login-btn.valid:not(:disabled) {
  background: linear-gradient(135deg, #34d399, #10b981);
  box-shadow: 0 4px 15px rgba(52, 211, 153, 0.3);
}

/* 选项 */
.login-options {
  text-align: center;
  margin-top: 1rem;
}

.forgot-link {
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  font-size: 0.9rem;
  transition: color 0.3s ease;
}

.forgot-link:hover {
  color: #60a5fa;
  text-decoration: underline;
}

/* 底部 */
.login-footer {
  text-align: center;
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.login-footer p {
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
}

.register-link {
  color: #60a5fa;
  text-decoration: none;
  font-weight: 600;
  transition: color 0.3s ease;
}

.register-link:hover {
  color: #93c5fd;
  text-decoration: underline;
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
  animation: fadeIn 0.3s ease-out;
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
  animation: slideUp 0.4s ease-out;
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

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { 
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to { 
    opacity: 1;
    transform: translateY(0) scale(1);
  }
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

/* 响应式调整 */
@media (max-width: 768px) {
  .login-container {
    padding: 1rem;
  }
  
  .login-card {
    padding: 2rem 1.5rem;
    max-width: 380px;
  }
  
  .login-title {
    font-size: 1.5rem;
  }
  
  .captcha-group {
    flex-direction: column;
  }
  
  .captcha-image-wrapper {
    width: 100%;
    min-width: auto;
  }
}
</style> 