<template>
  <img v-if="iconUrl && !iconError" :src="iconUrl" :style="imgStyle" @error="iconError = true" />
  <span v-else v-html="displaySvg" :style="iconStyle" />
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  appType: { type: String, default: '' },
  iconUrl: { type: String, default: '' },
  iconName: { type: String, default: '' },
  size: { type: Number, default: 48 },
})

const iconError = ref(false)
const iconStyle = computed(() => ({
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: props.size + 'px',
  height: props.size + 'px',
  flexShrink: 0,
}))
const imgStyle = computed(() => ({
  ...iconStyle.value,
  objectFit: 'contain',
}))

// 图标 SVG 映射
const iconSvgs = {
  'brand:nextcloud': `<svg viewBox="0 0 24 24" width="s" height="s"><circle cx="12" cy="12" r="10" fill="#0082c9"/><circle cx="12" cy="12" r="7" fill="#fff"/><path d="M8.5 9a3 3 0 100 6 3 3 0 010-6zm7 0a3 3 0 100 6 3 3 0 010-6z" fill="#0082c9"/></svg>`,
  'brand:jellyfin': `<svg viewBox="0 0 24 24" width="s" height="s"><path d="M12 3L21 21H3L12 3z" fill="#00a4dc"/><path d="M12 7l5 10H7l5-10z" fill="#fff"/><path d="M12 10l3 5H9l3-5z" fill="#00a4dc"/></svg>`,
  'brand:navidrome': `<svg viewBox="0 0 24 24" width="s" height="s"><circle cx="12" cy="12" r="10" fill="#6c5ce7"/><circle cx="12" cy="12" r="5.5" fill="#fff"/><path d="M12 6a6 6 0 00-6 6 6 6 0 006 6V6z" fill="#6c5ce7"/></svg>`,
  'brand:calibre': `<svg viewBox="0 0 24 24" width="s" height="s"><rect x="3" y="4" width="14" height="16" rx="2" fill="#c0392b"/><rect x="5" y="6" width="10" height="12" rx="1" fill="#fff"/><line x1="7" y1="9" x2="14" y2="9" stroke="#ddd" stroke-width="1"/><line x1="7" y1="11" x2="12" y2="11" stroke="#ddd" stroke-width="1"/><line x1="7" y1="13" x2="13" y2="13" stroke="#ddd" stroke-width="1"/><rect x="17" y="7" width="5" height="13" rx="1" fill="#c0392b"/></svg>`,
  'brand:pve': `<svg viewBox="0 0 24 24" width="s" height="s"><rect x="3" y="4" width="18" height="16" rx="2" fill="#e67e22"/><rect x="6" y="7" width="12" height="10" rx="1" fill="#fff"/><path d="M9 10h6M9 13h4M9 16h3" stroke="#e67e22" stroke-width="1.5" stroke-linecap="round"/></svg>`,
  'brand:xboard': `<svg viewBox="0 0 24 24" width="s" height="s"><rect x="2" y="4" width="20" height="16" rx="2" fill="#34495e"/><rect x="5" y="7" width="14" height="10" rx="1" fill="#ecf0f1"/><rect x="7" y="9" width="6" height="1" rx=".5" fill="#7f8c8d"/><rect x="7" y="11" width="4" height="1" rx=".5" fill="#7f8c8d"/><rect x="7" y="13" width="8" height="2" rx="1" fill="#27ae60"/><circle cx="16" cy="9.5" r="1.5" fill="#e74c3c"/></svg>`,
  'cloud': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#5b9bd5" stroke-width="2"><path d="M6.5 17a5 5 0 1 1 0-10 5.5 5.5 0 0 1 10.5 1.5A4 4 0 1 1 18 17H6.5"/></svg>`,
  'server': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#2ecc71" stroke-width="2"><rect x="3" y="4" width="18" height="8" rx="2"/><rect x="3" y="12" width="18" height="8" rx="2"/><circle cx="7" cy="8" r="1" fill="#2ecc71"/><circle cx="7" cy="16" r="1" fill="#2ecc71"/></svg>`,
  'music': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#e74c3c" stroke-width="2"><circle cx="8" cy="18" r="3"/><path d="M11 18V4l9-1v11"/><circle cx="18" cy="17" r="3" fill="#e74c3c"/></svg>`,
  'book': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#8e44ad" stroke-width="2"><path d="M4 6h16M4 12h16M4 18h12"/><rect x="4" y="2" width="16" height="20" rx="2"/></svg>`,
  'movie': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#e67e22" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M8 4v16M16 4v16M2 8h20M2 12h20"/></svg>`,
  'terminal': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#2c3e50" stroke-width="2"><path d="M4 17l6-6-6-6m8 12h8"/></svg>`,
  'shield': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#e74c3c" stroke-width="2"><path d="M12 3a12 12 0 0 0 8.5 3 12 12 0 0 1-8.5 15 12 12 0 0 1-3.5-3"/></svg>`,
  'database': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#3498db" stroke-width="2"><ellipse cx="12" cy="6" rx="8" ry="3"/><path d="M4 6v6c0 1.66 3.58 3 8 3s8-1.34 8-3V6M4 12v6c0 1.66 3.58 3 8 3s8-1.34 8-3v-6"/></svg>`,
  'photo': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#1abc9c" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"/><circle cx="9" cy="10" r="2" fill="#1abc9c"/><path d="m22 18-6-6-4 4-4-4-6 6"/></svg>`,
  'dashboard': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#9b59b6" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 8V3M16 12h5M8 12H3"/></svg>`,
  'lock': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#f39c12" stroke-width="2"><rect x="6" y="10" width="12" height="10" rx="2"/><circle cx="12" cy="16" r="1" fill="#f39c12"/><path d="M8 10V7a4 4 0 1 1 8 0v3"/></svg>`,
  'link': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#7f8c8d" stroke-width="2"><path d="M10 14a3.5 3.5 0 0 0 5 0l4-4a3.5 3.5 0 0 0-5-5l-2 2M14 10a3.5 3.5 0 0 0-5 0l-4 4a3.5 3.5 0 0 0 5 5l2-2"/></svg>`,
  'globe': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#2980b9" stroke-width="2"><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="3" ry="9"/><path d="M3 12h18"/></svg>`,
  'home': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#34495e" stroke-width="2"><path d="M3 12l9-9 9 9M5 10v10h14V10"/></svg>`,
  'download': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#27ae60" stroke-width="2"><path d="M12 4v12m0 0l-4-4m4 4l4-4M4 20h16"/></svg>`,
  'monitor': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#e74c3c" stroke-width="2"><rect x="3" y="3" width="18" height="12" rx="2"/><path d="M8 21h8M12 15v6"/></svg>`,
  'forward': `<svg viewBox="0 0 24 24" width="s" height="s" fill="none" stroke="#95a5a6" stroke-width="2"><path d="M10 8l4 4-4 4M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>`,
}

const displaySvg = computed(() => {
  const s = props.size
  // 优先用选中的图标
  if (props.iconName && iconSvgs[props.iconName]) {
    return iconSvgs[props.iconName].replace(/"s"/g, '"' + s + '"')
  }
  // 品牌默认图标
  const brandKey = 'brand:' + props.appType
  if (iconSvgs[brandKey]) {
    return iconSvgs[brandKey].replace(/"s"/g, '"' + s + '"')
  }
  // 回退到 appType 直接匹配
  if (iconSvgs[props.appType]) {
    return iconSvgs[props.appType].replace(/"s"/g, '"' + s + '"')
  }
  return ''
})
</script>
