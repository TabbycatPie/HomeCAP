<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
      <h2 style="margin:0">🔑 权限分配</h2>
    </div>

    <el-card>
      <el-table :data="users" stripe v-loading="loading">
        <el-table-column prop="username" label="用户名" width="150">
          <template #default="{ row }">
            <strong>{{ row.username }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="130" />
        <el-table-column label="已授权应用" min-width="300">
          <template #default="{ row }">
            <span v-if="userPermMap[row.id] && userPermMap[row.id].length">
              <span
                v-for="a in userPermMap[row.id]"
                :key="a.appId"
                style="display:inline-flex;align-items:center;gap:4px;margin:2px 8px 2px 0;padding:4px 8px;background:#f0f5ff;border-radius:6px;font-size:13px"
              >
                <AppIcon :app-type="a.appType" :size="20" />
                <span>{{ a.appName }}</span>
              </span>
            </span>
            <span v-else style="color:#c0c4cc;font-size:13px">暂无权限</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑权限弹窗 -->
    <el-dialog v-model="editVisible" title="编辑权限" width="500px">
      <div v-if="editingUser" style="margin-bottom:16px;display:flex;align-items:center;gap:10px">
        <strong>用户：</strong>{{ editingUser.username }}
        <span style="color:#909399;font-size:13px">（{{ editingUser.nickname }}）</span>
      </div>

      <el-checkbox-group v-model="editingAppIds">
        <div style="display:flex;flex-wrap:wrap;gap:12px">
          <div v-for="app in allApps" :key="app.id" style="width:45%">
            <el-checkbox :label="app.id" :value="app.id" :disabled="app.status !== 'enabled'">
              <span style="display:inline-flex;align-items:center;gap:6px">
                <AppIcon :app-type="app.appType" :icon-url="app.iconUrl" :size="20" />
                {{ app.name }}
              </span>
            </el-checkbox>
          </div>
        </div>
      </el-checkbox-group>

      <el-alert title="勾选即授权，取消勾选即撤销权限。授权时会自动在目标应用创建用户账号" type="info" show-icon :closable="false" style="margin-top:16px" />

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminUserApi, adminAppApi, adminPermApi } from '@/api'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'

const loading = ref(false)
const saving = ref(false)
const users = ref([])
const allApps = ref([])
const userPermMap = ref({})

const editVisible = ref(false)
const editingUser = ref(null)
const editingAppIds = ref([])

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    const [u, a] = await Promise.all([adminUserApi.list(), adminAppApi.list()])
    users.value = u
    allApps.value = a
    // 为每个用户加载权限
    for (const user of u) {
      try {
        const perms = await adminPermApi.getByUser(user.id)
        userPermMap.value[user.id] = perms
          .filter(p => p.status === 'enabled')
          .map(p => {
            const app = a.find(x => x.id === p.appId)
            return { ...p, appName: app?.name || '?', appType: app?.appType || '' }
          })
      } catch { userPermMap.value[user.id] = [] }
    }
  } finally { loading.value = false }
}

function openEdit(row) {
  editingUser.value = row
  editingAppIds.value = (userPermMap.value[row.id] || []).map(p => p.appId)
  editVisible.value = true
}

async function handleSave() {
  if (!editingUser.value) return
  saving.value = true
  try {
    const userId = editingUser.value.id
    const current = (userPermMap.value[userId] || []).map(p => p.appId)
    const selected = editingAppIds.value

    const toAdd = selected.filter(id => !current.includes(id))
    const toRemove = current.filter(id => !selected.includes(id))

    for (const appId of toAdd) {
      try { await adminPermApi.grant({ userId, appId }) }
      catch (e) { console.error('授权失败', appId, e) }
    }
    for (const appId of toRemove) {
      try { await adminPermApi.revoke({ userId, appId }) }
      catch (e) { console.error('撤销失败', appId, e) }
    }

    ElMessage.success('权限已保存')
    editVisible.value = false
    fetchData()
  } finally { saving.value = false }
}
</script>
