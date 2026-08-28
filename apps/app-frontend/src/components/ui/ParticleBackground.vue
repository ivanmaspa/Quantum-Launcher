<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const canvas = ref(null)
let ctx = null
let raf = 0
let particles = []
let w = 0
let h = 0
let dpr = 1
const mouse = { x: -9999, y: -9999 }

const COLORS = ['#7fe8a8', '#4ee089', '#2bbf77', '#a7f3c6']

function resize() {
  if (!canvas.value) return
  dpr = window.devicePixelRatio || 1
  w = canvas.value.clientWidth
  h = canvas.value.clientHeight
  canvas.value.width = w * dpr
  canvas.value.height = h * dpr
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function spawn() {
  const count = Math.min(46, Math.floor((w * h) / 26000))
  particles = []
  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * w,
      y: Math.random() * h,
      vx: (Math.random() - 0.5) * 0.12,
      vy: -0.08 - Math.random() * 0.22,
      size: 1.5 + Math.random() * 3.2,
      rot: Math.random() * Math.PI,
      vr: (Math.random() - 0.5) * 0.01,
      color: COLORS[(Math.random() * COLORS.length) | 0],
      a: 0.18 + Math.random() * 0.4,
    })
  }
}

function diamond(x, y, s, rot) {
  ctx.save()
  ctx.translate(x, y)
  ctx.rotate(rot)
  ctx.beginPath()
  ctx.moveTo(0, -s)
  ctx.lineTo(s * 0.62, 0)
  ctx.lineTo(0, s)
  ctx.lineTo(-s * 0.62, 0)
  ctx.closePath()
  ctx.fill()
  ctx.restore()
}

function frame() {
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  for (const p of particles) {
    // лёгкое искажение траектории от курсора
    const dx = p.x - mouse.x
    const dy = p.y - mouse.y
    const d2 = dx * dx + dy * dy
    if (d2 < 26000) {
      const f = (1 - d2 / 26000) * 0.9
      const d = Math.sqrt(d2) || 1
      p.vx += (dx / d) * f * 0.02
      p.vy += (dy / d) * f * 0.02
    }
    p.x += p.vx
    p.y += p.vy
    p.rot += p.vr
    // лёгкое возвращение к базовому дрейфу
    p.vx *= 0.992
    p.vy = p.vy * 0.992 - 0.0006

    if (p.y < -10) {
      p.y = h + 10
      p.x = Math.random() * w
    }
    if (p.x < -10) p.x = w + 10
    if (p.x > w + 10) p.x = -10

    ctx.globalAlpha = p.a
    ctx.fillStyle = p.color
    ctx.shadowColor = p.color
    ctx.shadowBlur = 6
    diamond(p.x, p.y, p.size, p.rot)
  }
  ctx.globalAlpha = 1
  ctx.shadowBlur = 0
  raf = requestAnimationFrame(frame)
}

function onMove(e) {
  const r = canvas.value.getBoundingClientRect()
  mouse.x = e.clientX - r.left
  mouse.y = e.clientY - r.top
}
function onLeave() {
  mouse.x = -9999
  mouse.y = -9999
}

onMounted(() => {
  ctx = canvas.value.getContext('2d')
  resize()
  spawn()
  window.addEventListener('resize', () => {
    resize()
    spawn()
  })
  window.addEventListener('mousemove', onMove)
  canvas.value.addEventListener('mouseleave', onLeave)
  raf = requestAnimationFrame(frame)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('mousemove', onMove)
})
</script>

<template>
  <canvas ref="canvas" class="ql-particles" />
</template>

<style scoped>
.ql-particles {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
  opacity: 0.9;
}
</style>
