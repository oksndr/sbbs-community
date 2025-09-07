/**
 * 统一的 API 基础 URL 获取函数
 * 修复SSR问题：根据环境使用不同的后端地址
 */
export const useApiBaseUrl = () => {
  // 在服务端环境：根据开发/生产环境使用不同地址
  if (process.server || typeof window === 'undefined') {
    const config = useRuntimeConfig()
    return config.apiBaseUrl;
  } else {
    // 在客户端环境：使用代理路径
    return '/api';
  }
}

/**
 * 获取完整的 API URL
 * @param endpoint - API 端点路径
 */
export const useApiUrl = (endpoint: string) => {
  const baseUrl = useApiBaseUrl()
  const cleanEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  return `${baseUrl}${cleanEndpoint}`
} 