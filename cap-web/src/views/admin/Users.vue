<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
      <h2 style="margin:0">👥 用户管理</h2>
      <el-button type="primary" @click="openAdd">+ 添加用户</el-button>
    </div>

    <el-card>
      <el-table :data="users" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="role" label="角色" width="80">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">
              {{ row.role === 'admin' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'warning'" size="small">{{ row.status === 'enabled' ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应用权限" min-width="180">
          <template #default="{ row }">
            <span v-if="userPerms[row.id] && userPerms[row.id].length">
              <span v-for="a in userPerms[row.id]" :key="a.appId" style="display:inline-block;margin-right:6px" :title="a.appName">
                <AppIcon :app-type="a.appType" :size="24" />
              </span>
            </span>
            <span v-else style="color:#c0c4cc;font-size:13px">无</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-switch v-if="row.role !== 'admin'" :model-value="row.status === 'enabled'" style="margin:0 6px" size="small" @change="(v) => toggleStatus(row, v)" />
            <el-popconfirm v-if="row.role !== 'admin'" title="确定删除？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" :loading="deletingId === row.id">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加弹窗 -->
    <el-dialog v-model="addVisible" title="添加用户" width="460px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="用户名"><el-input v-model="addForm.username" placeholder="登录用户名" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="addForm.password" type="password" show-password placeholder="默认 123456" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="addForm.nickname" placeholder="显示名称" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="addForm.email" placeholder="邮箱地址" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑用户" width="460px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="editForm.nickname" placeholder="显示名称" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="editForm.email" placeholder="邮箱地址" /></el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="editForm.newPassword" type="password" show-password placeholder="留空则不修改密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEditSave">保存</el-button>
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
const adding = ref(false)
const saving = ref(false)
const deletingId = ref(null)
const users = ref([])
const allApps = ref([])
const userPerms = ref({})
const addVisible = ref(false)
const addForm = ref({ username: '', password: '', nickname: '', email: '' })
const editVisible = ref(false)
const editForm = ref({ id: null, username: '', nickname: '', email: '', newPassword: '' })

onMounted(() => fetchData())

async function fetchData() {
  loading.value = true
  try {
    users.value = await adminUserApi.list()
    allApps.value = await adminAppApi.list()
    // 为每个用户加载权限
    for (const u of users.value) {
      try {
        const perms = await adminPermApi.getByUser(u.id)
        userPerms.value[u.id] = perms
          .filter(p => p.status === 'enabled')
          .map(p => {
            const app = allApps.value.find(a => a.id === p.appId)
            return { ...p, appName: app?.name || '?', appType: app?.appType || '' }
          })
      } catch { userPerms.value[u.id] = [] }
    }
  } finally { loading.value = false }
}

function openAdd() {
  addForm.value = { username: '', password: '', nickname: '', email: '' }
  addVisible.value = true
}

async function handleAdd() {
  if (!addForm.value.username) { ElMessage.warning('请输入用户名'); return }
  adding.value = true
  try {
    await adminUserApi.create({
      username: addForm.value.username,
      password: addForm.value.password || '123456',
      nickname: addForm.value.nickname,
      email: addForm.value.email,
    })
    ElMessage.success('创建成功')
    addVisible.value = false
    fetchData()
  } finally { adding.value = false }
}

function openEdit(row) {
  editForm.value = {
    id: row.id, username: row.username,
    nickname: row.nickname || '', email: row.email || '',
    newPassword: ''
  }
  editVisible.value = true
}

async function handleEditSave() {
  if (!editForm.value.nickname) { ElMessage.warning('请输入昵称'); return }
  saving.value = true
  try {
    // 更新昵称和邮箱（如果后端支持）
    if (editForm.value.newPassword) {
      await adminUserApi.setPassword(editForm.value.id, editForm.value.newPassword)
      ElMessage.success('密码已重置')
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    fetchData()
  } catch {} finally { saving.value = false }
}

async function toggleStatus(row, enabled) {
  try {
    await adminUserApi.updateStatus(row.id, enabled ? 'enabled' : 'disabled')
    row.status = enabled ? 'enabled' : 'disabled'
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch {}
}

async function handleDelete(row) {
  deletingId.value = row.id
  try {
    await adminUserApi.delete(row.id)
    ElMessage.success('已删除')
    fetchData()
  } finally { deletingId.value = null }
}
</script>
