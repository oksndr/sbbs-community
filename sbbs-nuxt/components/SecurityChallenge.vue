<template>
  <div v-if="isVisible" class="security-challenge-overlay">
    <div class="security-challenge-container">
      <div class="security-challenge-header">
        <div class="security-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
            <path fill="#f38020" d="M32 0C14.3 0 0 14.3 0 32S14.3 64 32 64s32-14.3 32-32S49.7 0 32 0zm0 10.4c11.9 0 21.6 9.7 21.6 21.6 0 11.9-9.7 21.6-21.6 21.6-11.9 0-21.6-9.7-21.6-21.6 0-11.9 9.7-21.6 21.6-21.6z"/>
          </svg>
        </div>
        <h1>安全验证</h1>
      </div>
      <div class="security-challenge-content">
        <p>为了保护网站免受自动化滥用，请完成以下验证</p>
        <div class="challenge-box">
          <div v-if="challengeStep === 'waiting'" class="waiting-step">
            <div class="spinner"></div>
            <p>正在检查您的浏览器...</p>
          </div>
          <div v-if="challengeStep === 'solve'" class="solve-step">
            <p>请完成以下数学计算:</p>
            <div class="math-challenge">
              <span>{{ mathProblem.num1 }} + {{ mathProblem.num2 }} = ?</span>
              <input 
                type="number" 
                v-model="userAnswer" 
                placeholder="输入答案" 
                @keyup.enter="checkAnswer"
              />
              <button @click="checkAnswer" class="verify-btn">验证</button>
            </div>
          </div>
          <div v-if="challengeStep === 'success'" class="success-step">
            <div class="success-icon">✓</div>
            <p>验证成功！正在重定向...</p>
          </div>
        </div>
      </div>
      <div class="security-challenge-footer">
        <p>此安全检查将在您访问站点后自动消失</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { markChallengeAsPassed } from '../utils/requestGuard'

const props = defineProps({
  isVisible: {
    type: Boolean,
    default: false
  },
  onComplete: {
    type: Function,
    default: () => {}
  }
})

// 挑战状态
const challengeStep = ref('waiting') // waiting, solve, success
const userAnswer = ref('')
const mathProblem = reactive({
  num1: 0,
  num2: 0,
  correctAnswer: 0
})

// 生成随机数学问题
function generateMathProblem() {
  mathProblem.num1 = Math.floor(Math.random() * 10) + 1
  mathProblem.num2 = Math.floor(Math.random() * 10) + 1
  mathProblem.correctAnswer = mathProblem.num1 + mathProblem.num2
}

// 检查答案
function checkAnswer() {
  if (parseInt(userAnswer.value) === mathProblem.correctAnswer) {
    challengeStep.value = 'success'
    
    // 标记挑战通过
    markChallengeAsPassed()
    
    // 3秒后完成并关闭挑战
    setTimeout(() => {
      props.onComplete()
    }, 2000)
  } else {
    // 答案错误，重新生成问题
    userAnswer.value = ''
    generateMathProblem()
    alert('答案错误，请重试')
  }
}

// 组件挂载时
onMounted(() => {
  // 模拟等待检查
  setTimeout(() => {
    challengeStep.value = 'solve'
    generateMathProblem()
  }, 1500)
})
</script>

<style scoped>
.security-challenge-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.75);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
}

.security-challenge-container {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
  width: 90%;
  max-width: 500px;
  padding: 2rem;
}

.security-challenge-header {
  display: flex;
  align-items: center;
  margin-bottom: 1.5rem;
}

.security-icon {
  margin-right: 1rem;
}

.security-challenge-header h1 {
  font-size: 1.8rem;
  color: #333;
  margin: 0;
}

.security-challenge-content {
  margin-bottom: 1.5rem;
}

.security-challenge-footer {
  font-size: 0.8rem;
  color: #666;
  text-align: center;
}

.challenge-box {
  background-color: #f5f5f5;
  padding: 1.5rem;
  border-radius: 6px;
  margin: 1rem 0;
}

.waiting-step, .solve-step, .success-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(0, 0, 0, 0.1);
  border-left-color: #f38020;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.math-challenge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  width: 100%;
}

.math-challenge span {
  font-size: 1.5rem;
  font-weight: bold;
}

.math-challenge input {
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  width: 60%;
  text-align: center;
}

.verify-btn {
  background-color: #f38020;
  color: white;
  border: none;
  padding: 0.75rem 2rem;
  font-size: 1rem;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.verify-btn:hover {
  background-color: #e67510;
}

.success-icon {
  background-color: #4caf50;
  color: white;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 2rem;
  margin-bottom: 1rem;
}
</style> 