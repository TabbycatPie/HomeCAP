<template>
  <div class="login-container">
    <div class="login-card">
      <div class="logo-row">
        <svg viewBox="0 0 32 32" width="36" height="36">
          <path d="M16 2L4 8v8c0 7.2 5.1 13.9 12 15.5 6.9-1.6 12-8.3 12-15.5V8L16 2zm0 6a3 3 0 110 6 3 3 0 010-6zm-6 12c1.3-2.7 4.7-4 6-4s4.7 1.3 6 4H10z" fill="#409eff"/>
        </svg>
        <h2 class="title">统一认证平台</h2>
      </div>
      <p class="subtitle">登录到您的账号</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="links">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 已登录 + 有 redirect 参数 → 直接跳到授权页，无需再输入密码
onMounted(() => {
  var hash = window.location.hash
  var qi = hash.indexOf('?')
  if (qi >= 0) {
    var q = new URLSearchParams(hash.substring(qi + 1))
    var redirect = q.get('redirect')
    if (redirect && authStore.isLoggedIn) {
      window.location.href = decodeURIComponent(redirect)
    }
  }
})

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    // OIDC redirect 支持 — hash 路由下从 hash 中取参数
    var hash = window.location.hash
    var qi = hash.indexOf('?')
    if (qi >= 0) {
      var q = new URLSearchParams(hash.substring(qi + 1))
      var redirect = q.get('redirect')
      if (redirect) {
        window.location.href = decodeURIComponent(redirect)
        return
      }
    }
    router.push('/dashboard')
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  background: #fff;
  padding: 40px;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  width: 400px;
  max-width: 90vw;
}
.logo-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
}
.title {
  text-align: center;
  margin: 0 0 8px;
  color: #303133;
}
.subtitle {
  text-align: center;
  color: #909399;
  margin: 0 0 30px;
  font-size: 14px;
}
.links {
  text-align: center;
  font-size: 14px;
  color: #909399;
}
.links a {
  color: #409eff;
  text-decoration: none;
}
</style>
