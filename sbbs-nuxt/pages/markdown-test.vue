<template>
  <div class="markdown-test-page">
    <h1>Markdown 渲染测试</h1>
    
    <div class="test-container">
      <div class="input-section">
        <h2>输入</h2>
        <textarea
          v-model="markdownInput"
          class="markdown-input"
          placeholder="输入Markdown测试内容"
          @input="updateRenderedContent"
        ></textarea>
      </div>
      
      <div class="output-section">
        <h2>渲染结果</h2>
        <div class="markdown-body" v-html="renderedContent"></div>
      </div>
    </div>
    
    <div class="test-cases">
      <h2>测试用例</h2>
      <button 
        v-for="(test, index) in testCases" 
        :key="index" 
        @click="loadTestCase(test)"
        class="test-button"
      >
        {{ test.name }}
      </button>
    </div>
    
    <div class="comparison-container">
      <h2>对比结果</h2>
      <div class="comparison">
        <div class="renderer-section">
          <h3>使用原始 Markdown-it</h3>
          <div class="markdown-body" v-html="originalRendered"></div>
        </div>
        <div class="renderer-section">
          <h3>使用增强的 Markdown-it</h3>
          <div class="markdown-body" v-html="enhancedRendered"></div>
        </div>
      </div>
    </div>
    
    <div class="implementation-container">
      <h2>实现方案</h2>
      <div class="implementation-code">
        <div class="code-header">
          <span>修复方案代码</span>
          <button @click="copyImplementation" class="copy-btn">
            <i class="ri-file-copy-line"></i>
            复制代码
          </button>
        </div>
        <pre class="code-block"><code>{{ implementationCode }}</code></pre>
      </div>
      
      <div class="explanation">
        <h3>修复说明</h3>
        <p>本修复方案通过以下方式解决了问题：</p>
        <ol>
          <li>
            <strong>行内加粗问题</strong>：通过迁移到 markdown-it 库解决，该库对行内加粗语法支持更完善，无需额外处理。
          </li>
          <li>
            <strong>引用块问题</strong>：在预处理阶段检测引用块前是否有空行，如果没有则自动添加，确保引用块渲染正确。
          </li>
          <li>
            <strong>扩展功能</strong>：通过插件系统增强了表格、锚点链接和表情符号支持。
          </li>
        </ol>
        <p>该方案比手动修复更稳定可靠，处理了更多可能的Markdown语法问题。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import MarkdownIt from 'markdown-it'
import { useMarkdownIt } from '~/composables/post/useMarkdownIt'

// 引入CSS
import '~/assets/css/markdown.css'

// 使用组合式函数
const { md: enhancedMd, renderMarkdown } = useMarkdownIt()

// 创建一个基础的markdown-it实例用于对比
const originalMd = new MarkdownIt({
  html: true,
  breaks: true,
  linkify: true,
  typographer: true
})

// 状态
const markdownInput = ref('')
const renderedContent = ref('')
const originalRendered = ref('')
const enhancedRendered = ref('')

// 实现代码
const implementationCode = `// 1. 安装依赖
npm uninstall marked
npm install markdown-it markdown-it-emoji markdown-it-anchor

// 2. 创建复用组件 composables/post/useMarkdownIt.js
import MarkdownIt from 'markdown-it'
import emoji from 'markdown-it-emoji'
import anchor from 'markdown-it-anchor'

export function createMarkdownIt(options = {}) {
  // 基础配置
  const defaultOptions = {
    html: true,
    breaks: true,
    linkify: true,
    typographer: true
  }
  
  // 合并用户选项
  const mdOptions = { ...defaultOptions, ...options }
  
  // 创建实例
  const md = new MarkdownIt(mdOptions)
  
  // 添加emoji支持
  md.use(emoji)
  
  // 添加锚点支持（可禁用）
  if (!options.disableAnchor) {
    md.use(anchor, {
      permalink: anchor.permalink.ariaHidden({
        placement: 'after',
        class: 'header-anchor',
        symbol: '#',
        ariaHidden: true,
      }),
    })
  }
  
  // 添加预处理功能 - 确保引用块前有空行
  const originalRender = md.render.bind(md)
  md.render = function(content) {
    // 预处理内容，确保引用块前有空行
    let processedContent = content.replace(/([^\\n])\\n(>.*)/g, 
      (match, prevLine, quoteLine) => {
        return \`\${prevLine}\\n\\n\${quoteLine}\`
      }
    )
    
    // 使用原始渲染函数
    return originalRender(processedContent)
  }
  
  return md
}

export function useMarkdownIt(options = {}) {
  const md = createMarkdownIt(options)
  
  const renderMarkdown = (content) => {
    if (!content) return ''
    return md.render(content)
  }
  
  return {
    md,
    renderMarkdown
  }
}

// 3. 在组件中使用
import { useMarkdownIt } from '~/composables/post/useMarkdownIt'

// 使用组合式函数渲染Markdown
const { renderMarkdown } = useMarkdownIt()
const html = renderMarkdown(markdownContent)`

// 测试用例
const testCases = [
  {
    name: '加粗文本测试',
    content: '这是普通文本，**这是加粗文本**，这又是普通文本。\n\n这行里面也有**加粗的部分**和更多**加粗的内容**。'
  },
  {
    name: '引用块测试',
    content: '这是一段普通文本。\n> 这是引用内容\n> 引用的第二行\n\n这是另一段文本。\n这又是一行文本\n> 不带空行的引用\n> 引用的第二行'
  },
  {
    name: '混合格式测试',
    content: '# 标题1\n\n## 标题2\n\n这是常规段落，包含**加粗**和*斜体*以及`代码`。\n\n> 这是引用块\n> 引用块中的**加粗文本**\n\n- 列表项1\n- 列表项2\n  - 嵌套列表项\n\n1. 有序列表1\n2. 有序列表2\n\n```javascript\nconst test = "代码块";\nconsole.log(test);\n```'
  },
  {
    name: '表格测试',
    content: '| 表头1 | 表头2 | 表头3 |\n| --- | --- | --- |\n| 单元格1 | 单元格2 | 单元格3 |\n| 数据A | 数据B | 数据C |\n| **加粗** | *斜体* | `代码` |'
  },
  {
    name: 'Emoji测试',
    content: '支持emoji表情符号 :smile: :heart: :thumbsup: :cake:'
  },
  {
    name: '已知问题测试',
    content: '**行内加粗问题测试**\n\n这是一行文本\n> 这是一个没有空行的引用\n> 第二行引用\n\n正常引用：\n\n> 这是有空行的引用\n> 第二行'
  },
  {
    name: '特殊格式测试 - **[标题]**',
    content: `这是一段普通文本。

**[游戏简介]**

这是游戏简介的内容，非常有趣的游戏。

**[领取地址]**

这是领取地址的相关信息。

后面还有其他内容。`
  }
]

// 复制实现代码
const copyImplementation = () => {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(implementationCode).then(() => {
      alert('代码已复制到剪贴板')
    })
  } else {
    // 兼容方案
    const textarea = document.createElement('textarea')
    textarea.value = implementationCode
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      alert('代码已复制到剪贴板')
    } catch (err) {
      console.error('复制失败:', err)
      alert('复制失败，请手动复制')
    }
    document.body.removeChild(textarea)
  }
}

// 加载测试用例
const loadTestCase = (test) => {
  markdownInput.value = test.content
  updateRenderedContent()
}

// 更新渲染内容
const updateRenderedContent = () => {
  // 使用增强版渲染
  renderedContent.value = renderMarkdown(markdownInput.value)
  
  // 对比渲染结果
  originalRendered.value = originalMd.render(markdownInput.value)
  enhancedRendered.value = renderMarkdown(markdownInput.value)
}

// 页面加载后初始化第一个测试用例
onMounted(() => {
  if (testCases.length > 0) {
    loadTestCase(testCases[0])
  }
  
  // 设置全局函数
  if (typeof window !== 'undefined') {
    window.copyCode = function(button) {
      const codeBlock = button.closest('.code-block-container').querySelector('code')
      const text = codeBlock.textContent
      
      if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => {
          const originalText = button.innerHTML
          button.innerHTML = '<i class="ri-check-line"></i> 已复制'
          button.style.background = '#22c55e'
          
          setTimeout(() => {
            button.innerHTML = originalText
            button.style.background = '#2d3748'
          }, 2000)
        })
      }
    }
    
    window.previewImage = function(src) {
      const overlay = document.createElement('div')
      overlay.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.8);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 10000;
        cursor: pointer;
      `
      
      const img = document.createElement('img')
      img.src = src
      img.style.cssText = `
        max-width: 90%;
        max-height: 90%;
        border-radius: 8px;
        box-shadow: 0 8px 32px rgba(0,0,0,0.3);
      `
      
      overlay.appendChild(img)
      document.body.appendChild(overlay)
      
      overlay.addEventListener('click', () => {
        document.body.removeChild(overlay)
      })
    }
  }
})
</script>

<style scoped>
.markdown-test-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

h1, h2, h3 {
  margin-top: 0;
  color: #333;
}

.test-container, .comparison-container, .implementation-container {
  margin-bottom: 2rem;
}

.test-container {
  display: flex;
  gap: 2rem;
}

.input-section, .output-section, .renderer-section {
  flex: 1;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
}

.comparison {
  display: flex;
  gap: 2rem;
}

.renderer-section {
  background-color: #f9f9f9;
}

.markdown-input {
  width: 100%;
  height: 300px;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-family: monospace;
  resize: vertical;
}

.test-cases {
  margin-top: 2rem;
  margin-bottom: 2rem;
}

.test-button {
  margin-right: 0.5rem;
  margin-bottom: 0.5rem;
  padding: 0.5rem 1rem;
  background-color: #f0f0f0;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.test-button:hover {
  background-color: #e0e0e0;
}

.implementation-code {
  background-color: #1e293b;
  border-radius: 8px;
  overflow: hidden;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: #334155;
  color: white;
}

.copy-btn {
  background-color: #475569;
  color: white;
  border: none;
  padding: 0.35rem 0.75rem;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.875rem;
}

.copy-btn:hover {
  background-color: #64748b;
}

.copy-btn i {
  font-size: 0.875rem;
}

.code-block {
  margin: 0;
  padding: 1rem;
  overflow-x: auto;
  font-family: monospace;
  color: #e2e8f0;
  font-size: 0.875rem;
  line-height: 1.5;
  max-height: 400px;
  overflow-y: auto;
}

.explanation {
  margin-top: 2rem;
  padding: 1.5rem;
  background-color: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.explanation h3 {
  margin-top: 0;
  margin-bottom: 1rem;
  color: #0f172a;
}

.explanation p, .explanation li {
  color: #334155;
  line-height: 1.6;
}

.explanation strong {
  color: #0f172a;
}

@media (max-width: 768px) {
  .test-container, .comparison {
    flex-direction: column;
  }
  
  .implementation-code {
    font-size: 0.75rem;
  }
}
</style> 