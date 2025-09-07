<template>
  <div class="layout-container">
    <!-- 添加高亮通知组件 -->
    <PostHighlighter v-if="$route.query.highlight === 'new'" />
    
    <div>
      <div class="container">
        <div class="grid-layout">
          <!-- 主内容区域，由使用组件的页面通过slot提供 -->
          <main>
            <slot />
          </main>

          <!-- 侧边栏 -->
          <aside>
            <!-- 分类筛选 -->
            <div class="sidebar-section">
              <div class="section-header">
                <h2><i class="ri-compass-3-line"></i> 快捷导航</h2>
              </div>
              <ul style="list-style: none; padding: 0.5rem; margin: 0;">
                <li 
                    class="nav-item"
                    :class="{ 'active': selectedTagId === null }"
                    @click="filterByTag(null)">
                  <i class="ri-apps-line"></i> 全部
                </li>
                <li v-for="tag in tags" 
                    :key="tag.id"
                    class="nav-item"
                    :class="{ 'active': selectedTagId === tag.id }"
                    @click="filterByTag(tag.id)">
                  <i :class="getTagIcon(tag.id, tag.name)"></i> {{ tag.name }}
                </li>
              </ul>
            </div>
            
            <!-- 个人中心 -->
            <div class="sidebar-section">
              <div class="section-header">
                <h2><i class="ri-user-line"></i> 个人中心</h2>
              </div>
              <div v-if="!isLoggedIn" style="text-align: center; padding: 1rem;">
                <p style="margin-bottom: 1rem; color: #999; font-size: 0.9rem;">登录后可以发布和收藏帖子</p>
                <div style="display: flex; gap: 0.5rem;">
                  <button @click="forceOpenLoginModal" class="btn btn-outline">登录</button>
                  <NuxtLink to="/register" class="btn btn-primary">注册</NuxtLink>
                </div>
              </div>
              <div v-if="isLoggedIn && userInfo" style="padding: 1rem;">
                <div style="display: flex; align-items: center; margin-bottom: 1rem;">
                  <img :src="getUserAvatar(userInfo.id)" alt="用户头像" class="user-profile-avatar"/>
                  <div>
                    <div style="font-weight: 600; color: #1a1a1a; font-size: 0.9375rem;">{{ userInfo.username }}</div>
                    <div style="color: #666; font-size: 0.75rem; margin-top: 0.125rem;">{{ userInfo.role }}</div>
                  </div>
                </div>
                <div style="display: flex; gap: 0.5rem;">
                  <a :href="`/user/${userInfo.id}`" class="profile-btn">
                    <i class="ri-user-line"></i> 个人主页
                  </a>
                  <button @click="logout" class="logout-btn">
                    <i class="ri-logout-box-line"></i> 退出登录
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 本周最火帖子 -->
            <div class="sidebar-section">
              <div class="section-header">
                <h2><i class="ri-fire-line"></i> 本周最火</h2>
              </div>
              <div class="hot-posts-container">
                <div v-if="hotPosts.length === 0" class="no-hot-posts">
                  暂无热门帖子
                </div>
                <a 
                  v-for="post in hotPosts" 
                  :key="post.id" 
                  :href="`/post/${post.id}?page=1`" 
                  class="hot-post-item"
                  @click="setImagePageTitle(post.title, post.id)">
                  <div class="hot-post-title">{{ post.title }}</div>
                  <div class="hot-post-meta">
                    <span class="hot-post-username">
                      <i class="ri-user-line"></i> {{ post.username }}
                    </span>
                    <span class="hot-post-stats">
                      <span class="hot-post-stat"><i class="ri-thumb-up-line"></i> {{ post.likeCount }}</span>
                      <span class="hot-post-stat"><i class="ri-message-2-line"></i> {{ post.commentCount }}</span>
                    </span>
                  </div>
                </a>
              </div>
            </div>

            <!-- 社区统计 -->
            <div class="sidebar-section">
              <div class="section-header">
                <h2><i class="ri-bar-chart-grouped-line"></i> 社区统计</h2>
              </div>
              <div style="padding: 0.5rem 0;">
                <div class="stats-grid">
                  <div class="stat-item">
                    <div class="stat-number">{{ communityStats.totalPosts }}</div>
                    <div class="stat-label">总帖子数</div>
                  </div>
                  <div class="stat-item">
                    <div class="stat-number">{{ communityStats.totalUsers }}</div>
                    <div class="stat-label">总用户数</div>
                  </div>
                  <div class="stat-item">
                    <div class="stat-number" style="color: #05c895;">{{ communityStats.newPostsToday }}</div>
                    <div class="stat-label">今日新帖</div>
                  </div>
                  <div class="stat-item">
                    <div class="stat-number" style="color: #5364f7;">{{ communityStats.newUsersToday }}</div>
                    <div class="stat-label">今日新用户</div>
                  </div>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { 
  ref, watch, provide, onMounted, onUnmounted,
  computed, useAsyncData, useNuxtApp,
  useRouter, useRoute
} from '#imports'
import { useUserStore } from '~/stores/user'
import { usePostStore } from '~/stores/post'
import { useTagsStore } from '~/stores/tags'
import PostHighlighter from '~/components/PostHighlighter.vue'
import pointsManager from '~/utils/points'
import { getUserAvatarUrl } from '~/utils/avatarUtils'

// 登录弹窗功能
const { forceOpenLoginModal } = useLoginModal()

const props = defineProps({
  hideRightSidebar: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['refresh'])

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const postStore = usePostStore()
const tagsStore = useTagsStore()

// 获取API基础URL
const API_BASE_URL = useApiBaseUrl()

// 帮助函数: 从客户端获取cookie值
const getCookieValue = (name) => {
  if (process.client) {
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'))
    return match ? match[2] : null
  }
  return null
}

// 帮助函数: 从cookie字符串中获取值 (用于SSR)
const getCookieValueFromString = (cookieString, name) => {
  const match = cookieString.match(new RegExp('(^| )' + name + '=([^;]+)'))
  return match ? match[2] : null
}

// 用户数据
const isLoggedIn = computed(() => userStore.isLoggedIn)
const userInfo = computed(() => userStore.user || {})

// 标签相关 - 使用store数据
const tags = computed(() => tagsStore.allTags)
const selectedTagId = computed(() => tagsStore.selectedTagId)

// 社区统计数据
const communityStats = ref({
  totalPosts: 0,
  totalUsers: 0,
  newPostsToday: 0,
  newUsersToday: 0
})

// 本周最火帖子
const hotPosts = ref([])

// 在LayoutWithSidebar组件中添加全局标识来检测新发布的帖子
// 在script setup中的常量部分添加
const latestPostTitle = ref('');
const highlightNewPost = ref(false);

// 标签筛选
const filterByTag = (tagId) => {
  tagsStore.setSelectedTagId(tagId)
  
  // 重定向到首页并添加查询参数
  if (router.currentRoute.value.path !== '/') {
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
    // 在首页时，更新URL查询参数并触发重新获取数据
    const newQuery = tagId ? { tagId } : {}
    router.replace({ path: '/', query: newQuery })
    
    // 通过事件通知页面组件更新
    if (process.client) {
      window.dispatchEvent(new CustomEvent('tag-filter-changed', {
        detail: { tagId }
      }))
    }
  }
}

// 获取标签图标 - 使用store方法
const getTagIcon = (tagId, tagName) => {
  return tagsStore.getTagIcon(tagId, tagName)
}

// 获取用户头像
const getUserAvatar = (userId) => {
  // 使用和layouts/default.vue一致的头像获取逻辑
  if (userInfo.value && userInfo.value.avatar) {
    return getUserAvatarUrl(userInfo.value.avatar)
  }
  // 如果用户信息还未加载完成，使用默认头像
  return getUserAvatarUrl(null)
}

// 退出登录
const logout = () => {
  userStore.logout()
  
  if (process.client && window.navigateWithPageTransition) {
    window.navigateWithPageTransition('/auth/login')
  } else {
    router.push('/auth/login')
  }
}

// 标签数据现在由 tagsStore 统一管理，无需重复获取

// 从URL查询参数获取选中的标签
if (process.client) {
  const tagIdFromQuery = router.currentRoute.value.query.tagId
  if (tagIdFromQuery) {
    tagsStore.setSelectedTagId(tagIdFromQuery)
  }
}

// 获取社区统计
const { data: statsData } = await useAsyncData('sidebar-stats', async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/v1/stats/overview`)
    const data = await response.json()
    if (data.code === 200) {
      return data.data || {
        totalPosts: 0,
        totalUsers: 0,
        newPostsToday: 0,
        newUsersToday: 0
      }
    }
    return {
      totalPosts: 0,
      totalUsers: 0,
      newPostsToday: 0,
      newUsersToday: 0
    }
  } catch (error) {
    console.error('获取社区统计失败:', error)
    return {
      totalPosts: 0,
      totalUsers: 0,
      newPostsToday: 0,
      newUsersToday: 0
    }
  }
})

// 获取本周最火帖子
const { data: hotPostsData } = await useAsyncData('hot-posts', async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/v2/hot/posts`)
    const data = await response.json()
    if (data.code === 200) {
      return data.data || []
    }
    return []
  } catch (error) {
    console.error('获取热门帖子失败:', error)
    return []
  }
})

// 监听统计数据的变化
watch(() => statsData.value, (newStats) => {
  if (newStats) {
    communityStats.value = newStats
  }
}, { immediate: true })

// 监听热门帖子数据的变化
watch(() => hotPostsData.value, (newHotPosts) => {
  if (newHotPosts) {
    hotPosts.value = newHotPosts.slice(0, 5) // 只取前5条
  }
}, { immediate: true })

// 检查是否需要高亮最新发布的帖子
onMounted(() => {
  if (router.currentRoute.value.query.highlight === 'new' && process.client) {
    const savedTitle = localStorage.getItem('sbbs-latest-post-title');
    const savedTime = localStorage.getItem('sbbs-latest-post-time');
    
    if (savedTitle && savedTime) {
      // 检查是否在十分钟内发布的
      const publishTime = parseInt(savedTime);
      const currentTime = new Date().getTime();
      const tenMinutes = 10 * 60 * 1000;
      
      if (currentTime - publishTime < tenMinutes) {
        latestPostTitle.value = savedTitle;
        highlightNewPost.value = true;
        
        // 30秒后自动取消高亮
        setTimeout(() => {
          highlightNewPost.value = false;
        }, 30000);
      }
    }
  }
  
  // 监听tag筛选变化事件，同步侧边栏状态
  if (process.client) {
    window.addEventListener('tag-filter-changed', (event) => {
      tagsStore.setSelectedTagId(event.detail.tagId);
    });
  }
})

// 清理事件监听器
onUnmounted(() => {
  if (process.client) {
    window.removeEventListener('tag-filter-changed', () => {});
  }
})

// 添加一个方法
// 检查帖子是否需要高亮显示
const shouldHighlightPost = (postTitle) => {
  return highlightNewPost.value && postTitle === latestPostTitle.value;
};

// 使用provide让子组件可以访问高亮方法
provide('shouldHighlightPost', shouldHighlightPost);

// 添加一个新的方法来设置页面标题
const setImagePageTitle = (title, postId) => {
  // 设置页面标题为简洁的格式：帖子标题 - SBBS社区
  const pageTitle = `${title} - SBBS社区`
  
  // 如果在客户端，直接设置document.title
  if (process.client) {
    document.title = pageTitle
  }
  
  // 也可以使用localStorage存储，供目标页面使用
  if (process.client) {
    localStorage.setItem('sbbs-post-title', pageTitle)
    localStorage.setItem('sbbs-post-id', postId.toString())
  }
}
</script>

<style scoped>
.container {
  max-width: 1200px; /* 回调到合适的宽度 */
  margin: 0 auto;
  padding: 0 1rem;
}

.grid-layout {
  display: grid;
  grid-template-columns: minmax(650px, 850px) 280px; /* 左列最小650px，最大850px，右列固定280px */
  gap: 1rem; /* 合适的间距 */
  margin-top: 1rem;
  justify-content: center; /* 整个网格在容器中居中 */
}

.main-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 1.25rem;
  display: flex;
  gap: 1.25rem;
}

.content-wrapper {
  flex: 1;
  min-width: 0; /* 防止内容溢出 */
}

.sidebar {
  width: 280px;
  flex-shrink: 0;
}

@media (max-width: 1400px) {
  .grid-layout {
    grid-template-columns: minmax(600px, 1fr) 280px; /* 中等屏幕：左列自适应但有最小宽度 */
  }
}

@media (max-width: 1024px) {
  .grid-layout {
    grid-template-columns: minmax(0, 1fr) 280px; /* 小一点的屏幕：左列完全自适应 */
  }
}

@media (max-width: 768px) {
  .grid-layout {
    grid-template-columns: 1fr; /* 小屏幕：单列布局 */
    gap: 1rem;
  }
  
  .main-container {
    flex-direction: column;
  }
  
  .sidebar {
    width: 100%;
  }
}

.sidebar-section {
  background-color: white;
  border-radius: 8px; /* 减小圆角 */
  box-shadow: 0 2px 10px rgba(0,0,0,0.05), 0 0 0 1px rgba(0,0,0,0.02);
  margin-bottom: 1rem; /* 减小底部间距 */
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.sidebar-section:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(0,0,0,0.05);
}

/* 热门帖子区块特殊样式 */
.sidebar-section:nth-child(3) {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(248, 250, 252, 1));
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(226, 232, 240, 0.6);
}

.sidebar-section:nth-child(3) .section-header h2 i {
  color: #f97316;
  animation: gentle-glow 3s ease-in-out infinite alternate;
}

@keyframes gentle-glow {
  0% { 
    color: #f97316;
    text-shadow: 0 0 5px rgba(249, 115, 22, 0.3);
  }
  100% { 
    color: #ea580c;
    text-shadow: 0 0 8px rgba(249, 115, 22, 0.5);
  }
}

/* 添加玻璃态效果 */
.sidebar-section.glass-effect {
  background-color: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.section-header {
  padding: 0.6rem 0.75rem; /* 减小内边距 */
  border-bottom: 1px solid rgba(226,232,240,0.6);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-header h2 {
  margin: 0;
  font-size: 0.85rem; /* 减小字号 */
  font-weight: 600;
  color: #1a202c;
  display: flex;
  align-items: center;
}

.section-header h2 i {
  margin-right: 0.5rem;
  color: #3b82f6;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.4rem; /* 减小间距 */
  padding: 0.5rem; /* 减小内边距 */
}

.stat-item {
  background-color: #f9fafb;
  padding: 0.5rem; /* 减小内边距 */
  border-radius: 6px;
  text-align: center;
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  border: 1px solid transparent;
  cursor: pointer;
}

.stat-number {
  font-weight: 600;
  font-size: 1rem; /* 减小字号 */
  color: #111;
  transition: all 0.3s ease;
}

.stat-label {
  font-size: 0.7rem; /* 减小字号 */
  color: #64748b;
  margin-bottom: 0.2rem;
}

.profile-btn, .logout-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.4rem 0.75rem; /* 减小垂直内边距 */
  font-size: 0.8125rem;
  color: #334155;
  background-color: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
}

.profile-btn:hover, .logout-btn:hover {
  background-color: #f5f5f5;
  color: #0066cc;
  border-color: #3b82f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.profile-btn i, .logout-btn i {
  margin-right: 0.35rem;
  font-size: 0.875rem;
}

.user-profile-avatar {
  width: 44px; /* 略微缩小 */
  height: 44px;
  border-radius: 8px;
  margin-right: 0.75rem;
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.btn {
  display: inline-flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  font-weight: 500;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.15s;
  text-decoration: none;
}

.btn i {
  margin-right: 0.375rem;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
  border: 1px solid transparent;
}

.btn-primary:hover {
  background-color: #2563eb;
}

.btn-outline {
  background-color: transparent;
  border: 1px solid #e5e7eb;
  color: #333;
}

.btn-outline:hover {
  background-color: #f9fafb;
}

.btn-sm {
  padding: 0.25rem 0.4rem;
  font-size: 0.75rem;
}

/* 导航项样式 */
.nav-item {
  padding: 0.35rem 0.6rem; /* 减小内边距 */
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.4rem; /* 减小间距 */
  transition: all 0.15s;
  font-size: 0.75rem; /* 减小字号 */
  border-radius: 4px; /* 减小圆角 */
  margin: 0.1rem 0;
}

.nav-item:hover {
  background-color: #f5f5f5;
}

.nav-item.active {
  font-weight: 600;
  color: #0066cc;
  background-color: #f0f7ff;
}

.nav-item.active:hover {
  background-color: #e6f0fd;
}

.nav-item i {
  font-size: 1rem;
  color: #64748b;
  transition: all 0.15s;
}

.nav-item:hover i {
  color: #3b82f6;
}

.nav-item.active i {
  color: #0066cc;
}

/* 总帖子数的卡片效果 */
.stat-item:nth-child(1):hover {
  background-color: rgba(59, 130, 246, 0.1);
  transform: translateY(-5px);
  box-shadow: 0 10px 15px -3px rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.2);
}

/* 总用户数的卡片效果 */
.stat-item:nth-child(2):hover {
  background-color: rgba(99, 102, 241, 0.1);
  transform: translateY(-5px) rotate(2deg);
  box-shadow: 0 10px 15px -3px rgba(99, 102, 241, 0.15);
  border-color: rgba(99, 102, 241, 0.2);
}

/* 今日新帖的卡片效果 */
.stat-item:nth-child(3):hover {
  background-color: rgba(5, 200, 149, 0.1);
  transform: translateY(-5px) scale(1.05);
  box-shadow: 0 10px 15px -3px rgba(5, 200, 149, 0.15);
  border-color: rgba(5, 200, 149, 0.2);
}

/* 今日新用户的卡片效果 */
.stat-item:nth-child(4):hover {
  background-color: rgba(83, 100, 247, 0.1);
  transform: translateY(-5px) rotate(-2deg);
  box-shadow: 0 10px 15px -3px rgba(83, 100, 247, 0.15);
  border-color: rgba(83, 100, 247, 0.2);
}

/* 统计数字的悬停效果 */
.stat-item:hover .stat-number {
  transform: scale(1.15);
  text-shadow: 0 0 8px rgba(0, 0, 0, 0.1);
}

/* 不同类型统计项的数字颜色 */
.stat-item:nth-child(1) .stat-number {
  color: #3b82f6;
}

.stat-item:nth-child(2) .stat-number {
  color: #6366f1;
}

.stat-item:nth-child(3) .stat-number {
  color: #05c895;
}

.stat-item:nth-child(4) .stat-number {
  color: #5364f7;
}

/* 全局的帖子高亮样式 */
.post-item.highlight-new-post {
  animation: highlight-pulse 2s infinite;
  border-left: 4px solid #3b82f6 !important;
  background-color: rgba(59, 130, 246, 0.1) !important;
  position: relative;
  z-index: 1;
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(59, 130, 246, 0.2);
}

@keyframes highlight-pulse {
  0% {
    box-shadow: 0 5px 15px rgba(59, 130, 246, 0.2);
  }
  50% {
    box-shadow: 0 5px 20px rgba(59, 130, 246, 0.4);
  }
  100% {
    box-shadow: 0 5px 15px rgba(59, 130, 246, 0.2);
  }
}

/* 热门帖子样式 */
.hot-posts-container {
  padding: 0;
}

.hot-post-item {
  display: block;
  padding: 0.5rem 0.6rem;
  margin: 0;
  text-decoration: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  background-color: transparent;
  border-bottom: 1px solid rgba(226,232,240,0.4);
  border-left: 3px solid transparent;
  border-radius: 0 6px 6px 0;
}

.hot-post-item:last-child {
  border-bottom: none;
}

.hot-post-item:hover {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.06), rgba(59, 130, 246, 0.03));
  transform: translateX(4px);
  border-left-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

.hot-post-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 0;
  height: 100%;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.08), transparent);
  transition: width 0.3s ease;
  z-index: -1;
}

.hot-post-item:hover::before {
  width: 100%;
}

.hot-post-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #334155;
  margin-bottom: 0.25rem;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  position: relative;
  transition: color 0.2s ease;
}

.hot-post-item:hover .hot-post-title {
  color: #1e40af;
}

.hot-post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.65rem;
  color: #64748b;
  position: relative;
}

.hot-post-username {
  display: flex;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
  transition: all 0.2s ease;
}

.hot-post-username i {
  margin-right: 4px;
  font-size: 0.75rem;
  color: #9ca3af;
}

.hot-post-stats {
  display: flex;
  gap: 0.75rem;
}

.hot-post-stat {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  transition: all 0.2s;
  padding: 0.1rem 0.25rem;
  border-radius: 10px;
  background-color: rgba(148, 163, 184, 0.08);
}

.hot-post-stat:hover {
  background-color: rgba(59, 130, 246, 0.15);
  transform: translateY(-1px);
}

.hot-post-stats i {
  font-size: 0.75rem;
  color: #9ca3af;
  transition: all 0.2s;
}

.hot-post-item:hover .hot-post-stat i {
  color: #3b82f6;
  transform: scale(1.1);
}

.hot-post-username:hover {
  color: #3b82f6;
  transform: translateX(2px);
}

.no-hot-posts {
  text-align: center;
  padding: 0.75rem 0;
  color: #64748b;
  font-size: 0.8125rem;
  background-color: transparent;
  border-radius: 0;
  margin: 0;
}
</style> 