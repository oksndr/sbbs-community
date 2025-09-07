export default defineEventHandler((event) => {
  const { req, res } = event.node
  const userAgent = getHeader(event, 'user-agent') || ''
  const cookies = parseCookies(event)
  
  // 检查是否有认证cookie
  const hasAuthCookie = !!cookies.Authorization || !!cookies.token
  
  // 检查是否是API请求
  const url = getURL(event)
  const isApiRequest = url.includes('/api/')
  
  // 检查是否是验证码相关请求
  const isCaptchaRequest = url.includes('/captcha') || 
                          url.includes('/verify') || 
                          url.includes('/auth/login') ||
                          url.includes('/auth/register')
  
  // 检查是否是动态内容
  const isDynamicContent = url.includes('/post/publish') || 
                          url.includes('/post/edit') ||
                          url.includes('/user/') ||
                          isCaptchaRequest
  
  // 检查是否是可缓存的页面（首页和帖子详情）
  const isCacheablePage = url === '/' || 
                         url.startsWith('/post/') && 
                         !url.includes('/edit') && 
                         !url.includes('/publish')
  
  // 设置缓存策略
  if (hasAuthCookie || isApiRequest || isDynamicContent) {
    // 已登录用户、API请求、动态内容 - 不缓存
    setHeader(event, 'Cache-Control', 'no-cache, no-store, must-revalidate')
    setHeader(event, 'CF-Cache-Status', 'DYNAMIC')
    setHeader(event, 'Vary', 'Cookie, Authorization')
  } else if (isCacheablePage) {
    // 未登录用户的静态页面 - 缓存5分钟
    setHeader(event, 'Cache-Control', 'public, max-age=300, s-maxage=300')
    setHeader(event, 'CF-Cache-Status', 'CACHEABLE')
    setHeader(event, 'Edge-Cache-Tag', 'anonymous-content')
    setHeader(event, 'Vary', 'Cookie, Authorization')
    // 添加Last-Modified头以支持懒更新
    setHeader(event, 'Last-Modified', new Date().toUTCString())
  } else {
    // 其他静态资源 - 较短缓存
    setHeader(event, 'Cache-Control', 'public, max-age=60, s-maxage=60')
    setHeader(event, 'CF-Cache-Status', 'CACHEABLE')
  }
  
  return {
    hasAuth: hasAuthCookie,
    cacheStrategy: hasAuthCookie || isDynamicContent ? 'no-cache' : (isCacheablePage ? 'static-cache' : 'short-cache'),
    url: url,
    cacheablePage: isCacheablePage
  }
}) 