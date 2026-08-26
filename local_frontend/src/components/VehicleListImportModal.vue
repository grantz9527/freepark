<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  open: boolean
  scope: 'internalVehicles' | 'whitelist' | 'blacklist' | 'spaces'
  importing: boolean
  downloadingTemplate: boolean
  importError: string
  importFile: File | null
  /** Optional override for the hint paragraph (e.g. current location/area). */
  hint?: string
}>()

const emit = defineEmits<{
  close: []
  submit: []
  downloadTemplate: []
  fileChange: [file: File | null]
}>()

const { t } = useI18n()
const importInput = ref<HTMLInputElement | null>(null)

function text(key: string): string {
  return t(`${props.scope}.${key}`)
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  emit('fileChange', input.files?.[0] ?? null)
}

function pickFile(): void {
  importInput.value?.click()
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
    <form class="modal import-modal" @submit.prevent="emit('submit')">
      <div class="modal-header">
        <h3>{{ text('importTitle') }}</h3>
        <button type="button" class="modal-close" :aria-label="text('cancel')" @click="emit('close')">×</button>
      </div>
      <p class="hint">{{ hint ?? text('importHint') }}</p>
      <div class="import-toolbar">
        <button type="button" class="outline" :disabled="downloadingTemplate" @click="emit('downloadTemplate')">
          {{ downloadingTemplate ? text('downloadingTemplate') : text('downloadTemplate') }}
        </button>
      </div>
      <label class="file-field">
        <span>{{ text('importFileLabel') }}</span>
        <button type="button" class="ghost file-picker" @click="pickFile">
          {{ importFile ? text('importChangeFile') : text('importChooseFile') }}
        </button>
        <input
          ref="importInput"
          type="file"
          accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
          class="hidden-file"
          @change="onFileChange"
        />
        <p v-if="importFile" class="import-file">{{ importFile.name }}</p>
      </label>
      <p v-if="importError" class="form-error">{{ importError }}</p>
      <div class="actions">
        <button type="button" class="ghost" :disabled="importing" @click="emit('close')">
          {{ text('cancel') }}
        </button>
        <button type="submit" :disabled="importing || !importFile">
          {{ importing ? text('importing') : text('importSubmit') }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 20, 0.45);
  display: grid;
  place-items: center;
  padding: 1rem;
  z-index: 20;
}

.modal {
  width: min(560px, 100%);
  display: grid;
  gap: 0.75rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 1.25rem;
  box-shadow: var(--shadow);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.modal h3 {
  margin: 0;
}

.modal-close {
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 1.35rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.15rem 0.35rem;
}

.modal-close:hover {
  color: var(--text);
}

.hint {
  margin: 0;
  color: var(--muted);
  font-size: 0.9rem;
  line-height: 1.45;
}

.import-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.outline {
  border: 1px solid var(--accent);
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  background: #fff;
  color: var(--accent);
  cursor: pointer;
}

.outline:hover:not(:disabled) {
  background: #f2f8f5;
}

.outline:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.file-field {
  display: grid;
  gap: 0.5rem;
}

.file-picker {
  justify-self: start;
}

.hidden-file {
  display: none;
}

.import-file {
  margin: 0;
  color: var(--muted);
  font-size: 0.88rem;
  word-break: break-all;
}

.ghost {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 0.5rem 0.85rem;
  font-weight: 600;
  background: #fff;
  color: var(--text);
  cursor: pointer;
}

.form-error {
  margin: 0;
  color: var(--danger);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.actions button:not(.ghost) {
  border: 0;
  border-radius: 8px;
  padding: 0.55rem 0.9rem;
  font-weight: 600;
  color: #fff;
  background: var(--accent);
}

.actions button:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
</style>
