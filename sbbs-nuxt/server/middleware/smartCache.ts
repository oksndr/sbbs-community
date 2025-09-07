// 智能缓存中间件 - 根据登录状态决定缓存策略
export default defineEventHandler(async (event) => {
  const url = event.node.req.url || ''
  
  // 只处理页面请求，不处理API请求
  if (url.startsWith('/api/') || url.includes('/_nuxt/')) {
    return
  }
  
  // 检查是否是可缓存的页面
  const isCacheablePage = url === '/' || 
                         url === '/cache-test' ||
                         (url.startsWith('/post/') && 
                          !url.includes('/edit') && 
                          !url.includes('/publish')) ||
                         url.startsWith('/user/')
  
  if (!isCacheablePage) {
    return
  }
  
  // 解析cookies来检查登录状态
  const cookieHeader = getHeader(event, 'cookie') || ''
  const cookies = parseCookies(event)
  const hasAuth = !!cookies.Authorization || !!cookies.token
  
  if (hasAuth) {
    // 已登录用户 - 禁用缓存，确保动态内容
    setHeader(event, 'Cache-Control', 'no-cache, no-store, must-revalidate')
    setHeader(event, 'CF-Cache-Status', 'DYNAMIC')
    setHeader(event, 'X-User-Status', 'authenticated')
    // 禁用Nuxt的ISR缓存
    event.context.nitro = event.context.nitro || {}
    event.context.nitro.noCache = true
  } else {
    // 未登录用户 - 启用静态缓存
    setHeader(event, 'Cache-Control', 'public, max-age=300, s-maxage=300')
    setHeader(event, 'CF-Cache-Status', 'CACHEABLE')
    setHeader(event, 'X-User-Status', 'anonymous')
    setHeader(event, 'Vary', 'Cookie, Authorization')
    setHeader(event, 'Edge-Cache-Tag', 'anonymous-content')
    
    // 添加Last-Modified头
    const now = new Date()
    // 将时间舍入到最近的5分钟，以便相同时间窗口内的请求有相同的Last-Modified
    const roundedTime = new Date(Math.floor(now.getTime() / (5 * 60 * 1000)) * (5 * 60 * 1000))
    setHeader(event, 'Last-Modified', roundedTime.toUTCString())
  }
}) 