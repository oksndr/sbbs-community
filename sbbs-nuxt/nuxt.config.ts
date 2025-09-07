// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  ssr: true, // 启用服务器端渲染
  compatibilityDate: '2025-05-15',
  devtools: { enabled: true },

  // 全局CSS
  css: [
    'remixicon/fonts/remixicon.css',
    '~/assets/css/main.css',
    '~/assets/css/style.css',
    '~/assets/css/comment-section.css',
  ],

  // 插件配置
  plugins: [
    '~/plugins/wafInterceptor.js'
  ],

  // 静态资源目录配置
  nitro: {
    publicAssets: [
      {
        dir: 'public',
        baseURL: '/'
      }
    ],
    // 开发环境代理配置
    devProxy: {
      '/api': {
        target: process.env.SBBS_DEV_API_URL || 'http://localhost:12367',
        changeOrigin: true,
        prependPath: true,
        headers: {
          'X-Forwarded-For': 'nitro-proxy'
        }
      }
    },
    // 生产环境代理配置
    routeRules: {
      '/api/**': {
        proxy: `${process.env.SBBS_API_URL || 'http://example:port'}/**`,
        cors: true,
        headers: {
          'X-Forwarded-For': 'nitro-proxy'
        }
      },
      // 为未登录用户的首页设置缓存规则（使用条件缓存）
      '/': {
        isr: false, // 暂时禁用ISR，避免SPA导航问题
        headers: {
          'Cache-Control': 'public, max-age=60, s-maxage=300',
          'Vary': 'Cookie, Authorization'
        }
      },
      // 为未登录用户的帖子详情页设置缓存规则
      '/post/**': {
        isr: false, // 暂时禁用ISR，避免SPA导航问题
        headers: {
          'Cache-Control': 'public, max-age=60, s-maxage=300',
          'Vary': 'Cookie, Authorization'
        }
      },
      // 为未登录用户的用户详情页设置缓存规则
      '/user/**': {
        isr: false, // 禁用ISR，由智能缓存中间件处理
        headers: {
          'Cache-Control': 'public, max-age=60, s-maxage=300',
          'Vary': 'Cookie, Authorization'
        }
      }
    }
  },

  // 应用程序头部配置
  app: {
    head: {
      title: 'SBBS社区',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'SBBS社区 - 一个现代化的社区论坛' }
      ],
      link: [
        { rel: 'icon', type: 'image/webp', href: '/favicon.webp' },
        { rel: 'icon', type: 'image/png', href: '/favicon.png' },
        { rel: 'apple-touch-icon', type: 'image/webp', href: '/favicon.webp' },
        { rel: 'stylesheet', href: '/fonts/inter.css' }
      ],
      script: [
        // marked.js已经通过npm安装，不再需要CDN引用
      ]
    }
  },

  // 组件自动导入配置
  components: {
    global: true,
    dirs: ['~/components']
  },

  // 模块
  modules: [
    '@pinia/nuxt',
    '@nuxtjs/tailwindcss',
    '@element-plus/nuxt',
  ],

  // Element Plus配置 - 修复为正确的格式
  build: {
    transpile: ['element-plus/es']
  },

  vite: {
    // 为Element Plus添加适当的配置
    optimizeDeps: {
      include: ['element-plus']
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "element-plus/theme-chalk/src/index.scss" as *;`
        }
      }
    }
  },

  // 运行时配置 - 根据环境区分开发和生产
  runtimeConfig: {
    // 服务器端私有配置 - 后端API地址（不会暴露给客户端）
    apiBaseUrl: process.env.NODE_ENV === 'development'
      ? (process.env.SBBS_DEV_API_URL || 'http://localhost:12367')
      : (process.env.SBBS_API_URL || 'http://' +
            'example:port'),
    // 客户端公共配置
    public: {
      // 客户端统一使用代理路径
      apiBase: '/api'
    }
  }
})
