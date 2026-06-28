<template>
  <el-container style="min-height: 100vh">
    <el-aside :width="collapsed ? '64px' : '220px'" style="background:#1e293b;transition:width .3s">
      <div class="logo" :style="{ padding: collapsed ? '16px 0' : '20px' }">
        <svg v-if="collapsed" viewBox="0 0 24 24" width="28" height="28" fill="none"><path d="M12 3c-1.5 0-3 .7-4 2-1.3 1.8-1.8 4-1.2 6.5.5 2 1.4 3.5 2.7 4.5" stroke="#60a5fa" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="9" stroke="#60a5fa" stroke-width="2"/><circle cx="12" cy="12" r="3" fill="#60a5fa"/></svg>
          <template v-else>
            <h2 style="color:#fff;margin:0;font-size:18px;display:flex;align-items:center;gap:8px">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="none"><path d="M12 3c-1.5 0-3 .7-4 2-1.3 1.8-1.8 4-1.2 6.5.5 2 1.4 3.5 2.7 4.5" stroke="#60a5fa" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="9" stroke="#60a5fa" stroke-width="2"/><circle cx="12" cy="12" r="3" fill="#60a5fa"/></svg>
              CAP
            </h2>
            <span style="color:#94a3b8;font-size:11px">统一认证平台</span>
          </template>
      </div>
      <el-divider style="border-color:#334155;margin:0"/>

      <div class="collapse-btn" @click="collapsed = !collapsed">
        <Fold v-if="!collapsed" style="width:16px;height:16px;color:#94a3b8" />
        <Expand v-else style="width:16px;height:16px;color:#94a3b8" />
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        background-color="#1e293b"
        text-color="#94a3b8"
        active-text-color="#60a5fa"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Grid /></el-icon>
          <template #title>我的应用</template>
        </el-menu-item>

        <template v-if="authStore.isAdmin">
          <el-divider style="border-color:#334155;margin:8px 0" />
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/apps">
            <el-icon><Setting /></el-icon>
            <template #title>应用管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/permissions">
            <el-icon><Key /></el-icon>
            <template #title>权限分配</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background:#fff;border-bottom:1px solid #e2e8f0;display:flex;align-items:center;justify-content:space-between;height:60px;padding:0 24px">
        <div style="color:#64748b;font-size:14px">{{ route.meta?.title || '' }}</div>
        <el-dropdown trigger="click">
          <div class="user-trigger">
            <UserCircleIcon :size="22" color="#64748b" />
            <span>{{ authStore.user?.nickname || authStore.user?.username }}</span>
            <ChevronDownIcon style="width:16px;height:16px;color:#94a3b8" />
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="showPassword = true">
                <LockIcon :size="16" style="margin-right:6px" />修改密码
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <LogoutIcon :size="16" style="margin-right:6px" />退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main style="padding:24px;background:#f1f5f9;min-height:calc(100vh - 60px)">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="showPassword" title="修改密码" width="420px">
    <el-form :model="pwdForm" label-width="100px" @keyup.enter="handleChangePwd">
      <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
      <el-form-item label="确认密码"><el-input v-model="pwdForm.confirmPassword" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showPassword = false">取消</el-button>
      <el-button type="primary" :loading="pwdLoading" @click="handleChangePwd">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { userApi } from '@/api'
import { ElMessage } from 'element-plus'

// Tabler Icons — 直接用路径避免 tree-shaking
import UserCircleIcon from '@tabler/icons-vue/dist/esm/icons/IconUserCircle.mjs'
import ChevronDownIcon from '@tabler/icons-vue/dist/esm/icons/IconChevronDown.mjs'
import LockIcon from '@tabler/icons-vue/dist/esm/icons/IconLock.mjs'
import LogoutIcon from '@tabler/icons-vue/dist/esm/icons/IconLogout.mjs'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const collapsed = ref(false)

const showPassword = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function handleChangePwd() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) { ElMessage.warning('请填写完整'); return }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) { ElMessage.warning('两次密码不一致'); return }
  if (pwdForm.newPassword.length < 4) { ElMessage.warning('密码至少4位'); return }
  pwdLoading.value = true
  try {
    await userApi.changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    ElMessage.success('密码修改成功')
    showPassword.value = false
    pwdForm.oldPassword = ''; pwdForm.newPassword = ''; pwdForm.confirmPassword = ''
  } catch {} finally { pwdLoading.value = false }
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.logo { text-align: center; border-bottom: 1px solid #334155; }
.collapse-btn {
  display: flex; justify-content: center; align-items: center; height: 32px; cursor: pointer; border-bottom: 1px solid #334155; font-size: 16px;
}
.collapse-btn:hover { background: #263445; }
.user-trigger {
  display: flex; align-items: center; gap: 6px; cursor: pointer; color: #334155; font-size: 14px; padding: 6px 12px; border-radius: 8px;
}
.user-trigger:hover { background: #f1f5f9; }
.el-divider { border-color: #334155; }
</style>
