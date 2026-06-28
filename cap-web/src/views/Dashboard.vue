<template>
  <div>
    <h2 style="margin-bottom:20px">我的应用</h2>
    <div v-if="loading" style="text-align:center;padding:60px">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p style="color:#909399;margin-top:12px">加载中...</p>
    </div>
    <div v-else-if="apps.length === 0" style="text-align:center;padding:60px">
      <el-empty description="暂无可用应用"><p style="color:#909399">请联系管理员分配权限</p></el-empty>
    </div>
    <el-row v-else :gutter="20">
      <el-col v-for="app in apps" :key="app.appId" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom:20px">
        <el-card shadow="hover" :body-style="{padding:'24px',cursor:'pointer',textAlign:'center'}" @click="handleAccess(app)">
          <div class="app-icon" v-loading="accessingId === app.appId">
            <AppIcon v-if="accessingId !== app.appId" :app-type="app.appType" :icon-url="app.iconUrl" :size="48" />
            <el-icon v-else class="is-loading" :size="48" color="#409eff"><Loading /></el-icon>
          </div>
          <h3 style="margin:12px 0 4px">{{ app.appName }}</h3>
          <p style="color:#909399;font-size:13px;margin:0">
            <span v-if="accessingId === app.appId">跳转中...</span>
            <span v-else>点击访问</span>
          </p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userPermApi, ssoApi } from '@/api'
import { useAuthStore } from '@/store/auth'
import AppIcon from '@/components/AppIcon.vue'

const authStore = useAuthStore()
const loading = ref(true)
const accessingId = ref(null)
const apps = ref([])

onMounted(async () => {
  try {
    const user = authStore.user
    if (!user) return
    const [perms, allApps] = await Promise.all([
      userPermApi.myPerms(),
      userPermApi.apps()
    ])
    const permMap = new Set(perms.filter(p => p.status === 'enabled').map(p => p.appId))
    apps.value = allApps
      .filter(a => permMap.has(a.id) && a.status === 'enabled')
      .map(a => ({ appId: a.id, appName: a.name, appType: a.appType, iconUrl: a.iconUrl, publicUrl: a.publicUrl }))
  } catch {} finally { loading.value = false }
})

function handleAccess(app) {
  if (accessingId.value) return
  accessingId.value = app.appId
  ssoApi.access(app.appId)
    .then(r => {
      var t = localStorage.getItem('token')
      window.open('/api/sso/go/' + app.appId + '?token=' + encodeURIComponent(t), '_blank')
    })
    .finally(() => { accessingId.value = null })
}
</script>

<style scoped>
.app-icon { width:72px;height:72px;border-radius:16px;background:linear-gradient(135deg,#ecf5ff,#d9ecff);display:flex;align-items:center;justify-content:center;margin:0 auto; }
</style>
