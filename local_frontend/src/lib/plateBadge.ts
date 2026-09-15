const PLATE_STYLES: Record<string, { background: string; color: string }> = {
  BLUE: { background: '#2b4db1', color: '#ffffff' },
  YELLOW: { background: '#f5c518', color: '#1a1a1a' },
  GREEN: { background: '#1a8f3c', color: '#ffffff' },
  YELLOW_GREEN: { background: 'linear-gradient(90deg, #f5c518 48%, #2e9a44 52%)', color: '#1a1a1a' },
  BLACK: { background: '#1c1c1c', color: '#ffffff' },
  WHITE: { background: '#f4f6f8', color: '#111111' },
  RED: { background: '#c62828', color: '#ffffff' },
  ORANGE: { background: '#f0883a', color: '#1a1a1a' },
  BROWN: { background: '#6d4c41', color: '#ffffff' },
  PURPLE: { background: '#7b1fa2', color: '#ffffff' },
  PINK: { background: '#d81b60', color: '#ffffff' },
  GRAY: { background: '#8a8f98', color: '#ffffff' },
  SILVER: { background: '#c7ccd1', color: '#1a1a1a' },
  GOLD: { background: '#c9a227', color: '#1a1a1a' },
  CREAM: { background: '#f3ead2', color: '#1a1a1a' },
  BEIGE: { background: '#d7c4a3', color: '#1a1a1a' },
  NAVY: { background: '#12315c', color: '#ffffff' },
  MAROON: { background: '#7a1f2b', color: '#ffffff' },
  OLIVE: { background: '#6b8f3c', color: '#ffffff' },
  TEAL: { background: '#1d7a73', color: '#ffffff' },
  CYAN: { background: '#2bb8c8', color: '#10343a' },
  MAGENTA: { background: '#c2185b', color: '#ffffff' },
  LIME: { background: '#9ccc3c', color: '#1a1a1a' },
  LAVENDER: { background: '#b39ddb', color: '#2a2140' },
  TURQUOISE: { background: '#2aa89a', color: '#ffffff' },
  INDIGO: { background: '#3949ab', color: '#ffffff' },
  CORAL: { background: '#ef7a63', color: '#1a1a1a' },
  AMBER: { background: '#e6a817', color: '#1a1a1a' },
  VIOLET: { background: '#8e24aa', color: '#ffffff' },
  CHARCOAL: { background: '#3d434b', color: '#ffffff' },
  LIGHT_BLUE: { background: '#7eb6e6', color: '#12324d' },
  LIGHT_GREEN: { background: '#8fd18b', color: '#16351a' },
  DARK_BLUE: { background: '#163a7a', color: '#ffffff' },
  DARK_GREEN: { background: '#14532d', color: '#ffffff' },
  RUST: { background: '#b5522a', color: '#ffffff' },
  BRONZE: { background: '#8c6239', color: '#ffffff' },
  PEACH: { background: '#f3b48c', color: '#4a2a18' },
  MINT: { background: '#98d7b2', color: '#163526' },
  ROSE: { background: '#e8a0b4', color: '#4a1d2d' },
  SALMON: { background: '#ee8b78', color: '#3d1c16' },
  COPPER: { background: '#b87333', color: '#ffffff' },
  PLUM: { background: '#7a3e6a', color: '#ffffff' },
  CRIMSON: { background: '#9b1b30', color: '#ffffff' },
  SCARLET: { background: '#d32f2f', color: '#ffffff' },
  EMERALD: { background: '#1f8a5b', color: '#ffffff' },
  SAPPHIRE: { background: '#1a56b0', color: '#ffffff' },
  RUBY: { background: '#9b1c48', color: '#ffffff' },
  OTHER: { background: '#e8eaed', color: '#1a1a1a' },
}

const LIGHT_BORDERS = new Set([
  'WHITE',
  'CREAM',
  'BEIGE',
  'SILVER',
  'YELLOW',
  'GOLD',
  'LIME',
  'PEACH',
  'MINT',
  'LAVENDER',
  'LIGHT_BLUE',
  'LIGHT_GREEN',
  'OTHER',
])

export function plateStyle(color: string): Record<string, string> {
  const style = PLATE_STYLES[color] ?? PLATE_STYLES.OTHER
  return { background: style.background, color: style.color }
}

export function plateSwatchStyle(color: string): Record<string, string> {
  const style = plateStyle(color)
  return {
    ...style,
    border: LIGHT_BORDERS.has(color) ? '1px solid #c5c9d0' : '1px solid rgba(0, 0, 0, 0.28)',
  }
}
