// API 请求工具函数
export const useApi = () => {
  const config = useRuntimeConfig()
  
  // 获取正确的 API 基础 URL - 统一使用代理路径
  const getApiBaseUrl = () => {
    // 所有环境都统一使用代理路径，由Nitro处理路由
    return '/api';
  }
  
  const apiBaseUrl = getApiBaseUrl()
  
  // 通用请求函数
  const request = async <T>(
    endpoint: string, 
    options: RequestInit = {}
  ): Promise<T> => {
    const url = `${apiBaseUrl}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`
    
    const defaultOptions: RequestInit = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers
      },
      ...options
    }
    
    try {
      const response = await fetch(url, defaultOptions)
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      return await response.json()
    } catch (error) {
      console.error('API request failed:', error)
      throw error
    }
  }
  
  // 便捷方法
  const get = <T>(endpoint: string, options?: RequestInit) => 
    request<T>(endpoint, { ...options, method: 'GET' })
    
  const post = <T>(endpoint: string, data?: any, options?: RequestInit) => 
    request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined
    })
    
  const put = <T>(endpoint: string, data?: any, options?: RequestInit) => 
    request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined
    })
    
  const del = <T>(endpoint: string, options?: RequestInit) => 
    request<T>(endpoint, { ...options, method: 'DELETE' })
  
  return {
    request,
    get,
    post,
    put,
    delete: del,
    apiBaseUrl
  }
} 