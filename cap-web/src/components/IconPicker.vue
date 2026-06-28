<template>
  <el-popover trigger="click" placement="bottom" :width="320">
    <template #reference>
      <el-button size="small" class="icon-pick-btn">
        <span v-if="selected" v-html="previewSvg" style="display:inline-flex;align-items:center;gap:6px" />
        <span v-else>选择图标</span>
        <el-icon style="margin-left:4px"><ArrowDown /></el-icon>
      </el-button>
    </template>

    <div class="icon-grid">
      <div
        v-for="icon in icons"
        :key="icon.name"
        class="icon-item"
        :class="{ active: modelValue === icon.name }"
        @click="select(icon.name)"
        :title="icon.label"
      >
        <img :src="icon.url" v-if="icon.url" style="width:24px;height:24px;object-fit:contain" />
        <span v-else v-html="icon.svg" style="display:flex" />
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  appType: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const selected = computed(() => icons.find(i => i.name === props.modelValue))

const previewSvg = computed(() => selected.value?.svg || '')

function select(name) {
  emit('update:modelValue', name)
}

// === 可用图标列表 ===
const icons = [
  // 品牌应用图标
  { name: 'brand:nextcloud', label: 'Nextcloud', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><circle cx="12" cy="12" r="10" fill="#0082c9"/><circle cx="12" cy="12" r="7" fill="#fff"/><path d="M8.5 9a3 3 0 100 6 3 3 0 010-6zm7 0a3 3 0 100 6 3 3 0 010-6z" fill="#0082c9"/></svg>` },
  { name: 'brand:jellyfin', label: 'Jellyfin', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><path d="M12 3L21 21H3L12 3z" fill="#00a4dc"/><path d="M12 7l5 10H7l5-10z" fill="#fff"/><path d="M12 10l3 5H9l3-5z" fill="#00a4dc"/></svg>` },
  { name: 'brand:navidrome', label: 'Navidrome', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><circle cx="12" cy="12" r="10" fill="#6c5ce7"/><circle cx="12" cy="12" r="5.5" fill="#fff"/><path d="M12 6a6 6 0 00-6 6 6 6 0 006 6V6z" fill="#6c5ce7"/></svg>` },
  { name: 'brand:calibre', label: 'Calibre', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><rect x="3" y="4" width="14" height="16" rx="2" fill="#c0392b"/><rect x="5" y="6" width="10" height="12" rx="1" fill="#fff"/><line x1="7" y1="9" x2="14" y2="9" stroke="#ddd" stroke-width="1"/><line x1="7" y1="11" x2="12" y2="11" stroke="#ddd" stroke-width="1"/><line x1="7" y1="13" x2="13" y2="13" stroke="#ddd" stroke-width="1"/><rect x="17" y="7" width="5" height="13" rx="1" fill="#c0392b"/></svg>` },
  { name: 'brand:pve', label: 'Proxmox', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><rect x="3" y="4" width="18" height="16" rx="2" fill="#e67e22"/><rect x="6" y="7" width="12" height="10" rx="1" fill="#fff"/><path d="M9 10h6M9 13h4M9 16h3" stroke="#e67e22" stroke-width="1.5" stroke-linecap="round"/></svg>` },
  { name: 'brand:xboard', label: 'xBoard', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><rect x="2" y="4" width="20" height="16" rx="2" fill="#34495e"/><rect x="5" y="7" width="14" height="10" rx="1" fill="#ecf0f1"/><rect x="7" y="9" width="6" height="1" rx=".5" fill="#7f8c8d"/><rect x="7" y="11" width="4" height="1" rx=".5" fill="#7f8c8d"/><rect x="7" y="13" width="8" height="2" rx="1" fill="#27ae60"/><circle cx="16" cy="9.5" r="1.5" fill="#e74c3c"/></svg>` },
  { name: 'brand:plex', label: 'Plex', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><path d="M4 4l8 8-8 8z" fill="#e5a00d"/><path d="M12 4l8 8-8 8z" fill="#282828"/></svg>` },
  { name: 'brand:emby', label: 'Emby', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><rect x="2" y="4" width="20" height="16" rx="3" fill="#52b54b"/><text x="12" y="16" text-anchor="middle" fill="#fff" font-size="10" font-weight="bold" font-family="Arial">E</text></svg>` },
  { name: 'brand:portainer', label: 'Portainer', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><rect x="2" y="2" width="20" height="20" rx="4" fill="#13bef9"/><path d="M7 8h3v8H7zM14 8h3v8h-3z" fill="#fff"/></svg>` },
  { name: 'brand:qbittorrent', label: 'qBittorrent', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><circle cx="12" cy="12" r="10" fill="#2b669a"/><path d="M12 5a7 7 0 100 14 7 7 0 000-14zm-1 3h2v5h-2V8zm0 7h2v1h-2v-1z" fill="#fff"/></svg>` },
  { name: 'brand:transmission', label: 'Transmission', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><circle cx="12" cy="12" r="10" fill="#c62828"/><path d="M7 7l10 5-10 5V7z" fill="#fff"/></svg>` },
  // 通用图标
  { name: '', label: '品牌默认', svg: `<svg viewBox="0 0 24 24" width="24" height="24"><rect x="3" y="3" width="18" height="18" rx="3" fill="#e0e0e0"/><rect x="6" y="6" width="5" height="5" rx="1" fill="#bdbdbd"/><rect x="13" y="6" width="5" height="5" rx="1" fill="#bdbdbd"/><rect x="6" y="13" width="5" height="5" rx="1" fill="#bdbdbd"/><rect x="13" y="13" width="5" height="5" rx="1" fill="#bdbdbd"/></svg>` },
  { name: 'cloud', label: '云存储', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#5b9bd5" stroke-width="2"><path d="M6.5 17a5 5 0 1 1 0-10 5.5 5.5 0 0 1 10.5 1.5A4 4 0 1 1 18 17H6.5"/></svg>` },
  { name: 'server', label: '服务器', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#2ecc71" stroke-width="2"><rect x="3" y="4" width="18" height="8" rx="2"/><rect x="3" y="12" width="18" height="8" rx="2"/><circle cx="7" cy="8" r="1" fill="#2ecc71"/><circle cx="7" cy="16" r="1" fill="#2ecc71"/></svg>` },
  { name: 'music', label: '音乐', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#e74c3c" stroke-width="2"><circle cx="8" cy="18" r="3"/><path d="M11 18V4l9-1v11"/><circle cx="18" cy="17" r="3" fill="#e74c3c"/></svg>` },
  { name: 'book', label: '书籍', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#8e44ad" stroke-width="2"><path d="M4 6h16"/><path d="M4 12h16"/><path d="M4 18h12"/><rect x="4" y="2" width="16" height="20" rx="2"/></svg>` },
  { name: 'movie', label: '影视', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#e67e22" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M8 4v16M16 4v16M2 8h20M2 12h20"/></svg>` },
  { name: 'terminal', label: '终端', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#2c3e50" stroke-width="2"><path d="M4 17l6-6-6-6m8 12h8"/></svg>` },
  { name: 'shield', label: '安全', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#e74c3c" stroke-width="2"><path d="M12 3a12 12 0 0 0 8.5 3 12 12 0 0 1-8.5 15A12 12 0 0 1 3.5 6 12 12 0 0 0 12 3"/></svg>` },
  { name: 'database', label: '数据库', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#3498db" stroke-width="2"><ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v6c0 1.66 3.58 3 8 3s8-1.34 8-3V6M4 12v6c0 1.66 3.58 3 8 3s8-1.34 8-3v-6"/></svg>` },
  { name: 'photo', label: '相册', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#1abc9c" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"/><circle cx="9" cy="10" r="2" fill="#1abc9c"/><path d="m22 18-6-6-4 4-4-4-6 6"/></svg>` },
  { name: 'dashboard', label: '仪表盘', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#9b59b6" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 8V3M16 12h5M8 12H3"/></svg>` },
  { name: 'lock', label: '密码', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#f39c12" stroke-width="2"><rect x="6" y="10" width="12" height="10" rx="2"/><circle cx="12" cy="16" r="1" fill="#f39c12"/><path d="M8 10V7a4 4 0 1 1 8 0v3"/></svg>` },
  { name: 'link', label: '外链', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#7f8c8d" stroke-width="2"><path d="M10 14a3.5 3.5 0 0 0 5 0l4-4a3.5 3.5 0 0 0-5-5l-2 2"/><path d="M14 10a3.5 3.5 0 0 0-5 0l-4 4a3.5 3.5 0 0 0 5 5l2-2"/></svg>` },
  { name: 'globe', label: '地球', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#2980b9" stroke-width="2"><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="3" ry="9"/><path d="M3 12h18"/></svg>` },
  { name: 'home', label: '主页', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#34495e" stroke-width="2"><path d="M3 12l9-9 9 9"/><path d="M5 10v10h14V10"/></svg>` },
  { name: 'download', label: '下载', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#27ae60" stroke-width="2"><path d="M12 4v12m0 0l-4-4m4 4l4-4"/><path d="M4 20h16"/></svg>` },
  { name: 'monitor', label: '监控', svg: `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#e74c3c" stroke-width="2"><rect x="3" y="3" width="18" height="12" rx="2"/><path d="M8 21h8M12 15v6"/></svg>` },
]
</script>

<style scoped>
.icon-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 260px;
  overflow-y: auto;
  padding: 4px;
}
.icon-item {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid transparent;
}
.icon-item:hover { background: #f0f5ff; }
.icon-item.active { border-color: #409eff; background: #ecf5ff; }
</style>
