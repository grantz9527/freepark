const MIME_CANDIDATES = [
  'video/mp4; codecs="avc1.64001F"',
  'video/mp4; codecs="avc1.4D401F"',
  'video/mp4; codecs="avc1.42E01E"',
]

export function canPlayTranscodedFmp4(): boolean {
  return typeof MediaSource !== 'undefined' && MIME_CANDIDATES.some((mime) => MediaSource.isTypeSupported(mime))
}

function pickMime(): string {
  const mime = MIME_CANDIDATES.find((item) => MediaSource.isTypeSupported(item))
  if (!mime) {
    throw new Error('mse-unsupported')
  }
  return mime
}

/**
 * 把后端转出的 fMP4 流喂给 video（MSE）。signal abort 时停止。
 */
export async function pipeFmp4ToVideo(
  video: HTMLVideoElement,
  stream: ReadableStream<Uint8Array>,
  signal: AbortSignal,
): Promise<void> {
  if (!canPlayTranscodedFmp4()) {
    throw new Error('mse-unsupported')
  }
  const mediaSource = new MediaSource()
  const objectUrl = URL.createObjectURL(mediaSource)
  video.src = objectUrl
  const cleanup = (): void => {
    try {
      if (mediaSource.readyState === 'open') {
        mediaSource.endOfStream()
      }
    } catch {
      // already closed
    }
    URL.revokeObjectURL(objectUrl)
  }
  signal.addEventListener('abort', cleanup, { once: true })
  try {
    await waitForEvent(mediaSource, 'sourceopen', signal)
    const sourceBuffer = mediaSource.addSourceBuffer(pickMime())
    sourceBuffer.mode = 'segments'
    const pending: Uint8Array[] = []
    let streamDone = false

    const appendNext = (): void => {
      if (signal.aborted || sourceBuffer.updating) {
        return
      }
      if (pending.length === 0) {
        if (streamDone && mediaSource.readyState === 'open') {
          try {
            mediaSource.endOfStream()
          } catch {
            // ignore
          }
        }
        return
      }
      const chunk = pending.shift()
      if (!chunk) {
        return
      }
      try {
        sourceBuffer.appendBuffer(chunk.slice())
      } catch {
        pending.unshift(chunk)
      }
    }

    sourceBuffer.addEventListener('updateend', appendNext)
    void video.play().catch(() => undefined)

    const reader = stream.getReader()
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) {
        streamDone = true
        appendNext()
        break
      }
      if (value != null && value.byteLength > 0) {
        pending.push(value)
        appendNext()
      }
    }
  } catch (error) {
    cleanup()
    if (signal.aborted) {
      return
    }
    throw error
  }
}

function waitForEvent(target: EventTarget, type: string, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const onAbort = (): void => {
      target.removeEventListener(type, onOpen)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    const onOpen = (): void => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }
    target.addEventListener(type, onOpen, { once: true })
    signal.addEventListener('abort', onAbort, { once: true })
  })
}
