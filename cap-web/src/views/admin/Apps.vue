<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
      <h2 style="margin:0">⚙️ 应用管理</h2>
      <el-button type="primary" @click="openAdd">+ 添加应用</el-button>
    </div>

    <el-card>
      <el-table :data="apps" stripe v-loading="loading">
        <el-table-column label="" width="50" align="center">
          <template #default="{ row }">
            <AppIcon :app-type="row.appType" :icon-url="row.iconUrl" :icon-name="row.configJson ? (JSON.parse(row.configJson||'{}').iconName || '') : ''" :size="28" />
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="45" />
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="appType" label="类型" width="80" />
        <el-table-column label="跳转" width="70">
          <template #default="{ row }">
            <el-tag :type="row.redirectMode === 'direct' ? 'warning' : 'primary'" size="small">
              {{ row.redirectMode === 'direct' ? '直跳' : 'SSO' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publicUrl" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="65">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'warning'" size="small">{{ row.status === 'enabled' ? '开' : '关' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :loading="testingId === row.id" @click="handleTest(row)">测试</el-button>
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-switch :model-value="row.status === 'enabled'" style="margin-left:6px" size="small"
              @change="(v) => toggleStatus(row, v)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加弹窗 -->
    <el-dialog v-model="addVisible" title="添加应用" width="580px">
      <el-form :model="addForm" label-width="110px">
        <el-form-item label="应用名称"><el-input v-model="addForm.name" placeholder="如: Nextcloud" /></el-form-item>
        <el-form-item label="应用类型">
          <el-select v-model="addForm.appType" placeholder="选择插件类型" style="width:100%">
            <el-option v-for="p in pluginTypes" :key="p.appType" :label="p.displayName" :value="p.appType" />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转模式">
          <el-radio-group v-model="addForm.redirectMode">
            <el-radio value="plugin">插件 SSO（走认证流程）</el-radio>
            <el-radio value="direct">直接跳转（直达目标页）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公网地址"><el-input v-model="addForm.publicUrl" placeholder="用户浏览器访问的地址" /></el-form-item>
        <el-form-item label="API 地址"><el-input v-model="addForm.baseUrl" placeholder="后端用内网地址（插件SSO需填）" /></el-form-item>
        <el-form-item label="管理员账号"><el-input v-model="addForm.adminUser" placeholder="Jellyfin 可填 API Key" /></el-form-item>
        <el-form-item label="管理员密码"><el-input v-model="addForm.adminPassword" type="password" show-password placeholder="必填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑应用" width="580px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="应用名称"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="跳转模式">
          <el-radio-group v-model="editForm.redirectMode">
            <el-radio value="plugin">插件 SSO</el-radio>
            <el-radio value="direct">直接跳转</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公网地址"><el-input v-model="editForm.publicUrl" placeholder="用户浏览器访问的地址" /></el-form-item>
        <el-form-item label="API 地址"><el-input v-model="editForm.baseUrl" placeholder="插件 SSO 时使用的内网地址" /></el-form-item>
        <el-form-item label="图标">
          <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
            <AppIcon v-if="editForm.iconUrl" :app-type="editForm.appType" :icon-url="editForm.iconUrl" :size="36" />
            <IconPicker v-model="editForm.iconName" :app-type="editForm.appType" />
            <el-upload :show-file-list="false" :before-upload="handleIconUpload" action="" accept="image/*">
              <el-button size="small" type="primary" :loading="uploading">{{ editForm.iconUrl ? '更换' : '上传' }}</el-button>
            </el-upload>
            <el-input v-model="editForm.iconUrl" placeholder="或输入图标 URL" style="width:200px" />
          </div>
        </el-form-item>
        <el-form-item label="管理员账号"><el-input v-model="editForm.adminUser" /></el-form-item>
        <el-form-item label="管理员密码"><el-input v-model="editForm.adminPassword" type="password" show-password placeholder="留空则不修改" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminAppApi, uploadApi } from '@/api'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import IconPicker from '@/components/IconPicker.vue'

const loading = ref(false)
const testingId = ref(null)
const adding = ref(false)
const saving = ref(false)
const uploading = ref(false)
const apps = ref([])
const pluginTypes = ref([])

const addVisible = ref(false)
const addForm = reactive({ name: '', appType: '', redirectMode: 'plugin', baseUrl: '', publicUrl: '', adminUser: '', adminPassword: '' })

const editVisible = ref(false)
const editForm = reactive({ id: null, name: '', appType: '', redirectMode: 'plugin', baseUrl: '', publicUrl: '', iconUrl: '', iconName: '', adminUser: '', adminPassword: '' })
const editConfig = ref({})

onMounted(() => { fetchApps(); fetchPluginTypes() })

async function fetchApps() {
  loading.value = true
  try { apps.value = await adminAppApi.list() } finally { loading.value = false }
}

async function fetchPluginTypes() {
  try { pluginTypes.value = await adminAppApi.listPluginTypes() } catch { pluginTypes.value = [] }
}

function openAdd() {
  Object.assign(addForm, { name: '', appType: '', redirectMode: 'plugin', baseUrl: '', publicUrl: '', adminUser: '', adminPassword: '' })
  addVisible.value = true
}

async function handleAdd() {
  if (!addForm.name) { ElMessage.warning('请填写应用名称'); return }
  if (addForm.redirectMode === 'direct' && !addForm.publicUrl) {
    ElMessage.warning('直跳模式需要填写公网地址'); return
  }
  adding.value = true
  try {
    const config = {}
    if (addForm.adminUser) config.adminUser = addForm.adminUser
    if (addForm.adminPassword) config.adminPassword = addForm.adminPassword
    await adminAppApi.create({
      name: addForm.name, appType: addForm.appType, redirectMode: addForm.redirectMode,
      baseUrl: addForm.baseUrl || '', publicUrl: addForm.publicUrl || '', config
    })
    ElMessage.success('添加成功')
    addVisible.value = false
    fetchApps()
  } catch {} finally { adding.value = false }
}

function openEdit(row) {
  let cfg = {}
  try { cfg = JSON.parse(row.configJson || '{}') } catch {}
  editConfig.value = cfg
  Object.assign(editForm, {
    id: row.id,
    name: row.name || '',
    appType: row.appType,
    redirectMode: row.redirectMode || 'plugin',
    baseUrl: row.baseUrl || '',
    publicUrl: row.publicUrl || '',
    iconUrl: row.iconUrl || '',
    iconName: cfg.iconName || '',
    adminUser: cfg.adminUser || cfg.apiKey || '',
    adminPassword: ''
  })
  editVisible.value = true
}

async function handleEditSave() {
  if (!editForm.name) { ElMessage.warning('应用名称不能为空'); return }
  if (editForm.redirectMode === 'direct' && !editForm.publicUrl) {
    ElMessage.warning('直跳模式需要填写公网地址'); return
  }
  saving.value = true
  try {
    const config = { ...editConfig.value }
    config.adminUser = editForm.adminUser || undefined
    if (editForm.adminPassword) config.adminPassword = editForm.adminPassword
    if (editForm.iconName) config.iconName = editForm.iconName
    // 清理空值
    if (!config.adminUser) delete config.adminUser
    if (!config.iconName) delete config.iconName
    const payload = {
      name: editForm.name,
      baseUrl: editForm.baseUrl || '',
      redirectMode: editForm.redirectMode,
      publicUrl: editForm.publicUrl || '',
      iconUrl: editForm.iconUrl || null,
      config
    }
    await adminAppApi.update(editForm.id, payload)
    ElMessage.success('保存成功')
    editVisible.value = false
    fetchApps()
  } catch {} finally { saving.value = false }
}

async function handleIconUpload(file) {
  uploading.value = true
  try {
    const res = await uploadApi.appIcon(editForm.id, file)
    editForm.iconUrl = res.iconUrl
    ElMessage.success('图标上传成功')
  } catch {} finally { uploading.value = false }
  return false
}

async function handleTest(row) {
  testingId.value = row.id
  try {
    const res = await adminAppApi.testConnection(row.id)
    const url = row.publicUrl || row.baseUrl
    if (res.success) {
      ElMessage({
        message: res.message || '连接成功',
        type: 'success',
        duration: 4000,
      })
      // 连接成功同时打开目标地址
      if (url) {
        setTimeout(() => window.open(url, '_blank'), 500)
        ElMessage({ message: '正在打开: ' + url, type: 'info', duration: 2500 })
      }
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch {} finally { testingId.value = null }
}

async function toggleStatus(row, enabled) {
  try {
    await adminAppApi.updateStatus(row.id, enabled ? 'enabled' : 'disabled')
    row.status = enabled ? 'enabled' : 'disabled'
    ElMessage.success(enabled ? '已启用' : '已禁用')
  } catch {}
}
</script>
