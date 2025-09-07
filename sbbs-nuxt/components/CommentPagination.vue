<template>
  <div class="pagination-container">
    <nav class="pagination">
      <button 
        class="page-btn"
        :disabled="currentPage <= 1"
        @click="goToPage(currentPage - 1)">
        <i class="ri-arrow-left-line"></i>
        上一页
      </button>
      
      <button
        v-for="page in getPageNumbers()"
        :key="page"
        class="page-number-btn"
        :class="{ active: page === currentPage }"
        @click="goToPage(page)">
        {{ page }}
      </button>
      
      <button 
        class="page-btn"
        :disabled="currentPage >= totalPages"
        @click="goToPage(currentPage + 1)">
        下一页
        <i class="ri-arrow-right-line"></i>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentPage: {
    type: Number,
    required: true
  },
  totalPages: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['page-change'])

const goToPage = (page) => {
  if (page >= 1 && page <= props.totalPages && page !== props.currentPage) {
    emit('page-change', page)
  }
}

const getPageNumbers = () => {
  const pages = []
  const totalPages = props.totalPages
  const currentPage = props.currentPage
  
  if (totalPages <= 7) {
    // 总页数少于等于7页，显示所有页码
    for (let i = 1; i <= totalPages; i++) {
      pages.push(i)
    }
  } else {
    // 总页数大于7页，智能显示
    if (currentPage <= 4) {
      // 当前页在前面
      for (let i = 1; i <= 5; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(totalPages)
    } else if (currentPage >= totalPages - 3) {
      // 当前页在后面
      pages.push(1)
      pages.push('...')
      for (let i = totalPages - 4; i <= totalPages; i++) {
        pages.push(i)
      }
    } else {
      // 当前页在中间
      pages.push(1)
      pages.push('...')
      for (let i = currentPage - 1; i <= currentPage + 1; i++) {
        pages.push(i)
      }
      pages.push('...')
      pages.push(totalPages)
    }
  }
  
  return pages
}
</script> 