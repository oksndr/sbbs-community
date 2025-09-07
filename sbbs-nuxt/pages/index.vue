<template>
  <LayoutWithSidebar>
    <!-- 搜索结果区域 -->
    <div v-if="isSearchMode" class="content-section">
      <div class="section-header" style="display: flex; justify-content: space-between; align-items: center;">
        <h2>{{ searchType === 'post' ? '帖子' : '用户' }}搜索结果: "{{ searchKeyword }}"</h2>
        <button @click="clearSearch" class="btn btn-outline btn-sm">
          <i class="ri-close-line"></i> 清除搜索
        </button>
      </div>
      
      <div v-if="isSearchLoading" class="loading-spinner">
        <span class="custom-loader"></span> 搜索中...
      </div>

      <!-- 帖子搜索结果 -->
      <div v-if="searchType === 'post' && !isSearchLoading" class="post-list">
        <div v-if="searchResults.length === 0" style="text-align:center; padding: 2rem; color: var(--text-secondary);">
          未找到匹配的帖子
        </div>
        
        <div v-for="post in searchResults" :key="post.id" :id="'post-' + post.id" class="post-item" :class="{ 'highlight-new-post': shouldHighlightPost && shouldHighlightPost(post.title) }" @click="goToPostDetail(post.id)">
          <div class="post-item-avatar-area">
            <img :src="post.avatar" alt="User Avatar" class="post-item-avatar"/>
          </div>
          <div class="post-item-details">
            <div class="post-item-title-line">
              <div class="title-left">
                <a :href="`/post/${post.id}?page=1`" 
                   class="post-item-title">{{ post.title }}</a>
              </div>
              <div class="post-tags">
                <span v-if="isPinnedPost(post, posts.indexOf(post))" class="pinned-badge">
                  <i class="ri-vip-crown-fill"></i>
                  <span class="badge-text">置顶</span>
                  <i class="ri-star-fill badge-star"></i>
                </span>
                <template v-if="post.tags && post.tags.length > 0">
                  <a v-for="(tagName, index) in post.tags.filter(tag => tag !== '置顶')" 
                     :key="index"
                     href="#" 
                     @click.prevent.stop="filterByTagName(tagName)" 
                     class="post-item-tag">
                    {{ tagName }}
                  </a>
                </template>
                <template v-else-if="post.tagIdsStringAlias">
                  <a v-for="tagId in post.tagIdsStringAlias.split(',')" 
                     :key="tagId"
                     href="#" 
                     @click.prevent.stop="filterByTag(tagId.trim())" 
                     class="post-item-tag">
                    {{ getTagName(tagId.trim()) }}
                  </a>
                </template>
              </div>
            </div>
            <div class="post-item-meta">
              <span class="post-item-author" @click.stop="goToUserProfile(post.userId)">
                <i class="ri-user-3-line"></i>{{ post.username }}</span>
              <span class="meta-time">
                <i class="ri-calendar-line"></i>发布 {{ formatTimeAgo(post.created) }}</span>
              <span v-if="post.updated" class="meta-time">
                <i class="ri-time-line"></i>活跃 {{ formatTimeAgo(post.updated) }}</span>
              <div class="post-stats">
                <span class="meta-stats like">
                  <i class="ri-thumb-up-line"></i>{{ post.likeCount || 0 }}</span>
                <span class="meta-stats dislike">
                  <i class="ri-thumb-down-line"></i>{{ post.dislikeCount || 0 }}</span>
                <span class="meta-stats comments">
                  <i class="ri-chat-1-line"></i>{{ post.commentCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 用户搜索结果 -->
      <div v-if="searchType === 'user' && !isSearchLoading">
        <div v-if="searchResults.length === 0" style="text-align:center; padding: 2rem; color: var(--text-secondary);">
          未找到匹配的用户
        </div>
        
        <div v-for="user in searchResults" :key="user.id" style="padding: 1rem; display: flex; align-items: center; border-bottom: 1px solid var(--border-color);">
          <img :src="user.avatar" :alt="user.username" style="width: 50px; height: 50px; border-radius: 8px; margin-right: 1rem; object-fit: cover;"/>
          <div>
            <div style="font-weight: 600; font-size: 1rem; color: #333;">{{ user.username }}</div>
            <div style="margin-top: 0.5rem;">
              <button @click="goToUserProfile(user.id)" class="btn btn-outline btn-sm">
                <i class="ri-user-line"></i> 查看主页
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 搜索结果分页 -->
      <div v-if="!isSearchLoading && searchResults.length > 0" class="pagination-container">
        <div class="pagination-controls">
          <!-- 上一页 -->
          <button 
            @click="goToSearchPage(searchCurrentPage - 1)"
            :disabled="!searchHasPrevPage"
            class="btn btn-outline btn-sm pagination-btn"
            :class="{ 'disabled': !searchHasPrevPage }"
          >
            <i class="ri-arrow-left-line"></i>
          </button>
          
          <!-- 页码按钮 -->
          <template v-for="page in visibleSearchPages" :key="page">
            <!-- 省略号 -->
            <span v-if="page === '...'" class="pagination-ellipsis">...</span>
            <!-- 页码按钮 -->
            <button 
              v-else
              @click="goToSearchPage(page)"
              class="btn btn-sm pagination-btn"
              :class="page === searchCurrentPage ? 'btn-primary' : 'btn-outline'"
            >
              {{ page }}
            </button>
          </template>
          
          <!-- 下一页 -->
          <button 
            @click="goToSearchPage(searchCurrentPage + 1)"
            :disabled="!searchHasNextPage"
            class="btn btn-outline btn-sm pagination-btn"
            :class="{ 'disabled': !searchHasNextPage }"
          >
            <i class="ri-arrow-right-line"></i>
          </button>
        </div>
      </div>
    </div>

    <!-- 帖子列表区域 -->
    <div v-if="!isSearchMode" class="content-section">
      <div class="section-header" style="display: flex; justify-content: space-between; align-items: center;">
        <h2>{{ selectedTagId ? `${getTagName(selectedTagId)} 帖子` : '最新帖子' }}</h2>
        <div style="display: flex; gap: 8px; align-items: center;">
          <button 
            class="btn btn-primary btn-sm" 
            :class="{ 'btn-loading': isNavigatingToPublish }" 
            :disabled="isNavigatingToPublish"
            @click="navigateToPublishWithLoading">
            <template v-if="isNavigatingToPublish">
              <i class="ri-loader-4-line spinning"></i>
              <span>加载中...</span>
            </template>
            <template v-else>
              发布新帖
            </template>
          </button>
        </div>
      </div>
      
      <div v-if="isLoading && posts.length === 0" class="loading-spinner">
        <span class="custom-loader"></span> 加载中...
      </div>

      <div v-if="!isLoading && posts.length === 0 && !isSearchMode" style="text-align:center; padding: 2rem; color: var(--text-secondary);">
        暂无帖子
      </div>

      <div class="post-list" v-if="posts.length > 0">
        <!-- 帖子列表 -->
        <div v-for="post in posts" 
             :key="post.id" 
             :id="'post-' + post.id"
             class="post-item" 
             :class="{ 'highlight-new-post': checkPostHighlight(post.title) }"
             @click="goToPostDetail(post.id)">
          <div class="post-item-avatar-area">
            <img :src="post.avatar" alt="User Avatar" class="post-item-avatar"/>
          </div>
          <div class="post-item-details">
            <div class="post-item-title-line">
              <div class="title-left">
                <a :href="`/post/${post.id}?page=1`" 
                   class="post-item-title">{{ post.title }}</a>
              </div>
              <div class="post-tags">
                <span v-if="isPinnedPost(post, posts.indexOf(post))" class="pinned-badge">
                  <i class="ri-vip-crown-fill"></i>
                  <span class="badge-text">置顶</span>
                  <i class="ri-star-fill badge-star"></i>
                </span>
                <template v-if="post.tags && post.tags.length > 0">
                  <a v-for="(tagName, index) in post.tags.filter(tag => tag !== '置顶')" 
                     :key="index"
                     href="#" 
                     @click.prevent.stop="filterByTagName(tagName)" 
                     class="post-item-tag">
                    {{ tagName }}
                  </a>
                </template>
                <template v-else-if="post.tagIdsStringAlias">
                  <a v-for="tagId in post.tagIdsStringAlias.split(',')" 
                     :key="tagId"
                     href="#" 
                     @click.prevent.stop="filterByTag(tagId.trim())" 
                     class="post-item-tag">
                    {{ getTagName(tagId.trim()) }}
                  </a>
                </template>
              </div>
            </div>
            
            <div class="post-item-meta">
              <span class="post-item-author" @click.stop="goToUserProfile(post.userId)">
                <i class="ri-user-3-line"></i>{{ post.username }}</span>
              <span class="meta-time">
                <i class="ri-calendar-line"></i>发布 {{ formatTimeAgo(post.created) }}</span>
              <span v-if="post.updated" class="meta-time">
                <i class="ri-time-line"></i>活跃 {{ formatTimeAgo(post.updated) }}</span>
              <div class="post-stats">
                <span class="meta-stats like">
                  <i class="ri-thumb-up-line"></i>{{ post.likeCount || 0 }}</span>
                <span class="meta-stats dislike">
                  <i class="ri-thumb-down-line"></i>{{ post.dislikeCount || 0 }}</span>
                <span class="meta-stats comments">
                  <i class="ri-chat-1-line"></i>{{ post.commentCount || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 分页加载状态显示 -->
      <div v-if="isLoadingMore" class="load-more-container">
        <div class="load-more-content">
          <span class="custom-loader"></span> 加载更多中...
        </div>
      </div>
      <div v-if="isLoadMoreDisabled && posts.length > 0 && !isSearchMode" style="text-align: center; padding: 1rem 0; color: #999; font-size: 0.875rem;">
        已经到底啦 ~
      </div>
    </div>
  </LayoutWithSidebar>
</template>

<script setup>
import { ref, onMounted, computed, onUnmounted, inject, watch } from 'vue'
import { useRouter, useRoute, useAsyncData } from '#app'
import { useUserStore } from '~/stores/user'
import { API } from '~/utils/api'
import { useLoginModal } from '~/composables/useLoginModal'
import LayoutWithSidebar from '~/components/LayoutWithSidebar.vue'

const router = useRouter()
// 登录弹窗功能
const { requireLogin, requireLoginForAction } = useLoginModal()
const userStore = useUserStore()
const route = useRoute()

// 帖子数据
const posts = ref([]) 
const isLoading = ref(false) 
const error = ref(null) 
const hasMorePosts = ref(true) 

// 统计信息
const communityStats = ref({
  totalPosts: 0,
  totalUsers: 0,
  newPostsToday: 0,
  newUsersToday: 0
})

// 热门标签
const tags = ref([])

// 数据
// 使用computed响应store状态变化
const isLoggedIn = computed(() => userStore.isLoggedIn);
const userInfo = computed(() => userStore.user);
const selectedTagId = ref(null);
const isLoadingMore = ref(false);
const isLoadMoreDisabled = ref(false);
const limit = ref(15); 
const searchKeyword = ref('');
const searchType = ref('post');
const showSearchTypeDropdown = ref(false);
const isSearchMode = ref(false);
const isSearchLoading = ref(false);
const searchResults = ref([]);
// 搜索分页相关变量
const searchCurrentPage = ref(1);
const searchPageSize = ref(20);
const searchHasNextPage = ref(false);
const searchHasPrevPage = ref(false);
const searchTotal = ref(0);
const searchTotalPages = ref(1);
// 移除已读帖子追踪功能
const lastId = ref(null);
const lastUpdated = ref(null);
// 添加发布按钮加载状态
const isNavigatingToPublish = ref(false);

// 自动加载第二页的定时器
const autoLoadTimer = ref(null);

// 获取API基础URL
const API_BASE_URL = useApiBaseUrl()

// 启动自动加载定时器的函数
const startAutoLoadTimer = () => {
  // 清除之前的定时器（如果有）
  if (autoLoadTimer.value) {
    clearTimeout(autoLoadTimer.value);
    autoLoadTimer.value = null;
  }
  
  // 只在主首页（非搜索、非标签筛选）且有更多内容时启用
  if (!isSearchMode.value && !selectedTagId.value && hasMorePosts.value) {
    autoLoadTimer.value = setTimeout(() => {
      // 再次确认条件（防止用户在3秒内切换到搜索或标签筛选）
      if (!isSearchMode.value && !selectedTagId.value && hasMorePosts.value && !isLoadingMore.value) {
        console.log('🚀 自动加载第二页（首页显示3秒后）');
        loadMorePosts();
      }
      autoLoadTimer.value = null;
    }, 3000); // 3秒后自动加载
  }
};

// SSR数据获取 - 获取首页帖子列表
// 在全局作用域定义cookie引用
const authCookie = useCookie('Authorization', { default: () => null })
const tokenCookie = useCookie('token', { default: () => null })

// 根据用户登录状态决定缓存策略
const getCacheKey = () => {
  const isLoggedIn = process.server ? 
    (!!authCookie.value || !!tokenCookie.value) : 
    userStore.isLoggedIn
    
  const tagId = route.query.tagId
  
  // 为不同状态的用户使用不同的缓存key
  if (isLoggedIn) {
    return `homepage-posts-auth-${userStore.user?.id || 'unknown'}-${tagId || 'all'}`
  } else {
    return `homepage-posts-anon-${tagId || 'all'}`
  }
}

const { pending: postsDataPending, error: postsDataError, data: postsData } = await useAsyncData('homepage-posts', async () => {
  try {
    console.log('获取首页帖子，环境:', process.client ? '客户端' : '服务端')
    
    // 检查用户登录状态  
    const isLoggedIn = process.server ? 
      (!!authCookie.value || !!tokenCookie.value) : 
      userStore.isLoggedIn
    
    console.log('用户登录状态:', isLoggedIn ? '已登录' : '未登录')
    
    // 检查是否有标签筛选参数
    const tagId = route.query.tagId
    let url, params
    
    if (tagId) {
      // 获取特定标签的帖子
      url = `${API_BASE_URL}/v2/list/tag/${tagId}`
      params = new URLSearchParams({
        pageSize: '15'
      })
    } else {
      // 获取所有帖子
      url = `${API_BASE_URL}/v2/list`
      params = new URLSearchParams({
        pageSize: '16'
      })
    }
    
    // 为已登录用户添加认证头（如果有的话）
    const headers = {
      'Content-Type': 'application/json'
    }
    
    if (isLoggedIn && process.server) {
      if (authCookie.value) {
        headers['Authorization'] = authCookie.value
      }
    } else if (isLoggedIn && process.client && userStore.token) {
      headers['Authorization'] = `Bearer ${userStore.token}`
    }
    
    const response = await fetch(`${url}?${params}`, { headers })
    const data = await response.json()
    
    console.log('首页帖子API响应:', { 
      code: data.code, 
      postsCount: data.data?.list?.length || 0, 
      tagId,
      userStatus: isLoggedIn ? '已登录' : '未登录'
    })
    
    if (data.code === 200 && data.data && data.data.list) {
      const expectedPageSize = tagId ? 15 : 16
      return {
        posts: data.data.list,
        lastId: data.data.lastId,
        lastUpdated: data.data.lastUpdated,
        hasMore: data.data.list.length >= expectedPageSize,
        tagId: tagId || null,
        isStaticContent: !isLoggedIn // 标记是否为静态内容
      }
    }
    
    return { 
      posts: [], 
      lastId: null, 
      lastUpdated: null, 
      hasMore: false, 
      tagId: tagId || null,
      isStaticContent: !isLoggedIn
    }
  } catch (error) {
    console.error('获取首页帖子失败:', error)
    return { 
      posts: [], 
      lastId: null, 
      lastUpdated: null, 
      hasMore: false, 
      tagId: null,
      isStaticContent: false
    }
  }
})

// 同步SSR数据到组件状态
watch(() => postsData.value, (newData) => {
  if (newData) {
    posts.value = newData.posts || []
    lastId.value = newData.lastId
    lastUpdated.value = newData.lastUpdated
    hasMorePosts.value = newData.hasMore
    isLoadMoreDisabled.value = !newData.hasMore
    selectedTagId.value = newData.tagId
    isLoading.value = false
    
    // 当首页帖子数据加载完成且显示时，启动自动加载定时器
    if (process.client && newData.posts && newData.posts.length > 0) {
      startAutoLoadTimer();
    }
  }
}, { immediate: true })

// 监听加载状态
watch(() => postsDataPending.value, (isPending) => {
  isLoading.value = isPending
})

// 注入高亮检测函数
const shouldHighlightPost = inject('shouldHighlightPost', null)
// 也尝试注入PostHighlighter中提供的高亮检查函数
const highlightCheck = inject('highlightCheck', null)

// 创建一个兼容的高亮检查函数
const checkPostHighlight = (title) => {
  // 优先使用PostHighlighter中的highlightCheck函数
  if (highlightCheck) {
    return highlightCheck(title);
  }
  
  // 然后尝试使用LayoutWithSidebar中的shouldHighlightPost函数
  if (shouldHighlightPost) {
    return shouldHighlightPost(title);
  }
  
  // 最后，检查全局变量（由PostHighlighter设置）
  if (process.client && window.SBBS_SHOULD_HIGHLIGHT_POST) {
    return window.SBBS_LATEST_POST_TITLE === title;
  }
  
  return false;
};

// 检测是否为置顶帖
const isPinnedPost = (post, index) => {
  // 在搜索模式和标签筛选模式下不显示置顶特效
  if (isSearchMode.value || selectedTagId.value) {
    return false;
  }
  
  // 根据帖子的tags数组中是否包含"置顶"标签来判断
  if (!post.tags || !Array.isArray(post.tags)) {
    return false;
  }
  
  return post.tags.includes('置顶');
};

// 计算当前应该使用的页面大小
const currentPageSize = computed(() => {
  return selectedTagId.value ? 15 : 16;
});

// 搜索分页计算属性（移除，现在直接使用API返回的totalPages）

// 可见的搜索页码 - 显示所有页码按钮
const visibleSearchPages = computed(() => {
  const total = searchTotalPages.value;
  const pages = [];
  
  // 如果总页数较少（<=10页），显示所有页码
  if (total <= 10) {
    for (let i = 1; i <= total; i++) {
      pages.push(i);
    }
  } else {
    // 如果页数较多，显示智能分页
    const current = searchCurrentPage.value;
    
    // 始终显示第1页
    pages.push(1);
    
    // 计算中间显示的页码范围
    let start = Math.max(2, current - 2);
    let end = Math.min(total - 1, current + 2);
    
    // 如果开始页码不是2，添加省略号标记
    if (start > 2) {
      pages.push('...');
    }
    
    // 添加中间页码
    for (let i = start; i <= end; i++) {
      if (i !== 1 && i !== total) {
        pages.push(i);
      }
    }
    
    // 如果结束页码不是倒数第二页，添加省略号标记
    if (end < total - 1) {
      pages.push('...');
    }
    
    // 始终显示最后一页（如果总页数大于1）
    if (total > 1) {
      pages.push(total);
    }
  }
  
  return pages;
});

// 方法
const fetchTags = async () => {
  try {
    const response = await API.tags.getAllTags();
    if (response.code === 200) {
      tags.value = response.data || [];
    }
  } catch (error) {
    console.error('获取标签失败', error);
  }
}

// 获取帖子列表
const fetchPosts = async (loadMore = false) => {
  if (isLoading.value || (loadMore && isLoadingMore.value)) return;
  


  if (loadMore) {
    isLoadingMore.value = true;
  } else {
    isLoading.value = true;
    posts.value = [] 
    lastId.value = null
    lastUpdated.value = null
    isLoadMoreDisabled.value = false // 重置
  }

  try {
    let response;
    
    // 根据是否有标签筛选选择不同的API端点
    if (selectedTagId.value !== null) {
      // 使用标签筛选的API端点
      const url = `${API_BASE_URL}/v2/list/tag/${selectedTagId.value}`;
      const params = new URLSearchParams({
        pageSize: String(currentPageSize.value)
      });
      
      // 如果是加载更多，添加游标参数
      if (loadMore && lastId.value) {
        params.append('lastId', String(lastId.value));
      }
      
      if (loadMore && lastUpdated.value) {
        params.append('lastUpdated', lastUpdated.value);
      }
      
      const fullUrl = `${url}?${params}`;
      
      // 添加token认证头
      const headers = {
        'Content-Type': 'application/json'
      };
      
      if (userStore.isLoggedIn && userStore.token) {
        headers['Authorization'] = `Bearer ${userStore.token}`;
      }
      
      const fetchResponse = await fetch(fullUrl, { headers });
      response = await fetchResponse.json();
    } else {
      // 使用原有的API调用方式（获取所有帖子）
      const params = {
        pageSize: currentPageSize.value
      };
      
      if (loadMore && lastId.value) {
        params.lastId = lastId.value;
      }
      
      if (loadMore && lastUpdated.value) {
        params.lastUpdated = lastUpdated.value;
      }
      
      response = await API.posts.getList(params);
    }

    if (response.code === 200 && response.data && response.data.list) {
      const newPosts = response.data.list;
      
      if (loadMore) {
        posts.value = [...posts.value, ...newPosts];
      } else {
        posts.value = newPosts;
      }
      
      if (newPosts.length > 0) {
        lastId.value = response.data.lastId;
        lastUpdated.value = response.data.lastUpdated;
      }
      
      isLoadMoreDisabled.value = newPosts.length < currentPageSize.value;
      hasMorePosts.value = !isLoadMoreDisabled.value
    } else {
      console.error('获取帖子列表失败:', response.msg);
      error.value = response.msg || '获取帖子列表失败';
    }
  } catch (err) {
    console.error('获取帖子列表异常:', err);
    error.value = err.message || '获取帖子列表异常';
  } finally {
    isLoading.value = false;
    isLoadingMore.value = false;
    
    // 如果是首次加载（非loadMore）且在客户端，启动自动加载定时器
    if (!loadMore && process.client && posts.value.length > 0) {
      startAutoLoadTimer();
    }
  }
};

// 加载更多帖子
const loadMorePosts = () => {
  if (isLoadingMore.value || isLoadMoreDisabled.value || !hasMorePosts.value) return;
  fetchPosts(true);
};

// 获取社区统计信息
const fetchCommunityStats = async () => {
  try {
    const response = await API.stats.getOverview();
    if (response.code === 200) {
      communityStats.value = response.data || {};
    }
  } catch (error) {
    console.error('获取社区统计失败', error);
  }
}

// 按标签筛选
const filterByTag = (tagId) => {
  selectedTagId.value = tagId;
  isSearchMode.value = false; 
  
  // 更新URL查询参数
  const newQuery = tagId ? { tagId } : {}
  router.replace({ path: '/', query: newQuery })
  
  // 触发全局事件通知header和侧边栏更新状态
  if (process.client) {
    window.dispatchEvent(new CustomEvent('tag-filter-changed', {
      detail: { tagId }
    }))
  }
  
  fetchPosts(); 
};

// 按标签名筛选 (如果需要)
const filterByTagName = (tagName) => {
  const tag = tags.value.find(t => t.name === tagName);
  if (tag) {
    filterByTag(tag.id);
  }
};

// 获取标签名称
const getTagName = (tagId) => {
  if (tagId === null || tagId === undefined) return '';
  const tag = tags.value.find(t => String(t.id) === String(tagId));
  return tag ? tag.name : '';
};

// 获取标签图标
const getTagIcon = (tagId) => {
  switch (String(tagId)) {
    case '1': return 'ri-lightbulb-line'; 
    case '2': return 'ri-question-answer-line'; 
    default: return 'ri-hashtag'; 
  }
};

// 搜索内容
const searchContent = async (page = 1) => {
  if (!searchKeyword.value.trim()) return;
  isSearchMode.value = true;
  isSearchLoading.value = true;
  searchResults.value = [];
  searchCurrentPage.value = page;
  
  try {
    const params = new URLSearchParams({
      type: searchType.value,
      keyword: searchKeyword.value.trim(),
      page: page.toString(),
      pageSize: searchPageSize.value.toString()
    });
    
    // 发送请求
    const headers = {
      'Content-Type': 'application/json'
    };
    
    if (userStore.token) {
      headers['Authorization'] = `Bearer ${userStore.token}`;
    }
    
    const response = await fetch(`${API_BASE_URL}/search?${params}`, {
      headers
    });
    
    const data = await response.json();
    console.log('搜索API响应:', data); // 调试日志
    
    if (data.code === 200) {
      // API返回的数据结构包含完整分页信息
      if (data.data && data.data.list) {
        searchResults.value = data.data.list;
        searchHasNextPage.value = data.data.hasNextPage || false;
        searchHasPrevPage.value = data.data.hasPrevPage || false;
        searchTotal.value = data.data.total || 0;
        searchTotalPages.value = data.data.totalPages || 1;
        searchCurrentPage.value = data.data.page || page;
        searchPageSize.value = data.data.pageSize || 20;
        console.log('搜索分页信息:', { 
          total: searchTotal.value, 
          currentPage: searchCurrentPage.value, 
          totalPages: searchTotalPages.value,
          hasNext: searchHasNextPage.value,
          hasPrev: searchHasPrevPage.value
        });
      } else {
        // 兼容旧版API直接返回数组的情况
        searchResults.value = data.data || [];
        searchHasNextPage.value = false;
        searchHasPrevPage.value = false;
        searchTotal.value = searchResults.value.length;
        searchTotalPages.value = 1;
      }
    } else {
      console.error('搜索失败', data.msg);
      searchResults.value = [];
      searchHasNextPage.value = false;
      searchHasPrevPage.value = false;
      searchTotal.value = 0;
      searchTotalPages.value = 1;
    }
  } catch (error) {
    console.error('搜索请求出错', error);
    searchResults.value = [];
    searchHasNextPage.value = false;
    searchHasPrevPage.value = false;
    searchTotal.value = 0;
    searchTotalPages.value = 1;
  } finally {
    isSearchLoading.value = false;
  }
};

// 搜索分页跳转
const goToSearchPage = (page) => {
  if (page < 1 || page > searchTotalPages.value) return;
  searchContent(page);
  // 滚动到页面顶部
  if (process.client) {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
};

// 移除快速跳转功能

// 清除搜索结果
const clearSearch = () => {
  isSearchMode.value = false;
  searchKeyword.value = '';
  searchResults.value = [];
  searchCurrentPage.value = 1; // 重置搜索页码
  searchHasNextPage.value = false;
  searchHasPrevPage.value = false;
  searchTotal.value = 0;
  searchTotalPages.value = 1;
  selectedTagId.value = null; 
  fetchPosts(); 
};

const toggleSearchTypeDropdown = () => {
  showSearchTypeDropdown.value = !showSearchTypeDropdown.value;
};

const selectSearchType = (type) => {
  searchType.value = type;
  showSearchTypeDropdown.value = false;
  // 切换搜索类型时重置分页
  searchCurrentPage.value = 1;
  searchHasNextPage.value = false;
  searchHasPrevPage.value = false;
  searchTotal.value = 0;
  searchTotalPages.value = 1;
  // 如果当前在搜索模式且有关键词，重新搜索
  if (isSearchMode.value && searchKeyword.value.trim()) {
    searchContent(1);
  }
};

// 移除checkLogin函数 - 使用store的响应式状态

// 退出登录
const logout = () => {
  userStore.logout()
  // 移除手动状态更新 - computed会自动响应
  router.push('/'); // 退出后返回首页，刷新状态
}

// 获取用户头像
const getUserAvatar = (userId) => {
  return (userInfo.value && userInfo.value.avatar) 
         ? userInfo.value.avatar 
         : `https://i.pravatar.cc/150?u=${userId || 'default'}`;
};



// 处理滚动事件，实现无限加载
const handleScroll = () => {
  if (isSearchMode.value || isLoading.value || isLoadingMore.value || isLoadMoreDisabled.value || !hasMorePosts.value) return;
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
  const windowHeight = window.innerHeight;
  const documentHeight = document.documentElement.scrollHeight; // 使用scrollHeight更可靠
  if (scrollTop + windowHeight >= documentHeight - 200) {
    loadMorePosts();
  }
}

// 跳转到帖子详情页
const goToPostDetail = (postId) => {
  if (process.client) {
    window.location.href = `/post/${postId}?page=1`
  }
}

// 跳转到用户主页 - 使用刷新式加载
const goToUserProfile = (userId) => {
  if (userId) { // 确保userId有效
    window.location.href = `/user/${userId}`
  }
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

// 移除已读帖子相关函数

// 格式化时间为"多久之前"
const formatTimeAgo = (timestamp) => {
  if (!timestamp) return '';
  const now = Date.now();
  const past = new Date(timestamp).getTime();
  const diffInSeconds = Math.floor((now - past) / 1000);
  if (diffInSeconds < 60) return `${diffInSeconds} 秒前`;
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} 分钟前`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} 小时前`;
    return `${Math.floor(diffInSeconds / 86400)} 天前`;
};

// 动态页面标题
const pageTitle = computed(() => {
  if (selectedTagId.value && tags.value.length > 0) {
    const currentTag = tags.value.find(tag => String(tag.id) === String(selectedTagId.value))
    if (currentTag) {
      return `${currentTag.name} - SBBS社区`
    }
  }
  return 'SBBS社区 - 首页'
})

// 更新页面标题
watch(pageTitle, (newTitle) => {
  if (process.client) {
    document.title = newTitle
  }
}, { immediate: true })

// 通知的自动获取现在由全局layout (default.vue) 统一处理，无需在首页重复加载

// 生命周期钩子
onMounted(() => {
  if (process.client) {
    // 移除已读帖子的localStorage读取
  window.addEventListener('scroll', handleScroll);
    
    // 监听标签筛选变化事件
    window.addEventListener('tag-filter-changed', (event) => {
      selectedTagId.value = event.detail.tagId;
      // 清除搜索模式，切换到正常的帖子列表模式
      isSearchMode.value = false;
      // 重新获取帖子
      fetchPosts();
      // 清除自动加载定时器（因为切换了筛选）
      if (autoLoadTimer.value) {
        clearTimeout(autoLoadTimer.value);
        autoLoadTimer.value = null;
      }
    });
    
    // 监听搜索事件
    window.addEventListener('search-content', (event) => {
      searchKeyword.value = event.detail.keyword;
      searchType.value = event.detail.type;
      searchContent();
      // 清除自动加载定时器（因为进入搜索模式）
      if (autoLoadTimer.value) {
        clearTimeout(autoLoadTimer.value);
        autoLoadTimer.value = null;
      }
    });
    
    // 检查URL参数是否有标签ID
    const urlParams = new URLSearchParams(window.location.search);
    const tagId = urlParams.get('tagId');
    if (tagId) {
      selectedTagId.value = tagId;
    }
    
    // 检查URL参数是否有搜索关键词
    const searchParam = urlParams.get('search');
    const typeParam = urlParams.get('type');
    if (searchParam) {
      searchKeyword.value = searchParam;
      if (typeParam) {
        searchType.value = typeParam;
      }
      searchContent();
    }

  }
  // 移除checkLogin调用 - store会自动初始化
  fetchTags();
  
  // 恢复到简单可靠的逻辑：只有在没有SSR数据或者有特殊筛选条件时才重新获取帖子
  if (posts.value.length === 0 || selectedTagId.value) {
    fetchPosts();
  }
  
  if (isLoggedIn.value) {
    fetchCommunityStats();
    // 通知获取由全局layout处理，无需在此重复调用
  }
});

onUnmounted(() => {
  if (process.client) {
  window.removeEventListener('scroll', handleScroll);
    window.removeEventListener('tag-filter-changed', () => {});
    window.removeEventListener('search-content', () => {});
    
    // 清除自动加载定时器
    if (autoLoadTimer.value) {
      clearTimeout(autoLoadTimer.value);
      autoLoadTimer.value = null;
    }
  }
});

definePageMeta({
  layout: 'default'
})

useHead({
  title: 'SBBS社区 - 首页',
  meta: [
    { name: 'description', content: 'SBBS社区 - 一个现代化的社区论坛' }
  ]
})
</script>

<style scoped>
.content-section {
  background-color: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.05), 0 0 0 1px rgba(0,0,0,0.03);
  margin-bottom: 1.5rem;
  overflow: hidden;
}

.section-header {
  padding: 1rem;
  border-bottom: 1px solid rgba(226,232,240,0.8);
}

.section-header h2 {
  font-size: 1.125rem;
  font-weight: 600;
  margin: 0;
  color: #111;
}

.post-list {
  display: flex;
  flex-direction: column;
}

.post-item {
  display: flex;
  padding: 0.65rem 0.85rem; /* 进一步减小内边距 */
  border-bottom: 1px solid var(--border-color);
  transition: all 0.2s ease;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.post-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  width: 3px;
  background: linear-gradient(to bottom, #3b82f6, #6366f1);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.post-item:hover {
  background-color: #f8fafc;
}

.post-item:hover::before {
  opacity: 1;
}

.post-item-avatar-area {
  margin-right: 0.75rem; /* 减小右边距 */
  flex-shrink: 0;
}

.post-item-avatar {
  width: 2.5rem; /* 进一步缩小头像 */
  height: 2.5rem;
  border-radius: 6px;
  object-fit: cover;
  transition: transform 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.post-item-details {
  flex: 1;
  min-width: 0;
}

.post-item-title-line {
  display: flex;
  align-items: center; 
  justify-content: space-between; /* 将标题和标签分开排列 */
  margin-bottom: 0.35rem;
  width: 100%;
}

.post-item-title {
  font-weight: 600;
  color: #1a202c;
  text-decoration: none;
  transition: color 0.15s;
  flex: 1; /* 占据可用空间 */
  font-size: 0.9rem;
  line-height: 1.35;
  margin-bottom: 0.1rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 1rem; /* 为标签留出空间 */
}

.post-item-title:hover {
  color: var(--primary-color);
}

.post-item-tag {
  font-size: 0.65rem;
  padding: 0.05rem 0.3rem;
  border-radius: 3px;
  background-color: rgba(79, 70, 229, 0.08);
  color: #4f46e5; 
  text-decoration: none;
  flex-shrink: 0;
  font-weight: 500;
  line-height: 1.5;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  white-space: nowrap; /* 防止标签换行 */
}

.post-item-tag:hover {
  background-color: rgba(79, 70, 229, 0.12);
  border-color: rgba(79, 70, 229, 0.2);
  transform: translateY(-1px);
}

.post-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem; 
  font-size: 0.65rem; /* 进一步缩小元数据字号 */
  color: #64748b;
  margin-top: 0.1rem; /* 减小上方间距 */
}

.post-item-author {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  color: #64748b;
  font-size: 0.65rem;
}

.post-item-author:hover {
  color: var(--primary-color);
}

.post-item-author i {
  font-size: 0.75rem;
  margin-right: 0.25rem;
  opacity: 0.7;
}

.meta-time {
  display: inline-flex;
  align-items: center;
  color: #64748b;
  font-size: 0.65rem;
}

.meta-time i {
  font-size: 0.75rem;
  margin-right: 0.25rem;
  opacity: 0.7;
}

.loading-spinner {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 3rem 1rem; 
  color: var(--text-secondary);
}

.loading-spinner i {
  font-size: 2rem;
  margin-right: 0.5rem;
}

footer {
  display: none; /* 隐藏重复的footer */
}

@media (max-width: 992px) {
  .grid-layout {
    grid-template-columns: 1fr;
  }
}

/* 个人主页和退出登录按钮样式 */
.profile-btn, .logout-btn {
    flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem;
  border-radius: 6px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  text-decoration: none;
  color: var(--text-color);
  border: 1px solid transparent;
  background-color: transparent;
}

.profile-btn:hover, .logout-btn:hover {
  background-color: #f0f7ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
}

.profile-btn:hover {
  color: #3b82f6;
  border-color: rgba(59, 130, 246, 0.2);
}

.logout-btn:hover {
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.2);
}

.profile-btn i, .logout-btn i {
  margin-right: 0.375rem;
  transition: transform 0.2s ease;
}

.profile-btn:hover i {
  transform: translateX(-2px);
  color: #3b82f6;
}

.logout-btn:hover i {
  transform: rotate(90deg);
  color: #ef4444;
}

/* 用户头像样式 */
.user-profile-avatar {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  margin-right: 0.75rem;
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  transition: all 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  }
  
.user-profile-avatar:hover {
  transform: scale(1.05) rotate(2deg);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
} 

/* 自定义加载器 */
.custom-loader {
  width: 18px;
  height: 18px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  display: inline-block;
  margin-right: 8px;
  vertical-align: middle;
}

/* 旋转动画 */
.custom-loader {
  animation-name: spinLoader;
  animation-duration: 1s;
  animation-iteration-count: infinite;
  animation-timing-function: linear;
}

@keyframes spinLoader {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 添加加载更多的样式 */
.load-more-container {
  display: flex;
  justify-content: center;
  padding: 1rem 0;
}

.load-more-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #999;
  font-size: 0.875rem;
}

/* 新增顶部操作栏样式，类似NodeSeek */
.content-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.65rem 0.85rem;
  border-bottom: 1px solid var(--border-color);
  background-color: #f9fafb;
}

.action-tabs {
  display: flex;
  gap: 0.75rem;
}

.action-tab {
  font-size: 0.8rem;
  color: #64748b;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-tab.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.08);
  font-weight: 500;
}

.action-tab:hover:not(.active) {
  background-color: #f1f5f9;
}

/* 移除unread-badge样式 */

/* 置顶帖徽章样式 - 精美重制版 */
.pinned-badge {
  display: inline-flex;
  align-items: center;
  padding: 0.05rem 0.3rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 0.65rem;
  font-weight: 700;
  border-radius: 3px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-right: 0.35rem;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  box-shadow: 
    0 2px 8px rgba(102, 126, 234, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.2),
    0 0 0 1px rgba(102, 126, 234, 0.15);
  animation: pinned-breath 3s ease-in-out infinite alternate;
  backdrop-filter: blur(10px);
  line-height: 1.5;
  vertical-align: baseline;
  align-self: center;
}

.pinned-badge::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  animation: shine 2s ease-in-out infinite;
}

.pinned-badge .ri-vip-crown-fill {
  font-size: 0.6rem;
  margin-right: 0.15rem;
  color: #ffd700;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.2));
  animation: crown-bounce 2s ease-in-out infinite;
}

.pinned-badge .badge-text {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  font-weight: 700;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.2);
  position: relative;
  z-index: 2;
}

.pinned-badge .badge-star {
  font-size: 0.45rem;
  margin-left: 0.15rem;
  color: #ffd700;
  animation: star-twinkle 1.5s ease-in-out infinite alternate;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.2));
}

@keyframes pinned-breath {
  0% { 
    box-shadow: 
      0 2px 8px rgba(102, 126, 234, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.2),
      0 0 0 1px rgba(102, 126, 234, 0.15);
    transform: translateY(0);
  }
  100% { 
    box-shadow: 
      0 3px 12px rgba(102, 126, 234, 0.4),
      inset 0 1px 0 rgba(255, 255, 255, 0.3),
      0 0 0 1px rgba(102, 126, 234, 0.25),
      0 0 8px rgba(118, 75, 162, 0.2);
    transform: translateY(-0.5px);
  }
}

@keyframes shine {
  0% { left: -100%; }
  100% { left: 100%; }
}

@keyframes crown-bounce {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-1px) scale(1.05); }
}

@keyframes star-twinkle {
  0% { 
    opacity: 0.7; 
    transform: scale(1) rotate(0deg); 
  }
  100% { 
    opacity: 1; 
    transform: scale(1.1) rotate(15deg); 
  }
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-bottom: 0.35rem;
  justify-content: flex-end; /* 标签居右对齐 */
  flex-shrink: 0; /* 防止标签被挤压 */
}

.post-stats {
  display: flex;
  gap: 0.5rem;
  margin-left: auto;
}

.meta-stats {
  display: inline-flex;
  align-items: center;
  font-size: 0.65rem;
  color: #64748b;
}

.meta-stats i {
  font-size: 0.75rem;
  margin-right: 0.25rem;
  opacity: 0.8;
}

.meta-stats.like i {
  color: #05c895;
}

.meta-stats.dislike i {
  color: #e74c3c;
}

.meta-stats.comments i {
  color: #3b82f6;
}

.title-left {
  display: flex;
  align-items: center;
  min-width: 0; /* 允许内容压缩 */
  flex: 1;
}

/* 高亮新发布的帖子 */
.highlight-new-post {
  background-color: rgba(59, 130, 246, 0.12) !important;
  animation: highlight-pulse 1s infinite;
  border-left: 4px solid #3b82f6 !important;
  box-shadow: 0 2px 15px rgba(59, 130, 246, 0.25);
  transform: translateY(-2px);
  z-index: 1;
  position: relative;
}

@keyframes highlight-pulse {
  0% { background-color: rgba(59, 130, 246, 0.12); }
  50% { background-color: rgba(59, 130, 246, 0.2); }
  100% { background-color: rgba(59, 130, 246, 0.12); }
}

/* 按钮样式 */
.btn {
  display: inline-flex;
  align-items: center;
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
  margin-right: 0.375rem;
  font-size: 0.9375rem;
}

.btn-primary {
  background-color: #3b82f6;
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
  border: 1px solid #e5e7eb;
  color: #333;
}

.btn-outline:hover {
  background-color: #f9fafb;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0,0,0,0.05);
}

.btn-sm {
  padding: 0.375rem 0.625rem;
  font-size: 0.8125rem;
}

/* 分页样式 */
.pagination-container {
  margin-top: 2rem;
  padding: 1rem 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.pagination-controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.25rem;
  flex-wrap: wrap;
}

.pagination-btn {
  min-width: 40px;
  justify-content: center;
  padding: 0.375rem 0.5rem;
}

.pagination-btn:disabled,
.pagination-btn.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

.pagination-btn:disabled:hover,
.pagination-btn.disabled:hover {
  transform: none;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  background-color: transparent;
}

.pagination-ellipsis {
  padding: 0.375rem 0.5rem;
  color: var(--text-secondary);
  font-size: 0.875rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .pagination-controls {
    gap: 0.125rem;
  }
  
  .pagination-btn {
    min-width: 36px;
    padding: 0.25rem 0.375rem;
    font-size: 0.75rem;
  }
}

/* 发布按钮样式优化 */
.section-header .btn {
  white-space: nowrap;
  min-width: 70px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  position: relative;
  padding: 0;
  font-size: 0.8125rem;
  line-height: 1;
  vertical-align: middle;
}

.section-header .btn span {
  line-height: 1;
  margin: 0;
  vertical-align: middle;
  display: inline-block;
}

.section-header .btn i {
  line-height: 1;
  margin: 0;
  vertical-align: middle;
  display: inline-block;
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

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style> 