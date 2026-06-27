<script setup>
import { computed, ref } from 'vue'

const files = ref([])
const loading = ref(false)
const error = ref('')
const previewResp = ref(null)
const ratingResp = ref(null)
const activeBattle = ref(0)
const sortState = ref({})

const PLAYER_LABELS = {
  nickname: '玩家', clan: '战队', tank_name: '车辆', tank_tier: '等级',
  tank_type: '坦克类型', tank_nation: '国家', alpha_damage: '炮伤',
  rating: '评分', survived_label: '存活', kills: '击杀',
  damage_dealt: '伤害', potential_damage: '潜在伤害',
  potential_damage_supplement: '补增伤害', potential_damage_detail: '潜在明细',
  damage_assisted: '协助伤害', damage_received: '损失血量',
  damage_blocked: '格挡', n_shots: '发射', n_hits_dealt: '命中',
  n_penetrations_dealt: '击穿', n_hits_received: '被命中',
  n_penetrations_received: '被击穿', n_enemies_damaged: '击伤',
  platoon_label: '排', rank: '军阶', tank_id: '车辆ID', account_id: '账号ID'
}

const RATING_LABELS = {
  nickname: '玩家', clan: '战队', battles: '场次', wins: '胜场',
  win_rate: '胜率%', rating: 'Rating', kast: 'KAST%',
  contribution: '贡献率%', influence: '影响力', damage_avg: '均伤',
  potential_damage_avg: '潜在均伤', potential_damage_supplement_avg: '场均补增',
  kills: '人头', kills_avg: '场均人头', account_id: '账号ID'
}

const playerCols = computed(() => previewResp.value?.playerColumns || [])
const ratingCols = computed(() => ratingResp.value?.ratingColumns || [])
const battles = computed(() => previewResp.value?.battles || [])
const currentBattle = computed(() => battles.value[activeBattle.value] || null)
const duplicateRows = computed(() => [
  ...tagRows('扩展字段', previewResp.value?.duplicates),
  ...tagRows('Rating', ratingResp.value?.duplicates)
])
const failureRows = computed(() => [
  ...tagRows('扩展字段', previewResp.value?.failures),
  ...tagRows('Rating', ratingResp.value?.failures)
])

function tagRows(scope, rows) {
  return (rows || []).map(r => ({ scope, name: r[0], detail: r[1] }))
}

function addFiles(list) {
  const picked = Array.from(list || []).filter(f => f.name.toLowerCase().endsWith('.wotbreplay'))
  const byKey = new Map(files.value.map(f => [fileKey(f), f]))
  picked.forEach(f => byKey.set(fileKey(f), f))
  files.value = Array.from(byKey.values()).sort((a, b) => displayName(a).localeCompare(displayName(b)))
  previewResp.value = null
  ratingResp.value = null
  activeBattle.value = 0
  error.value = ''
}

function fileKey(f) {
  return `${f.webkitRelativePath || f.name}:${f.size}:${f.lastModified}`
}

function displayName(f) {
  return f.webkitRelativePath || f.name
}

function formData() {
  const fd = new FormData()
  files.value.forEach(f => fd.append('files', f, displayName(f)))
  return fd
}

function clearFiles() {
  files.value = []
  previewResp.value = null
  ratingResp.value = null
  activeBattle.value = 0
  error.value = ''
}

async function loadPreview() {
  if (!files.value.length) {
    error.value = '请先选择回放文件或文件夹'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const r = await fetch('/api/preview', { method: 'POST', body: formData() })
    if (!r.ok) throw new Error('扩展字段解析失败: HTTP ' + r.status)
    previewResp.value = await r.json()
    activeBattle.value = 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadRating() {
  if (!files.value.length) {
    error.value = '请先选择回放文件或文件夹'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const r = await fetch('/api/rating', { method: 'POST', body: formData() })
    if (!r.ok) throw new Error('Rating 计算失败: HTTP ' + r.status)
    ratingResp.value = await r.json()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function label(map, key) {
  return map[key] || key
}

function countMapText(m) {
  return Object.entries(m || {})
    .sort((a, b) => Number(a[0]) - Number(b[0]))
    .map(([k, v]) => `${k}:${v}`)
    .join('  ')
}

function setSort(scope, key, num) {
  const prev = sortState.value[scope]
  sortState.value[scope] = prev?.key === key
    ? { key, num, reverse: !prev.reverse }
    : { key, num, reverse: false }
}

function sorted(rows, scope) {
  const state = sortState.value[scope]
  if (!state) return rows || []
  const out = [...(rows || [])]
  out.sort((a, b) => {
    const av = a.cells?.[state.key]
    const bv = b.cells?.[state.key]
    if (state.num) return (Number(av) || 0) - (Number(bv) || 0)
    return String(av ?? '').localeCompare(String(bv ?? ''))
  })
  return state.reverse ? out.reverse() : out
}

function arrow(scope, key) {
  const state = sortState.value[scope]
  return state?.key === key ? (state.reverse ? ' ▼' : ' ▲') : ''
}
</script>

<template>
  <div class="wrap">
    <header>
      <h1>WoT Blitz 扩展分析</h1>
    </header>

    <section class="toolbar">
      <label class="filebtn">
        选择回放文件
        <input type="file" multiple accept=".wotbreplay" @change="e => { addFiles(e.target.files); e.target.value = '' }" />
      </label>
      <label class="filebtn">
        选择文件夹
        <input type="file" multiple webkitdirectory @change="e => { addFiles(e.target.files); e.target.value = '' }" />
      </label>
      <button class="ghost" :disabled="loading || !files.length" @click="clearFiles">清空</button>
      <button :disabled="loading || !files.length" @click="loadPreview">扩展字段解析</button>
      <button :disabled="loading || !files.length" @click="loadRating">Rating 分析</button>
      <span class="muted">{{ files.length ? `已选 ${files.length} 个回放` : '未选择文件' }}</span>
      <span v-if="loading" class="muted">处理中…</span>
    </section>

    <section v-if="files.length" class="files">
      <span v-for="f in files" :key="fileKey(f)" class="chip">{{ displayName(f) }}</span>
    </section>

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="duplicateRows.length" class="notice warn">
      已跳过 {{ duplicateRows.length }} 个重复回放：
      <span v-for="(d, i) in duplicateRows" :key="`dup-${i}`">
        [{{ d.scope }}] {{ d.name }} <span v-if="d.detail">({{ d.detail }})</span>
      </span>
    </section>

    <section v-if="failureRows.length" class="notice fail">
      {{ failureRows.length }} 个文件解析失败：
      <span v-for="(f, i) in failureRows" :key="`fail-${i}`">
        [{{ f.scope }}] {{ f.name }} <span v-if="f.detail">({{ f.detail }})</span>
      </span>
    </section>

    <section v-if="ratingResp?.rows?.length" class="panel">
      <h2>Rating</h2>
      <div class="tablewrap">
        <table>
          <thead><tr>
            <th v-for="c in ratingCols" :key="c.key" @click="setSort('rating', c.key, c.num)">
              {{ label(RATING_LABELS, c.key) }}{{ arrow('rating', c.key) }}
            </th>
          </tr></thead>
          <tbody>
            <tr v-for="(row, i) in sorted(ratingResp.rows, 'rating')" :key="i">
              <td v-for="c in ratingCols" :key="c.key" :class="{ num: c.num }">{{ row.cells[c.key] }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="battles.length" class="panel">
      <h2>扩展字段</h2>
      <div class="tabs">
        <button v-for="(b, i) in battles" :key="i" :class="{ active: activeBattle === i }" @click="activeBattle = i">
          {{ b.mapName || '未知地图' }} #{{ i + 1 }}
        </button>
      </div>

      <template v-if="currentBattle">
        <p class="info">
          地图: {{ currentBattle.mapName }} · 版本: {{ currentBattle.version }}
          · 获胜: {{ currentBattle.winnerTeam || '未知' }}
          · source: {{ currentBattle.sourceName }}
        </p>

        <p v-if="currentBattle.replayTrace?.present" class="trace">
          data.wotreplay:
          client={{ currentBattle.replayTrace.clientVersion || '-' }}
          · packets={{ currentBattle.replayTrace.packetCount }}
          · clock={{ currentBattle.replayTrace.firstClock }}-{{ currentBattle.replayTrace.lastClock }}s
          · maxPayload={{ currentBattle.replayTrace.maxPayloadBytes }}B
          · types=[{{ countMapText(currentBattle.replayTrace.packetTypes) }}]
          · entitySubtypes=[{{ countMapText(currentBattle.replayTrace.entityMethodSubtypes) }}]
        </p>
        <p v-else class="trace">data.wotreplay: missing</p>

        <div class="grid2">
          <div class="tablewrap" v-if="currentBattle.replayTrace?.packetGroups?.length">
            <h3>packet groups</h3>
            <table class="small">
              <thead><tr>
                <th>type</th><th>count</th><th>payload</th><th>clock</th><th>accountHits</th><th>tankHits</th><th>sampleHex</th>
              </tr></thead>
              <tbody>
                <tr v-for="g in currentBattle.replayTrace.packetGroups" :key="g.id">
                  <td class="num">{{ g.id }}</td>
                  <td class="num">{{ g.count }}</td>
                  <td class="num">{{ g.minPayloadBytes }}/{{ g.avgPayloadBytes }}/{{ g.maxPayloadBytes }}</td>
                  <td class="num">{{ g.firstClock }}-{{ g.lastClock }}</td>
                  <td class="num">{{ g.accountIdPayloads }} <span v-if="g.sampleAccountIds">({{ g.sampleAccountIds }})</span></td>
                  <td class="num">{{ g.tankIdPayloads }} <span v-if="g.sampleTankIds">({{ g.sampleTankIds }})</span></td>
                  <td class="mono">{{ g.sampleHexPrefix }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="tablewrap" v-if="currentBattle.replayTrace?.entityMethodGroups?.length">
            <h3>entity method groups</h3>
            <table class="small">
              <thead><tr>
                <th>subtype</th><th>count</th><th>payload</th><th>clock</th><th>accountHits</th><th>tankHits</th><th>sampleHex</th>
              </tr></thead>
              <tbody>
                <tr v-for="g in currentBattle.replayTrace.entityMethodGroups" :key="g.id">
                  <td class="num">{{ g.id }}</td>
                  <td class="num">{{ g.count }}</td>
                  <td class="num">{{ g.minPayloadBytes }}/{{ g.avgPayloadBytes }}/{{ g.maxPayloadBytes }}</td>
                  <td class="num">{{ g.firstClock }}-{{ g.lastClock }}</td>
                  <td class="num">{{ g.accountIdPayloads }} <span v-if="g.sampleAccountIds">({{ g.sampleAccountIds }})</span></td>
                  <td class="num">{{ g.tankIdPayloads }} <span v-if="g.sampleTankIds">({{ g.sampleTankIds }})</span></td>
                  <td class="mono">{{ g.sampleHexPrefix }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="tablewrap" v-if="currentBattle.battleRawColumns?.length">
          <h3>battle raw</h3>
          <table class="small">
            <thead><tr><th v-for="key in currentBattle.battleRawColumns" :key="key">{{ key }}</th></tr></thead>
            <tbody><tr><td v-for="key in currentBattle.battleRawColumns" :key="key">{{ currentBattle.battleRaw?.[key] }}</td></tr></tbody>
          </table>
        </div>

        <div class="tablewrap">
          <h3>players</h3>
          <table>
            <thead><tr>
              <th v-for="c in playerCols" :key="c.key" @click="setSort('players', c.key, c.num)">
                {{ label(PLAYER_LABELS, c.key) }}{{ arrow('players', c.key) }}
              </th>
            </tr></thead>
            <tbody>
              <tr v-for="(row, i) in sorted(currentBattle.players, 'players')" :key="i" :class="row.team === 1 ? 't1' : 't2'">
                <td v-for="c in playerCols" :key="c.key" :class="{ num: c.num }">{{ row.cells[c.key] }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="tablewrap" v-if="currentBattle.rawColumns?.length">
          <h3>player raw</h3>
          <table class="small">
            <thead><tr>
              <th>玩家</th><th>账号ID</th><th v-for="key in currentBattle.rawColumns" :key="key">{{ key }}</th>
            </tr></thead>
            <tbody>
              <tr v-for="(row, i) in currentBattle.players" :key="i" :class="row.team === 1 ? 't1' : 't2'">
                <td>{{ row.cells.nickname }}</td>
                <td class="num">{{ row.cells.account_id }}</td>
                <td v-for="key in currentBattle.rawColumns" :key="key" class="mono">{{ row.raw?.[key] }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </div>
</template>

<style>
body { margin: 0; font-family: "Segoe UI", "Microsoft YaHei", sans-serif; color: #20242a; background: #f4f6f8; }
.wrap { max-width: 1500px; margin: 0 auto; padding: 16px 20px 28px; }
h1 { font-size: 22px; margin: 0 0 14px; }
h2 { font-size: 16px; margin: 0 0 8px; color: #1f4e79; }
h3 { font-size: 13px; margin: 10px 0 6px; color: #1f4e79; }
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
button, .filebtn { padding: 6px 14px; border: 1px solid #2f5597; background: #2f5597; color: #fff;
  border-radius: 4px; cursor: pointer; font-size: 13px; }
button:disabled { opacity: .5; cursor: default; }
.ghost { background: #fff; color: #2f5597; }
.filebtn input { display: none; }
.muted { color: #667085; font-size: 13px; }
.error { color: #b42318; }
.notice { border-radius: 4px; padding: 8px 10px; margin: 8px 0; font-size: 13px; }
.notice span { margin-right: 10px; }
.warn { background: #fff8df; border: 1px solid #eed27b; color: #7a4f01; }
.fail { background: #fff1f0; border: 1px solid #f3b2ad; color: #b42318; }
.files { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.chip { background: #eef2f7; border: 1px solid #d7dee9; border-radius: 4px; padding: 3px 6px; font-size: 12px; }
.panel { background: #fff; border: 1px solid #d8dee8; border-radius: 6px; padding: 10px; margin-top: 12px; }
.tabs { display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 8px; }
.tabs button { background: #e8edf5; color: #2f5597; border-color: #c7d3e6; }
.tabs button.active { background: #2f5597; color: #fff; }
.info, .trace { font-size: 13px; margin: 6px 0; color: #2b5c38; }
.trace { color: #394150; }
.grid2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.tablewrap { overflow-x: auto; margin-bottom: 10px; }
table { border-collapse: collapse; width: max-content; min-width: 100%; font-size: 13px; }
th, td { border: 1px solid #cfd8e3; padding: 4px 8px; white-space: nowrap; }
th { background: #2f5597; color: #fff; cursor: pointer; user-select: none; }
td.num { text-align: center; }
.small { font-size: 12px; }
.mono { font-family: Consolas, "Courier New", monospace; max-width: 520px; overflow: hidden; text-overflow: ellipsis; }
tr.t1 td { background: #ddebf7; }
tr.t2 td { background: #fce4d6; }
@media (max-width: 900px) {
  .grid2 { grid-template-columns: 1fr; }
}
</style>



