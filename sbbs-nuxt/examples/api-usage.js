// API 使用示例文件
// 展示如何在不同场景下使用新的 API 配置

/**
 * 方法1: 使用我们创建的 useApi composable（推荐）
 */
export async function useApiExample() {
  const { get, post } = useApi()
  
  try {
    // 获取帖子列表 - 自动处理环境差异
    const posts = await get('/v2/list?pageSize=15')
    
    // 创建新帖子
    const newPost = await post('/v1/posts', {
      title: '新帖子标题',
      content: '帖子内容'
    })
    
    return { posts, newPost }
  } catch (error) {
    console.error('API 请求失败:', error)
    throw error
  }
}

/**
 * 方法2: 使用传统的 API 工具函数
 */
export async function traditionalApiExample() {
  try {
    // 使用 utils/api.js 中的方法
    const posts = await API.posts.getList({ pageSize: 15 })
    const userProfile = await API.user.getProfile(123)
    
    return { posts, userProfile }
  } catch (error) {
    console.error('传统API请求失败:', error)
    throw error
  }
}

/**
 * 方法3: 在组件中使用 (Vue composition API)
 */
export function usePostsData() {
  const posts = ref([])
  const loading = ref(false)
  const error = ref(null)
  
  const fetchPosts = async () => {
    loading.value = true
    error.value = null
    
    try {
      const { get } = useApi()
      const response = await get('/v2/list?pageSize=15')
      
      if (response.code === 200) {
        posts.value = response.data.posts || []
      } else {
        throw new Error(response.msg || '获取数据失败')
      }
    } catch (err) {
      error.value = err.message
      console.error('获取帖子失败:', err)
    } finally {
      loading.value = false
    }
  }
  
  return {
    posts,
    loading,
    error,
    fetchPosts
  }
}

/**
 * 环境检测示例
 */
export function getApiInfo() {
  const config = useRuntimeConfig()
  
  return {
    isServer: process.server,
    isClient: process.client,
    serverApiUrl: process.server ? config.apiBaseUrl : 'N/A',
    clientApiUrl: process.client ? config.public.apiBase : 'N/A',
    currentApiUrl: process.server ? config.apiBaseUrl : config.public.apiBase
  }
} 