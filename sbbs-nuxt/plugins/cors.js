// 跨域和请求拦截器配置插件 (仅客户端)
export default defineNuxtPlugin((nuxtApp) => {
  // 确保只在客户端执行
  if (process.client) {
    // 配置Nuxt的fetch使用credentials
    const originalFetch = window.fetch;
    window.fetch = function(resource, options = {}) {
      // 检查是否是相对路径的API请求（代理请求）
      const isProxyRequest = typeof resource === 'string' && resource.startsWith('/api/');
      
      // 如果是代理请求，使用不同的配置
      if (isProxyRequest) {
        // 对于代理请求，不设置 credentials，让服务器处理
        options.credentials = options.credentials || 'same-origin';
      } else {
        // 对于直接请求外部API，添加 credentials: 'include'
        options.credentials = options.credentials || 'include';
      }
      
      // 如果没有设置headers，添加默认headers
      if (!options.headers) {
        options.headers = {};
      }
      
      // 如果请求体是对象类型，设置content-type
      if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        options.headers['Content-Type'] = 'application/json';
      }
      
      // 添加token
      const token = localStorage.getItem('token');
      if (token && !options.headers['Authorization']) {
        options.headers['Authorization'] = `Bearer ${token}`;
      }
      
      return originalFetch(resource, options);
    };
  }
}); 