<template>
  <div>
    <!-- 全局页面跳转加载动画 -->
    <PageTransitionLoader ref="pageTransitionLoader" />
    
    <header>
      <div class="header-content">
        <a href="/" class="logo">
          <i class="ri-discuss-line"></i>
          <span>SBBS社区</span>
          <span class="sparkle-1">✨</span>
          <span class="sparkle-2">⭐</span>
        </a>
        
        <div class="header-nav-tags">
          <div @click="filterByTag(null)" :class="{ active: selectedTagId === null }" class="header-tag-item">全部</div>
          <div v-for="tag in tags" 
             :key="tag.id" 
             @click="filterByTag(tag.id)" 
             :class="{ active: selectedTagId === tag.id }"
             class="header-tag-item">
            {{ tag.name }}
          </div>
        </div>
        
        <div class="header-search">
          <div style="display: flex; position: relative;">
            <div class="custom-dropdown" style="position: relative;">
              <div @click="toggleSearchTypeDropdown" class="dropdown-selected" style="display: flex; align-items: center; padding: 0.5rem 0.75rem; border: 1px solid var(--border-color); border-right: none; border-radius: 6px 0 0 6px; background-color: #f9fafb; cursor: pointer; min-width: 65px; height: 36px;">
                <i :class="searchType === 'post' ? 'ri-file-list-line' : 'ri-user-line'" style="margin-right: 5px; font-size: 0.9rem;"></i>
                <span style="font-size: 0.875rem; white-space: nowrap;">{{ searchType === 'post' ? '帖子' : '用户' }}</span>
                <i class="ri-arrow-down-s-line" style="margin-left: 5px; font-size: 0.9rem;"></i>
              </div>
              <div v-if="showSearchTypeDropdown" class="dropdown-menu" style="position: absolute; top: 100%; left: 0; z-index: 1000; min-width: 100px; background: white; border: 1px solid var(--border-color); border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); font-size: 0;">
                <div @click="selectSearchType('post')" class="dropdown-item" :class="{ 'active': searchType === 'post' }" style="display: flex; align-items: center; padding: 0.5rem 0.75rem; cursor: pointer; transition: all 0.2s; font-size: 0.875rem;">
                  <i class="ri-file-list-line" style="margin-right: 5px;"></i>
                  <span style="white-space: nowrap;">帖子</span>
                </div>
                <div @click="selectSearchType('user')" class="dropdown-item" :class="{ 'active': searchType === 'user' }" style="display: flex; align-items: center; padding: 0.5rem 0.75rem; cursor: pointer; transition: all 0.2s; font-size: 0.875rem;">
                  <i class="ri-user-line" style="margin-right: 5px;"></i>
                  <span style="white-space: nowrap;">用户</span>
                </div>
              </div>
            </div>
            <div style="position: relative; flex-grow: 1;">
              <input type="text" class="search-input" placeholder="搜索..." v-model="searchKeyword" @keyup.enter="searchContent" style="border-radius: 0 6px 6px 0; width: 100%; padding-right: 30px; height: 36px; box-sizing: border-box;">
              <button @click="searchContent" style="position: absolute; right: 8px; top: 50%; transform: translateY(-50%); background: none; border: none; color: #666; cursor: pointer;">
                <i class="ri-search-line"></i>
              </button>
            </div>
          </div>
        </div>
        
        <div class="cta-buttons">
          <template v-if="!isLoggedIn">
            <button @click="forceOpenLoginModal" class="btn btn-outline">登录</button>
            <NuxtLink to="/register" class="btn btn-primary">注册</NuxtLink>
          </template>
          <template v-else>
            <button 
              class="btn btn-primary btn-sm fixed-width-btn" 
              :class="{ 'btn-loading': isNavigatingToPublish }" 
              :disabled="isNavigatingToPublish"
              @click="navigateToPublishWithLoading">
              <template v-if="isNavigatingToPublish">
                <i class="ri-loader-4-line spinning"></i>
                <span>加载中</span>
              </template>
              <template v-else>
                <i class="ri-add-line"></i><span>发布</span>
              </template>
            </button>
            
            <div class="user-actions">
              <div class="user-avatar" @click="toggleNotificationPanel" title="通知">
                <img :src="getDisplayAvatarUrl()" alt="头像">
                <i class="ri-notification-line notification-icon" v-if="hasUnreadNotifications"></i>
                <!-- 通知面板 -->
                <NotificationPanel 
                  :show="showNotificationPanel" 
                  @close="closeNotificationPanel" 
                  @notifications-loaded="handleNotificationsLoaded" />
              </div>
              <div class="user-menu-container">
                <button class="user-menu-btn" @click="toggleUserMenu" title="用户菜单">
                  <i class="ri-more-line"></i>
                </button>
                <!-- 用户菜单下拉 -->
                <div v-if="showUserMenu" class="user-menu-dropdown">
                  <div class="user-menu-header">
                    <img :src="getDisplayAvatarUrl()" alt="头像" class="menu-avatar">
                    <div class="user-info">
                      <div class="username">{{ userInfo?.username }}</div>
                      <div class="user-role">{{ userInfo?.role }}</div>
                    </div>
                  </div>
                  <div class="menu-items">
                    <a :href="`/user/${userInfo?.id}`" class="menu-item" @click="closeUserMenu">
                      <i class="ri-user-line"></i>
                      <span>个人主页</span>
                    </a>
                    <div class="menu-item" @click.stop="openNotificationSettings">
                      <i class="ri-settings-3-line"></i>
                      <span>设置</span>
                    </div>
                    <div class="menu-divider"></div>
                    <div class="menu-item" @click="handleLogout">
                      <i class="ri-logout-box-line"></i>
                      <span>退出登录</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </header>
    
    <main class="container">
      <slot />
    </main>
    
    <!-- Toast通知组件 -->
    <Toast ref="toastRef" />
    
    <!-- 通知设置组件 -->
    <NotificationSettings 
      :show="showNotificationSettings" 
      @close="closeNotificationSettings"
      @updated="handleNotificationSettingsUpdated" />
    
    <!-- 登录弹窗组件 -->
    <LoginModal 
      :show="showLoginModal" 
      @close="closeLoginModal"
      @success="handleLoginSuccess" />
    
    <!-- Token验证组件已移除 - SSR阶段已经验证过token -->
    
    <footer>
      <div class="container">
        <div class="footer-content">
          <div class="footer-logo">
            <i class="ri-discuss-line"></i>
            <span>SBBS社区</span>
            <span class="sparkle-1">✨</span>
            <span class="sparkle-2">⭐</span>
          </div>

          <div class="footer-copyright">
            © {{ new Date().getFullYear() }} SBBS社区. 保留所有权利.
          </div>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick, defineEmits } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '~/stores/user'
import { useTagsStore } from '~/stores/tags'
import NotificationPanel from '~/components/NotificationPanel.vue'
import NotificationSettings from '~/components/NotificationSettings.vue'
import notificationManager from '~/utils/notificationManager'
import pointsManager from '~/utils/points'
import PageTransitionLoader from '~/components/PageTransitionLoader.vue'
import Toast from '~/components/Toast.vue'
import LoginModal from '~/components/LoginModal.vue'
import { getUserAvatarUrl } from '~/utils/avatarUtils'
import logger from '~/utils/logger'

const router = useRouter()
const userStore = useUserStore()
const API_BASE_URL = useApiBaseUrl()

// 登录弹窗状态管理
const { showLoginModal, closeLoginModal, requireLoginForAction, forceOpenLoginModal } = useLoginModal()

// 页面跳转加载器引用
const pageTransitionLoader = ref(null)

// 标签相关 - 使用共享的store
const tagsStore = useTagsStore()
const tags = computed(() => tagsStore.allTags)
const selectedTagId = computed(() => tagsStore.selectedTagId)

// 搜索功能
const searchKeyword = ref('')
const searchType = ref('post')
const showSearchTypeDropdown = ref(false)

// 用户状态 - 直接使用store的响应式状态
const isLoggedIn = computed(() => userStore.isLoggedIn)
const userInfo = computed(() => userStore.user)
const showUserMenu = ref(false)
const showNotificationPanel = ref(false)
const hasUnreadNotifications = ref(false)
const showNotificationSettings = ref(false)
const isNavigatingToPublish = ref(false)

// 标签数据现在由 tagsStore 统一管理，无需重复获取

// 按标签筛选
const filterByTag = (tagId) => {
  tagsStore.setSelectedTagId(tagId)
  
  // 检查当前是否在首页
  const currentPath = router.currentRoute.value.path
  console.log('Header filterByTag - 当前路径:', currentPath, '目标tagId:', tagId)
  
  if (currentPath !== '/') {
    // 不在首页，需要跳转到首页
    const url = tagId ? `/?tagId=${tagId}` : '/';
    
    if (process.client && window.navigateWithPageTransition) {
      window.navigateWithPageTransition(url);
    } else {
      if (tagId) {
        router.push({ path: '/', query: { tagId } })
      } else {
        router.push({ path: '/' })
      }
    }
  } else {
    // 在首页时，只更新URL查询参数，不进行页面跳转
    const newQuery = tagId ? { tagId } : {}
    console.log('Header filterByTag - 在首页，更新查询参数:', newQuery)
    router.replace({ path: '/', query: newQuery })
    
    // 通过事件通知页面组件更新
    if (process.client) {
      window.dispatchEvent(new CustomEvent('tag-filter-changed', {
        detail: { tagId }
      }))
    }
  }
};

// 切换搜索类型下拉菜单
const toggleSearchTypeDropdown = () => {
  showSearchTypeDropdown.value = !showSearchTypeDropdown.value;
};

// 选择搜索类型
const selectSearchType = (type) => {
  searchType.value = type;
  showSearchTypeDropdown.value = false;
};

// 搜索内容
const searchContent = () => {
  if (!searchKeyword.value.trim()) return;
  
  if (router.currentRoute.value.path === '/') {
    // 如果在首页，触发搜索事件
    window.dispatchEvent(new CustomEvent('search-content', { 
      detail: { 
        keyword: searchKeyword.value.trim(),
        type: searchType.value
      } 
    }));
  } else {
    // 如果不在首页，跳转到首页并带上搜索参数
    router.push(`/?search=${encodeURIComponent(searchKeyword.value.trim())}&type=${searchType.value}`);
  }
};



// 移除checkLogin函数 - 现在直接使用store的响应式状态
// 不再需要手动同步状态，computed会自动响应store变化

onMounted(() => {
  // 移除checkLogin调用 - store状态会自动同步
  // 标签数据现在由 tagsStore 统一管理，无需在此处获取
  
  // 初始化积分管理器
  pointsManager.init();
  
  // 添加通知管理器监听器
  notificationManager.addListener(notificationListener)
  
  // 如果用户已登录，获取缓存的通知状态
  if (isLoggedIn.value) {
    const cachedData = notificationManager.getCachedNotifications()
    hasUnreadNotifications.value = cachedData.unreadCount > 0
    
    // 自动加载最新通知（延迟500ms以避免与页面初始化冲突）
    setTimeout(() => {
      if (isLoggedIn.value) {
        notificationManager.autoLoadNotifications()
          .then(() => {
            logger.user('🔔 通知已自动更新')
          })
          .catch((error) => {
            logger.user('🔔 自动更新通知失败:', error)
          })
      }
    }, 500)
  }
  
  // 添加点击外部关闭菜单的事件监听
  if (process.client) {
    document.addEventListener('click', handleOutsideClick);
    
    // 监听URL参数变化
    if (router.currentRoute.value.query.tagId) {
      tagsStore.setSelectedTagId(router.currentRoute.value.query.tagId);
    }
    
    // 监听tag筛选变化事件，同步header状态
    window.addEventListener('tag-filter-changed', (event) => {
      tagsStore.setSelectedTagId(event.detail.tagId);
    });
    
    // 全局方法已经在 PageTransitionLoader 组件中自动设置
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleOutsideClick)
  notificationManager.removeListener(notificationListener)
  if (process.client) {
    window.removeEventListener('tag-filter-changed', () => {});
  }
})

// 切换用户菜单
const toggleUserMenu = (e) => {
  e.stopPropagation()
  showUserMenu.value = !showUserMenu.value
  showNotificationPanel.value = false // 关闭通知面板
}

// 切换通知面板
const toggleNotificationPanel = (e) => {
  e.stopPropagation()
  showNotificationPanel.value = !showNotificationPanel.value
  showUserMenu.value = false // 关闭用户菜单
}

// 关闭通知面板
const closeNotificationPanel = () => {
  showNotificationPanel.value = false
}

// 关闭用户菜单
const closeUserMenu = () => {
  showUserMenu.value = false
}

// 打开通知设置
const openNotificationSettings = (e) => {
  e?.stopPropagation() // 阻止事件冒泡
  showUserMenu.value = false // 先关闭用户菜单
  
  // 使用nextTick确保在下一个DOM更新周期中打开弹窗
  nextTick(() => {
    showNotificationSettings.value = true
  })
}

// 关闭通知设置
const closeNotificationSettings = () => {
  showNotificationSettings.value = false
}

  // 登录成功处理
  const handleLoginSuccess = () => {
    // 移除手动状态同步 - computed会自动响应store变化
    
    // 可以显示成功提示
    if (window.$toast) {
      window.$toast.success('登录成功！')
    }
  }

// 处理通知设置更新
const handleNotificationSettingsUpdated = (settings) => {
  console.log('通知设置已更新:', settings)
}

// 通知管理器监听器
const notificationListener = (data) => {
  hasUnreadNotifications.value = data.unreadCount > 0
}

// 处理通知加载完成事件
const handleNotificationsLoaded = (notificationCount) => {
  hasUnreadNotifications.value = notificationCount > 0
}

// 点击外部关闭菜单
const handleOutsideClick = (e) => {
  // 检查是否点击在用户菜单相关区域外
  if (showUserMenu.value && !e.target.closest('.user-menu-container')) {
    showUserMenu.value = false
  }
  
  // 检查是否点击在通知面板相关区域外
  if (showNotificationPanel.value && !e.target.closest('.user-avatar') && !e.target.closest('.notification-panel')) {
    showNotificationPanel.value = false
  }
  
  // 检查是否点击在通知设置弹窗相关区域外
  if (showNotificationSettings.value && 
      !e.target.closest('.notification-settings-modal') && 
      !e.target.closest('.user-menu-dropdown')) {
    showNotificationSettings.value = false
  }
  
  if (showSearchTypeDropdown.value && !e.target.closest('.custom-dropdown')) {
    showSearchTypeDropdown.value = false
  }
}

// 退出登录
const handleLogout = () => {
  userStore.logout()
  isLoggedIn.value = false
  userInfo.value = null
  showUserMenu.value = false
  router.push('/')
}

// 导航到发布页面（不显示动画）
const navigateToPublish = () => {
  if (process.client) {
    window._skipNextTransition = true;
  }
  router.push('/post/publish')
}

// 带加载状态的导航到发布页面
const navigateToPublishWithLoading = async () => {
  // 检查登录状态，如果未登录则强制显示登录弹窗
  const canProceed = requireLoginForAction('发布新帖')
  if (!canProceed) return
  
  isNavigatingToPublish.value = true
  
  try {
    if (process.client) {
      window._skipNextTransition = true;
    }
    
    // 短暂延迟让用户看到加载状态
    await new Promise(resolve => setTimeout(resolve, 300))
    
    await router.push('/post/publish')
  } catch (error) {
    console.error('导航到发布页面失败:', error)
  } finally {
    // 重置加载状态（即使导航成功也要重置，防止后退时状态异常）
    setTimeout(() => {
      isNavigatingToPublish.value = false
    }, 500)
  }
}

// 获取显示头像的URL
const getDisplayAvatarUrl = () => {
  if (userInfo.value && userInfo.value.avatar) {
    return getUserAvatarUrl(userInfo.value.avatar)
  }
  // 如果用户信息还未加载完成，使用默认头像
  return getUserAvatarUrl(null)
}
</script>

<style scoped>
:root {
  --border-color: #e5e7eb;
  --bg-color: #f5f5f5; 
  --card-bg: #fff;
  --text-color: #333;
  --text-secondary: #6b7280;
  --primary-color: #3b82f6;
  --hover-color: #f3f4f6;
}

body {
  background-color: var(--bg-color);
  background-image: linear-gradient(rgba(0,0,0,0.02) 1px, transparent 1px),
                    linear-gradient(90deg, rgba(0,0,0,0.02) 1px, transparent 1px);
  background-size: 20px 20px;
}

header {
  background-color: var(--card-bg);
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  transition: box-shadow 0.3s ease;
}

header:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  max-width: 1200px;
  margin: 0 auto;
}

.logo {
  display: flex;
  align-items: center;
  font-weight: 700;
  font-size: 1.25rem;
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  min-width: 120px;
  position: relative;
  background: linear-gradient(135deg, 
    #667eea 0%,
    #764ba2 20%,
    #f093fb 40%,
    #f5576c 60%,
    #4facfe 80%,
    #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientFlow 8s ease-in-out infinite;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 2px 4px rgba(102, 126, 234, 0.3));
}

.logo:hover {
  transform: translateY(-2px) scale(1.02);
  filter: drop-shadow(0 4px 8px rgba(102, 126, 234, 0.3));
  animation: gradientFlow 3s ease-in-out infinite, logoGlow 2s ease-in-out infinite;
}

.logo i {
  font-size: 1.5rem;
  margin-right: 0.5rem;
  background: linear-gradient(135deg, 
    #667eea 0%,
    #764ba2 20%,
    #f093fb 40%,
    #f5576c 60%,
    #4facfe 80%,
    #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientFlow 8s ease-in-out infinite;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
  position: relative;
}

.logo span {
  background: linear-gradient(135deg, 
    #667eea 0%,
    #764ba2 20%,
    #f093fb 40%,
    #f5576c 60%,
    #4facfe 80%,
    #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientFlow 8s ease-in-out infinite;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
  position: relative;
  letter-spacing: 0.5px;
}

@keyframes gradientFlow {
  0%, 100% {
    background-position: 0% 50%;
  }
  25% {
    background-position: 100% 50%;
  }
  50% {
    background-position: 50% 100%;
  }
  75% {
    background-position: 50% 0%;
  }
}

/* 更高级的炫彩效果 - 添加发光和脉冲效果 */
@keyframes logoGlow {
  0%, 100% {
    text-shadow: 
      0 0 3px rgba(102, 126, 234, 0.4),
      0 0 6px rgba(118, 75, 162, 0.3),
      0 0 9px rgba(240, 147, 251, 0.3),
      0 0 12px rgba(245, 87, 108, 0.2);
  }
  50% {
    text-shadow: 
      0 0 5px rgba(74, 172, 254, 0.6),
      0 0 10px rgba(0, 242, 254, 0.5),
      0 0 15px rgba(102, 126, 234, 0.4),
      0 0 20px rgba(240, 147, 251, 0.3);
  }
}

@keyframes sparkle {
  0%, 90%, 100% {
    opacity: 0;
  }
  45%, 55% {
    opacity: 1;
  }
}

/* 为logo添加星光效果 */
.logo .sparkle-1,
.logo .sparkle-2,
.footer-logo .sparkle-1,
.footer-logo .sparkle-2 {
  position: absolute;
  pointer-events: none;
  animation: sparkle 6s infinite;
  opacity: 0;
  z-index: 10;
}

.logo .sparkle-1,
.footer-logo .sparkle-1 {
  top: -5px;
  right: -10px;
  font-size: 12px;
}

.logo .sparkle-2,
.footer-logo .sparkle-2 {
  bottom: -5px;
  left: -5px;
  font-size: 10px;
  animation-delay: 3s;
}

/* 为支持的浏览器添加更多炫彩效果 */
@supports (backdrop-filter: blur(10px)) {
  .logo,
  .footer-logo {
    position: relative;
  }
  
  .logo:before,
  .footer-logo:before {
    content: '';
    position: absolute;
    top: -2px;
    left: -2px;
    right: -2px;
    bottom: -2px;
    background: linear-gradient(45deg, 
      rgba(102, 126, 234, 0.3),
      rgba(118, 75, 162, 0.3),
      rgba(240, 147, 251, 0.3),
      rgba(245, 87, 108, 0.3),
      rgba(74, 172, 254, 0.3),
      rgba(0, 242, 254, 0.3));
    border-radius: 8px;
    filter: blur(8px);
    opacity: 0;
    z-index: -1;
    transition: opacity 0.3s ease;
  }
  
  .logo:hover:before,
  .footer-logo:hover:before {
    opacity: 0.4;
    animation: gradientFlow 2s ease-in-out infinite;
  }
}

/* 为了在不支持background-clip的浏览器中显示备用样式 */
@supports not (-webkit-background-clip: text) {
  .logo,
  .logo i,
  .logo span,
  .footer-logo,
  .footer-logo i,
  .footer-logo span {
    color: #667eea;
    text-shadow: 0 0 10px rgba(102, 126, 234, 0.6);
  }
}

.header-nav-tags {
  display: flex;
  gap: 1.25rem; 
  flex-grow: 1; 
  overflow-x: auto; 
  margin: 0 1.5rem; /* 修改左右边距 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE and Edge */
  padding: 0.25rem 0;
  justify-content: flex-start;
}

.header-nav-tags::-webkit-scrollbar {
  display: none; /* Chrome, Safari, Opera */
}

.header-nav-tags a,
.header-nav-tags .header-tag-item {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.875rem; 
  transition: all 0.2s ease;
  padding: 0.25rem 0;
  border-bottom: 2px solid transparent; 
  white-space: nowrap;
  position: relative;
  letter-spacing: 0.01em;
  cursor: pointer;
}

.header-nav-tags a:hover,
.header-nav-tags a.active,
.header-nav-tags .header-tag-item:hover,
.header-nav-tags .header-tag-item.active {
  color: var(--primary-color);
}

.header-nav-tags a.active,
.header-nav-tags .header-tag-item.active {
  border-bottom-color: var(--primary-color);
  font-weight: 500; 
}

.header-nav-tags a.active::after,
.header-nav-tags .header-tag-item.active::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: var(--primary-color);
  transform: scaleX(1);
  transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.header-nav-tags a:not(.active)::after,
.header-nav-tags .header-tag-item:not(.active)::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: var(--primary-color);
  transform: scaleX(0);
  transition: transform 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  transform-origin: center;
}

.header-nav-tags a:not(.active):hover::after,
.header-nav-tags .header-tag-item:not(.active):hover::after {
  transform: scaleX(0.6);
}

.header-search {
  position: relative;
  width: 240px;
  margin-right: 0.75rem;
}

.search-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  padding-right: 30px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  font-size: 0.875rem;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  height: 36px;
  box-sizing: border-box;
}

.search-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.custom-dropdown .dropdown-selected {
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
}

.custom-dropdown .dropdown-item {
  transition: all 0.15s ease;
}

.custom-dropdown .dropdown-item:hover {
  background-color: #f0f7ff; 
  color: var(--primary-color);
}

.custom-dropdown .dropdown-item.active {
  background-color: #f0f7ff; 
  color: var(--primary-color);
  font-weight: 500;
}

.dropdown-selected:hover {
  border-color: #d0d7de; 
  background-color: #f5f8fa; 
}

.dropdown-menu {
  animation: fadeInDown 0.2s ease;
  transform-origin: top center;
}

.cta-buttons {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
}

/* 固定宽度按钮 - 防止加载时变大 */
.fixed-width-btn {
  width: 68px !important; /* 减少宽度，适应小屏幕 */
  min-width: 68px !important;
  max-width: 68px !important;
  text-align: center !important;
  justify-content: center !important;
  flex-shrink: 0 !important; /* 不允许收缩 */
  white-space: nowrap !important; /* 防止文本换行 */
  overflow: hidden !important; /* 防止溢出 */
  font-size: 0.75rem !important; /* 稍微减小字体以适应宽度 */
}

.fixed-width-btn span {
  display: inline-block;
  width: auto;
  text-align: center;
}

.fixed-width-btn i {
  width: 14px; /* 稍微减小图标宽度 */
  text-align: center;
  flex-shrink: 0;
}

/* 在更小的屏幕上进一步减小按钮 */
@media (max-width: 768px) {
  .fixed-width-btn {
    width: 60px !important;
    min-width: 60px !important;
    max-width: 60px !important;
    font-size: 0.7rem !important;
  }
  
  .fixed-width-btn i {
    width: 12px;
  }
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
  position: relative;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  font-weight: 500;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  border: none;
}

.btn i {
  font-size: 0.875rem;
  line-height: 1;
}

.btn-primary {
  background-color: var(--primary-color);
  color: white;
  border: 1px solid transparent;
}

.btn-primary:hover {
  background-color: #2563eb;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(37, 99, 235, 0.2);
}

.btn-outline {
  background-color: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-color);
}

.btn-outline:hover {
  background-color: var(--hover-color);
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0,0,0,0.05);
}

.btn-ghost {
  background-color: transparent;
  border: 1px solid transparent;
  color: var(--text-color);
}

.btn-ghost:hover {
  background-color: var(--hover-color);
  transform: translateY(-1px);
}

.btn-sm {
  padding: 0.25rem 0.4rem;
  font-size: 0.75rem;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-avatar {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
}

.user-avatar:hover {
  transform: translateY(-1px);
}

.user-avatar img {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid var(--border-color);
  transition: all 0.2s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.user-avatar:hover img {
  box-shadow: 0 3px 6px rgba(0,0,0,0.1);
}

.notification-icon {
  position: absolute;
  top: -5px;
  right: -5px;
  background: var(--primary-color);
  color: white;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  border: 2px solid white;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.user-menu-btn {
  padding: 0.5rem;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: white;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
}

.user-menu-btn:hover {
  background: var(--hover-color);
  color: var(--text-color);
  border-color: #d1d5db;
  transform: translateY(-1px);
}

.user-menu-container {
  position: relative;
}

.user-menu-dropdown {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  width: 250px;
  overflow: hidden;
  z-index: 1000;
  animation: fadeInDown 0.2s ease;
  transform-origin: top right;
  border: 1px solid #e5e7eb;
}

.user-menu-dropdown .user-menu-header {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  background: #fafafa;
}

.user-menu-dropdown .menu-avatar {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  object-fit: cover;
  margin-right: 0.75rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.user-menu-dropdown .user-info .username {
  font-weight: 600;
  color: #1a1a1a;
  font-size: 0.875rem;
}

.user-menu-dropdown .user-info .user-role {
  color: #666;
  font-size: 0.75rem;
  margin-top: 0.125rem;
}

.user-menu-dropdown .menu-items {
  padding: 0.5rem 0;
}

.user-menu-dropdown .menu-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 1rem;
  color: var(--text-color);
  text-decoration: none;
  font-size: 0.875rem;
  transition: all 0.15s ease;
  cursor: pointer;
}

.user-menu-dropdown .menu-item:hover {
  background-color: #f8fafc;
  color: var(--primary-color);
}

.user-menu-dropdown .menu-item i {
  margin-right: 0.5rem;
  font-size: 1rem;
  transition: transform 0.15s ease;
}

.user-menu-dropdown .menu-item:hover i {
  transform: translateX(2px);
}

.user-menu-dropdown .menu-divider {
  height: 1px;
  background-color: var(--border-color);
  margin: 0.5rem 0;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

footer {
  background-color: white;
  border-top: 1px solid var(--border-color);
  padding: 2rem 0;
  margin-top: 3rem;
  box-shadow: 0 -1px 3px rgba(0,0,0,0.03);
}

.footer-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.footer-logo {
  display: flex;
  align-items: center;
  font-weight: 700;
  font-size: 1.25rem;
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  background: linear-gradient(135deg, 
    #667eea 0%,
    #764ba2 20%,
    #f093fb 40%,
    #f5576c 60%,
    #4facfe 80%,
    #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientFlow 8s ease-in-out infinite;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 2px 4px rgba(102, 126, 234, 0.3));
}

.footer-logo:hover {
  transform: translateY(-2px) scale(1.02);
  filter: drop-shadow(0 4px 8px rgba(102, 126, 234, 0.3));
  animation: gradientFlow 3s ease-in-out infinite, logoGlow 2s ease-in-out infinite;
}

.footer-logo i {
  font-size: 1.5rem;
  margin-right: 0.5rem;
  background: linear-gradient(135deg, 
    #667eea 0%,
    #764ba2 20%,
    #f093fb 40%,
    #f5576c 60%,
    #4facfe 80%,
    #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientFlow 8s ease-in-out infinite;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
}

.footer-logo span {
  background: linear-gradient(135deg, 
    #667eea 0%,
    #764ba2 20%,
    #f093fb 40%,
    #f5576c 60%,
    #4facfe 80%,
    #00f2fe 100%);
  background-size: 400% 400%;
  animation: gradientFlow 8s ease-in-out infinite;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent;
  letter-spacing: 0.5px;
}

.footer-links {
  display: flex;
  gap: 1.5rem;
}

.footer-links a {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.875rem;
  transition: all 0.2s ease;
  position: relative;
}

.footer-links a::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 100%;
  height: 1px;
  background-color: var(--primary-color);
  transform: scaleX(0);
  transition: transform 0.25s ease;
}

.footer-links a:hover {
  color: var(--primary-color);
}

.footer-links a:hover::after {
  transform: scaleX(1);
}

.footer-copyright {
  color: var(--text-secondary);
  font-size: 0.75rem;
  margin-top: 0.5rem;
}

/* 动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式调整 */
@media (max-width: 768px) {
  .header-nav-tags {
    display: none;
  }
  
  .header-search {
    width: 180px;
  }
  
  .user-menu-dropdown {
    width: 220px;
    right: -1rem;
  }
  
  .user-menu-dropdown .user-menu-header {
    padding: 0.625rem 0.75rem;
  }
  
  .user-menu-dropdown .menu-avatar {
    width: 36px;
    height: 36px;
  }
  
  .user-menu-dropdown .menu-item {
    padding: 0.5rem 0.75rem;
    font-size: 0.8125rem;
  }
  
  /* 小屏幕下进一步缩小发布按钮 */
  .header-content .btn-primary {
    min-width: 65px;
    height: 32px;
    font-size: 0.8125rem;
    padding: 0;
    gap: 3px;
  }
  
  .header-content .btn-primary i {
    font-size: 0.8125rem;
  }
  
  .cta-buttons {
    gap: 0.5rem;
  }
}

@media (max-width: 480px) {
  .header-content {
    padding: 0.5rem 0.75rem;
  }
  
  .header-search {
    width: 140px;
  }
  
  /* 超小屏幕下只显示图标 */
  .header-content .btn-primary span {
    display: none;
  }
  
  .header-content .btn-primary {
    min-width: 36px;
    width: 36px;
    height: 36px;
    justify-content: center;
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
}

/* 按钮加载状态样式 */
.btn-loading {
  opacity: 0.7;
  cursor: not-allowed;
  pointer-events: none;
}

.btn-loading .spinning {
  animation: spin 1s linear infinite;
}

.btn-loading::before {
  display: none !important;
}

.btn-loading::after {
  display: none !important;
}

/* 统一 header 内主要按钮的样式 */
.header-content .btn-primary,
.header-content .btn-outline {
  min-width: 75px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
  font-size: 0.875rem;
  padding: 0 0.75rem; /* 为 outline button 恢复一些内边距 */
  border-radius: 6px;
  line-height: 1;
  vertical-align: middle;
}

/* 按钮的特定样式 */
.header-content .btn-primary {
  border: none;
}

/* 发布按钮专用样式 */
.header-content .btn-primary i {
  font-size: 0.875rem;
  margin: 0;
  line-height: 1;
  vertical-align: middle;
  display: inline-block;
}

.header-content .btn-primary span {
  line-height: 1;
  margin: 0;
  vertical-align: middle;
  display: inline-block;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 标签相关样式 */
</style> 