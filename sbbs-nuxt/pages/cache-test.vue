<template>
  <div style="padding: 2rem; max-width: 800px; margin: 0 auto; font-family: system-ui, sans-serif;">
    <h1 style="color: #2563eb; margin-bottom: 2rem;">🧪 缓存策略测试页面</h1>
    
    <div style="background: #f8fafc; border: 2px solid #e2e8f0; padding: 1.5rem; border-radius: 12px; margin: 1rem 0;">
      <h3 style="color: #1e293b; margin-top: 0;">用户状态</h3>
      <p><strong>登录状态:</strong> 
        <span :style="{ color: isLoggedIn ? '#16a34a' : '#dc2626', fontWeight: 'bold' }">
          {{ isLoggedIn ? '✅ 已登录' : '❌ 未登录' }}
        </span>
      </p>
      <p v-if="isLoggedIn"><strong>用户名:</strong> {{ userInfo?.username || '未知' }}</p>
    </div>
    
    <div style="background: #eff6ff; border: 2px solid #bfdbfe; padding: 1.5rem; border-radius: 12px; margin: 1rem 0;">
      <h3 style="color: #1e40af; margin-top: 0;">缓存信息</h3>
      <p><strong>页面生成时间:</strong> 
        <code style="background: #1e293b; color: #f1f5f9; padding: 0.25rem 0.5rem; border-radius: 4px;">
          {{ pageGeneratedAt }}
        </code>
      </p>
      <p><strong>当前时间:</strong> {{ currentTime }}</p>
      <p><strong>预期缓存状态:</strong> 
        <span :style="{ color: isLoggedIn ? '#dc2626' : '#16a34a', fontWeight: 'bold' }">
          {{ expectedCacheStatus }}
        </span>
      </p>
    </div>
    
    <div style="background: #f0fdf4; border: 2px solid #bbf7d0; padding: 1.5rem; border-radius: 12px; margin: 1rem 0;">
      <h3 style="color: #166534; margin-top: 0;">💡 测试说明</h3>
      <ul style="margin: 0; padding-left: 1.5rem; line-height: 1.6;">
        <li><strong>已登录用户:</strong> 每次刷新看到新的生成时间（无缓存）</li>
        <li><strong>未登录用户:</strong> 5分钟内刷新看到相同生成时间（有缓存）</li>
        <li><strong>验证方法:</strong> 
          <ol style="margin: 0.5rem 0; padding-left: 1.5rem;">
            <li>打开浏览器开发者工具 (F12)</li>
            <li>切换到 Network 面板</li>
            <li>刷新页面</li>
            <li>查看页面请求的响应头</li>
          </ol>
        </li>
      </ul>
    </div>
    
    <div style="text-align: center; margin: 2rem 0;">
      <button @click="refreshPage" 
              style="padding: 0.75rem 1.5rem; background: #3b82f6; color: white; border: none; border-radius: 8px; cursor: pointer; margin: 0.5rem; font-weight: 600; transition: all 0.2s;">
        🔄 刷新页面
      </button>
      <button v-if="!isLoggedIn" @click="showLogin" 
              style="padding: 0.75rem 1.5rem; background: #10b981; color: white; border: none; border-radius: 8px; cursor: pointer; margin: 0.5rem; font-weight: 600; transition: all 0.2s;">
        🔐 测试登录
      </button>
      <button v-if="isLoggedIn" @click="logout" 
              style="padding: 0.75rem 1.5rem; background: #ef4444; color: white; border: none; border-radius: 8px; cursor: pointer; margin: 0.5rem; font-weight: 600; transition: all 0.2s;">
        🚪 退出登录
      </button>
    </div>
    
    <div style="background: #fefce8; border: 2px solid #fde047; padding: 1.5rem; border-radius: 12px; margin: 1rem 0;">
      <h4 style="color: #a16207; margin-top: 0;">🔍 响应头说明</h4>
      <div style="font-family: monospace; background: #1e293b; color: #f1f5f9; padding: 1rem; border-radius: 6px; margin: 0.5rem 0;">
        <div style="color: #16a34a;">✅ 未登录用户应该看到：</div>
        <div style="margin-left: 1rem;">
          Cache-Control: public, max-age=300, s-maxage=300<br>
          X-User-Status: anonymous
        </div>
        <br>
        <div style="color: #dc2626;">❌ 已登录用户应该看到：</div>
        <div style="margin-left: 1rem;">
          Cache-Control: no-cache, no-store, must-revalidate<br>
          X-User-Status: authenticated
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'

const userStore = useUserStore()
const { forceOpenLoginModal } = useLoginModal()

// 创建一个在服务端固定的时间戳，通过Nuxt的缓存机制来保持
// 这个时间戳会随着页面缓存一起被缓存
const serverTimestamp = process.server ? 
  new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }) : 
  // 客户端使用服务端传递的值，不重新生成
  '服务端生成'

// 页面生成时间（这个值会随着整个页面一起被缓存）
const pageGeneratedAt = ref(serverTimestamp)

const currentTime = ref('')

const isLoggedIn = computed(() => userStore.isLoggedIn)
const userInfo = computed(() => userStore.user)

const expectedCacheStatus = computed(() => {
  if (isLoggedIn.value) {
    return '🔴 动态内容（实时加载，无缓存）'
  } else {
    return '🟢 静态缓存（5分钟TTL，CDN缓存）'
  }
})

const updateCurrentTime = () => {
  currentTime.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const refreshPage = () => {
  window.location.reload()
}

const showLogin = () => {
  forceOpenLoginModal()
}

const logout = async () => {
  userStore.logout()
  // 等待一下再刷新，确保退出完成
  setTimeout(() => {
    window.location.reload()
  }, 100)
}

onMounted(() => {
  updateCurrentTime()
  // 每秒更新当前时间
  setInterval(updateCurrentTime, 1000)
})

// 设置页面标题
useHead({
  title: '缓存策略测试 - SBBS社区',
  meta: [
    { name: 'description', content: '测试SBBS社区的智能缓存策略' }
  ]
})
</script> 