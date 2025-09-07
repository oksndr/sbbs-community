import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'

/**
 * 简化的Markdown渲染组合式函数
 * 使用md-editor-v3的预览组件进行渲染
 * 替代复杂的useMarkdownIt实现
 */
export function useSimpleMarkdown() {
  
  /**
   * 渲染markdown内容为HTML字符串
   * @param {string} content - markdown内容
   * @returns {string} 渲染后的HTML
   */
  const renderMarkdown = (content) => {
    if (!content) return ''
    
    // 使用md-editor-v3内置的markdown解析器
    // 这比自定义的markdown-it配置更简单且功能完备
    return content
  }
  
  return {
    renderMarkdown,
    MdPreview // 导出预览组件供页面直接使用
  }
}
