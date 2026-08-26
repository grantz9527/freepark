const PLATE_STYLES: Record<string, { background: string; color: string }> = {
  BLUE: { background: '#2b4db1', color: '#ffffff' },
  YELLOW: { background: '#f5c518', color: '#1a1a1a' },
  GREEN: { background: '#1a8f3c', color: '#ffffff' },
  YELLOW_GREEN: { background: '#b8c935', color: '#1a1a1a' },
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
}

export function plateStyle(color: string): Record<string, string> {
  const style = PLATE_STYLES[color] ?? { background: '#e8eaed', color: '#1a1a1a' }
  return { background: style.background, color: style.color }
}
