/**
 * 格式化相关的工具函数
 */
export function useFormatters() {
  // 格式化日期时间
  const formatDateTime = (timestamp) => {
    if (!timestamp) return ''
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN', { 
      year: 'numeric', 
      month: '2-digit', 
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }
  
  // 格式化时间为"多久之前"
  const formatTimeAgo = (timestamp) => {
    if (!timestamp) return ''
    const now = Date.now()
    const past = new Date(timestamp).getTime()
    const diffInSeconds = Math.floor((now - past) / 1000)
    if (diffInSeconds < 60) return `${diffInSeconds} 秒前`
    if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} 分钟前`
    if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} 小时前`
    return `${Math.floor(diffInSeconds / 86400)} 天前`
  }

  return {
    formatDateTime,
    formatTimeAgo
  }
} 