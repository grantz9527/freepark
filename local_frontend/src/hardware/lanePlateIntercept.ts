import type { PlateColor } from '@/api/client'
import { siteAllowedPlateColors } from '@/site/settings'

const STORAGE_KEY = 'freepark.planning.lanePlateIntercept'

type InterceptMap = Record<string, PlateColor[]>

function loadMap(): InterceptMap {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return {}
  }
  try {
    const parsed = JSON.parse(raw) as InterceptMap
    if (!parsed || typeof parsed !== 'object') {
      return {}
    }
    return parsed
  } catch {
    return {}
  }
}

function persist(map: InterceptMap): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(map))
}

function allowedColors(): Set<PlateColor> {
  return new Set(siteAllowedPlateColors.value)
}

export function listLanePlateIntercept(): InterceptMap {
  const allowed = allowedColors()
  const next: InterceptMap = {}
  for (const [laneId, colors] of Object.entries(loadMap())) {
    const filtered = [...new Set((colors ?? []).filter((color) => allowed.has(color)))]
    if (filtered.length > 0) {
      next[laneId] = filtered
    }
  }
  return next
}

export function getLanePlateIntercept(laneId: string): PlateColor[] {
  return listLanePlateIntercept()[laneId] ?? []
}

export function saveLanePlateIntercept(laneId: string, colors: PlateColor[]): PlateColor[] {
  const allowed = allowedColors()
  const nextColors = [...new Set(colors.filter((color) => allowed.has(color)))]
  const map = loadMap()
  if (nextColors.length === 0) {
    delete map[laneId]
  } else {
    map[laneId] = nextColors
  }
  persist(map)
  return nextColors
}
