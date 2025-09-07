import MarkdownIt from 'markdown-it'

/**
 * 创建并配置markdown-it实例
 * @param {Object} options - 自定义选项
 * @returns {MarkdownIt} 配置好的markdown-it实例
 */
export function createMarkdownIt(options = {}) {
  // 基础配置
  const defaultOptions = {
    html: true,        // 启用HTML标签支持
    breaks: false,     // 修改：需要两个换行才分段，单个换行不转换为<br>
    linkify: true,     // 自动检测并转换URL为链接
    typographer: true  // 启用一些语言中性替换+引号美化
  }
  
  // 合并用户选项
  const mdOptions = { ...defaultOptions, ...options }
  
  // 创建实例
  const md = new MarkdownIt(mdOptions)
  
  // 自定义渲染器 - 优化表格样式
  const defaultTableOpen = md.renderer.rules.table_open || function(tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options)
  }
  md.renderer.rules.table_open = function(tokens, idx, options, env, self) {
    tokens[idx].attrJoin('class', 'markdown-table')
    return defaultTableOpen(tokens, idx, options, env, self)
  }
  
  // 自定义引用块渲染 - 添加样式类
  const defaultBlockquoteOpen = md.renderer.rules.blockquote_open || function(tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options)
  }
  md.renderer.rules.blockquote_open = function(tokens, idx, options, env, self) {
    tokens[idx].attrJoin('class', 'enhanced-blockquote')
    return defaultBlockquoteOpen(tokens, idx, options, env, self)
  }
  
  // 增强代码块渲染 - 添加复制按钮和语言标识
  const defaultFence = md.renderer.rules.fence
  md.renderer.rules.fence = function(tokens, idx, options, env, self) {
    const token = tokens[idx]
    const langName = token.info.trim() || 'text'
    const content = token.content
    
    // 使用默认渲染器获取基本HTML
    const defaultRendered = defaultFence(tokens, idx, options, env, self)
    
    // 增强HTML，添加复制按钮和语言标识
    return `<div class="code-block-container">
      <div class="code-block-header">
        <span class="code-language">${langName}</span>
        <button class="copy-code-btn" onclick="copyCode(this)">
          <i class="ri-file-copy-line"></i>
          复制
        </button>
      </div>
      ${defaultRendered}
    </div>`
  }
  
  // 增强图片渲染 - 添加容器和点击功能
  const defaultImage = md.renderer.rules.image || function(tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options)
  }
  md.renderer.rules.image = function(tokens, idx, options, env, self) {
    const token = tokens[idx]
    const srcIndex = token.attrIndex('src')
    const src = token.attrs[srcIndex][1]
    const altIndex = token.attrIndex('alt')
    const alt = altIndex >= 0 ? token.attrs[altIndex][1] : ''
    
    return `<div class="image-container">
      <img src="${src}" alt="${alt}" class="content-image" style="cursor: pointer;" onclick="previewImage('${src}')" />
      ${alt ? `<div class="image-caption">${alt}</div>` : ''}
    </div>`
  }
  
  // 添加预处理功能 - 确保引用块前有空行
  const originalRender = md.render.bind(md)
  md.render = function(content) {
    // 预处理内容
    let processedContent = content
    
    // 处理引用块前的文本，确保有空行分隔
    processedContent = processedContent.replace(/([^\n])\n(>.*)/g, (match, prevLine, quoteLine) => {
      return `${prevLine}\n\n${quoteLine}`
    })
    
    // 使用原始渲染函数
    return originalRender(processedContent)
  }
  
  return md
}

/**
 * 使用markdown-it进行渲染的组合式函数
 * @returns {{
 *   md: MarkdownIt,
 *   renderMarkdown: function
 * }} 返回markdown-it实例和渲染函数
 */
export function useMarkdownIt(options = {}) {
  // 创建markdown-it实例
  const md = createMarkdownIt(options)
  
  // 内联样式增强 - 确保行内加粗正确渲染
  // 添加自定义文本规则处理，强制处理加粗语法
  try {
    const originalTextRule = md.renderer.rules.text || function(tokens, idx) {
      return md.utils.escapeHtml(tokens[idx].content);
    };

    md.renderer.rules.text = function(tokens, idx) {
      let content = tokens[idx].content;
      
      // 手动处理加粗语法
      content = content.replace(/\*\*([^\*]+)\*\*/g, '<strong>$1</strong>');
      
      // 然后运行原始规则
      tokens[idx].content = content;
      return originalTextRule(tokens, idx);
    };
  } catch (err) {
    console.error('配置文本渲染器失败:', err);
  }
  
  // 渲染函数
  const renderMarkdown = (content) => {
    if (!content) return ''
    
    // 预处理：手动处理行内加粗格式
    // 这一步是为了确保即使md.renderer.rules.text修改失效，仍然可以处理加粗
    content = content.replace(/\*\*([^\*]+)\*\*/g, '@@BOLD_START@@$1@@BOLD_END@@');
    
    // 渲染Markdown
    let html = md.render(content);
    
    // 后处理：替换临时标记
    html = html.replace(/@@BOLD_START@@/g, '<strong>');
    html = html.replace(/@@BOLD_END@@/g, '</strong>');
    
    return html;
  }
  
  return {
    md,
    renderMarkdown
  }
}

/**
 * 自动为中文段落添加空行分隔
 * 解决连续中文文本不自动分段的问题
 * @param {string} content - 原始内容
 * @returns {string} 处理后的内容
 */
export function autoAddParagraphBreaks(content) {
  if (!content) return ''
  
  let processedContent = content.trim()
  
  // 保护代码块、引用块等特殊区域
  const protectedBlocks = []
  let blockCounter = 0
  
  // 1. 保护代码块 (```)
  processedContent = processedContent.replace(/```[\s\S]*?```/g, (match) => {
    const placeholder = `__CODE_BLOCK_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 2. 保护行内代码 (`)
  processedContent = processedContent.replace(/`[^`\n]+`/g, (match) => {
    const placeholder = `__INLINE_CODE_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 3. 保护引用块 (>)
  processedContent = processedContent.replace(/^>.*(?:\n^>.*)*$/gm, (match) => {
    const placeholder = `__QUOTE_BLOCK_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 4. 保护标题 (#)
  processedContent = processedContent.replace(/^#{1,6}\s+.*$/gm, (match) => {
    const placeholder = `__TITLE_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 5. 保护列表项
  processedContent = processedContent.replace(/^(\s*)[-*+]\s+.*$/gm, (match) => {
    const placeholder = `__LIST_ITEM_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  processedContent = processedContent.replace(/^(\s*)\d+\.\s+.*$/gm, (match) => {
    const placeholder = `__NUM_LIST_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 6. 保护图片和链接
  processedContent = processedContent.replace(/!\[[^\]]*\]\([^)]+\)/g, (match) => {
    const placeholder = `__IMAGE_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 7. 保护表格行
  processedContent = processedContent.replace(/^\|.*\|$/gm, (match) => {
    const placeholder = `__TABLE_ROW_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 8. 保护分隔线
  processedContent = processedContent.replace(/^(---+|===+|\*\*\*+)$/gm, (match) => {
    const placeholder = `__SEPARATOR_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
  // 9. 保护自定义格式 **[任意字符]**
  processedContent = processedContent.replace(/\*\*\[[^\]]+\]\*\*/g, (match) => {
    const placeholder = `__CUSTOM_FORMAT_${blockCounter++}__`
    protectedBlocks.push({ placeholder, content: match })
    return placeholder
  })
  
       // 现在对剩余的纯文本内容进行段落处理
  // 将所有单个换行转换为双换行（段落分隔）
  // 重点是回车就换行，不管内容是什么
  processedContent = processedContent.replace(/([^\n])\n([^\n])/g, (match, before, after) => {
    // 检查是否是占位符（受保护的内容）
    const isPlaceholderBefore = /__[A-Z_]+_\d+__/.test(before)
    const isPlaceholderAfter = /__[A-Z_]+_\d+__/.test(after)
    
    // 如果涉及占位符，保持原样
    if (isPlaceholderBefore || isPlaceholderAfter) {
      return match
    }
    
    // 所有单个换行都变成双换行（段落分隔）
    return `${before}\n\n${after}`
  })
  
  // 恢复受保护的内容
  protectedBlocks.forEach(({ placeholder, content }) => {
    processedContent = processedContent.replace(placeholder, content)
  })
  
  // 清理多余的连续空行（超过2个换行的地方减少为2个）
  processedContent = processedContent.replace(/\n{3,}/g, '\n\n')
  
  return processedContent
}

/**
 * 标准化markdown内容格式
 * 确保图片、标题、代码块等前后有正确的空行
 * @param {string} content - 原始markdown内容
 * @returns {string} 格式化后的markdown内容
 */
export function standardizeMarkdownContent(content) {
  if (!content) return ''
  
  let processedContent = content.trim()
  
  // 处理软换行问题：将从其他地方复制来的软换行转换为真正的换行
  // 检测可能的软换行模式并标准化
  // 修复：先保护所有完整的markdown标题，避免被错误分割
  const titleProtectionMap = new Map()
  let titleCounter = 0
  
  // 保护完整的标题行（包括emoji等特殊字符）
  processedContent = processedContent.replace(/^(#{1,6}\s+.*)$/gm, (match) => {
    const placeholder = `__TITLE_PROTECTION_${titleCounter++}__`
    titleProtectionMap.set(placeholder, match)
    return placeholder
  })
  
  processedContent = processedContent.replace(/([^\n])\n([^\n\s])/g, (match, before, after) => {
    // 如果前一行已经是空行，保持原样
    if (before === '') {
      return match
    }
    // 如果是代码块内容，保持原样
    if (before.includes('```') || after.includes('```')) {
      return match
    }
    // 如果是特殊格式开始，保持原样  
    if (/^\*\*\[/.test(after)) {
      return match
    }
    // 如果是受保护的标题，保持原样
    if (before.includes('__TITLE_PROTECTION_') || after.includes('__TITLE_PROTECTION_')) {
      return match
    }
    // 修复：如果是标点符号或链接结束符等，应该与前面的内容保持连接
    // 检查是否是常见的不应该被分行的情况
    if (/^[！。？，、；：）】」},.!?;:)\]}]+/.test(after) || // 标点符号开头
        /^\)/.test(after) || // 右括号开头 (链接结束)
        /^[\u4e00-\u9fff]$/.test(after.charAt(0))) { // 单个中文字符
      return match
    }
    // 如果后面是列表、代码块等特殊格式，保持原样（标题已被保护，这里不需要再检查）
    if (/^([-*+]\s+|\d+\.\s+|```|>)/.test(after)) {
      return match
    }
    // 默复：检查前一个字符是否看起来是被人为截断的
    // 比如中文字符后面跟着标点符号，通常不应该分行
    const lastCharOfBefore = before.charAt(before.length - 1)
    if (/[\u4e00-\u9fff]/.test(lastCharOfBefore) && /^[！。？，、；：）】」}]/.test(after)) {
      return match // 中文字符后跟中文标点，保持连接
    }
    
    // 默认情况：添加空行，让文本更易读
    return `${before}\n\n${after}`
  })
  
  // 恢复被保护的标题
  for (const [placeholder, originalTitle] of titleProtectionMap) {
    processedContent = processedContent.replace(placeholder, originalTitle)
  }
  
  // 确保特殊格式 **[标题]** 前后有空行
  processedContent = processedContent.replace(/([^\n\s])(\*\*\[[^\]]+\]\*\*)/g, '$1\n\n$2')  // 特殊格式前加空行（同一行）
  processedContent = processedContent.replace(/([^\n])\n(\*\*\[[^\]]+\]\*\*)/g, '$1\n\n$2')  // 特殊格式前加空行（换行后）
  processedContent = processedContent.replace(/(\*\*\[[^\]]+\]\*\*)\n([^\n])/g, '$1\n\n$2')  // 特殊格式后加空行
  processedContent = processedContent.replace(/(\*\*\[[^\]]+\]\*\*)([^\n\s])/g, '$1\n\n$2')  // 特殊格式后加空行（同一行）
  
  // 确保图片前后有空行（标准markdown格式）
  // 处理同一行中的图片（如：文本![图片](url)）
  processedContent = processedContent.replace(/([^\n\s])(!\[[^\]]*\]\([^)]+\))/g, '$1\n\n$2')  // 图片前加空行（同一行）
  // 处理换行后的图片
  processedContent = processedContent.replace(/([^\n])\n(!\[[^\]]*\]\([^)]+\))/g, '$1\n\n$2')  // 图片前加空行（换行后）
  // 处理图片后的内容
  processedContent = processedContent.replace(/(!\[[^\]]*\]\([^)]+\))\n([^\n])/g, '$1\n\n$2')  // 图片后加空行
  // 修复：移除会错误分割标点符号的图片处理正则表达式
  // 原来的正则：processedContent = processedContent.replace(/(!\[[^\]]*\]\([^)]+\))([^\n\s])/g, '$1\n\n$2')
  // 这个正则会把图片链接后面的标点符号错误地分行
  
  // 确保标题前后有空行
  // 修复：改进标题处理逻辑，避免错误分割标题内容
  // 使用更安全的方法，基于行来处理，而不是字符模式
  const lines = processedContent.split('\n')
  const processedLines = []
  
  for (let i = 0; i < lines.length; i++) {
    const currentLine = lines[i]
    const prevLine = i > 0 ? lines[i - 1] : null
    const nextLine = i < lines.length - 1 ? lines[i + 1] : null
    
    // 检查当前行是否是标题
    const isTitle = /^#{1,6}\s+.+/.test(currentLine)
    
    if (isTitle) {
      // 如果是标题，检查前面是否需要空行
      if (prevLine !== null && prevLine.trim() !== '' && processedLines[processedLines.length - 1] !== '') {
        processedLines.push('')  // 添加空行
      }
      processedLines.push(currentLine)
      
      // 检查后面是否需要空行
      if (nextLine !== null && nextLine.trim() !== '' && !nextLine.startsWith('#')) {
        // 如果下一行不是空行也不是标题，添加空行
        if (i + 1 < lines.length && lines[i + 1] !== '') {
          processedLines.push('')
        }
      }
    } else {
      processedLines.push(currentLine)
    }
  }
  
  processedContent = processedLines.join('\n')
  
  // 确保代码块前后有空行
  processedContent = processedContent.replace(/([^\n\s])(```)/g, '$1\n\n$2')  // 代码块前加空行（同一行）
  processedContent = processedContent.replace(/([^\n])\n(```)/g, '$1\n\n$2')  // 代码块前加空行（换行后）
  processedContent = processedContent.replace(/(```[^`]*```)\n([^\n])/g, '$1\n\n$2')  // 代码块后加空行
  processedContent = processedContent.replace(/(```[^`]*```)([^\n\s])/g, '$1\n\n$2')  // 代码块后加空行（同一行）
  
  // 确保引用块前后有空行
  processedContent = processedContent.replace(/([^\n\s])(>[^\n]*)/g, '$1\n\n$2')  // 引用块前加空行（同一行）
  processedContent = processedContent.replace(/([^\n])\n(>[^\n]*)/g, '$1\n\n$2')  // 引用块前加空行（换行后）
  processedContent = processedContent.replace(/(>[^\n]*(?:\n>[^\n]*)*)\n([^\n>])/g, '$1\n\n$2')  // 引用块后加空行
  // 修复：移除会错误分割标点符号的引用块处理正则表达式
  // 原来的正则：processedContent = processedContent.replace(/(>[^\n]*(?:\n>[^\n]*)*)([^\n\s>])/g, '$1\n\n$2')
  // 这个正则会把 "引用内容！" 分割成 "引用内容" 和 "！"，导致标点符号单独成行
  
  // 确保列表前后有空行（修复：避免误匹配特殊格式中的星号）
  // 更精确地匹配列表项：必须是行首的列表标记
  processedContent = processedContent.replace(/([^\n])\n(^[-*+]\s+)/gm, '$1\n\n$2')  // 无序列表前加空行（换行后，行首匹配）
  processedContent = processedContent.replace(/([^\n])\n(^\d+\.\s+)/gm, '$1\n\n$2')  // 有序列表前加空行（换行后，行首匹配）
  
  // 修复：确保水平分割线前后有空行（精确匹配，避免干扰特殊格式）
  // 只匹配纯分割线（以行首开始的分割线）
  processedContent = processedContent.replace(/([^\n])\n(^---+$|^===+$|^\*\*\*+$)/gm, '$1\n\n$2')  // 分割线前加空行
  processedContent = processedContent.replace(/(^---+$|^===+$|^\*\*\*+$)\n([^\n])/gm, '$1\n\n$2')  // 分割线后加空行
  
  // 确保表格前后有空行
  processedContent = processedContent.replace(/([^\n\s])(\|[^\n]*\|)/g, '$1\n\n$2')  // 表格前加空行（同一行）
  processedContent = processedContent.replace(/([^\n])\n(\|[^\n]*\|)/g, '$1\n\n$2')  // 表格前加空行（换行后）
  processedContent = processedContent.replace(/(\|[^\n]*\|(?:\n\|[^\n]*\|)*)\n([^\n|])/g, '$1\n\n$2')  // 表格后加空行
  processedContent = processedContent.replace(/(\|[^\n]*\|(?:\n\|[^\n]*\|)*)([^\n\s|])/g, '$1\n\n$2')  // 表格后加空行（同一行）
  
  // 清理多余的连续空行（超过2个换行的地方减少为2个）
  processedContent = processedContent.replace(/\n{3,}/g, '\n\n')
  
  return processedContent
} 