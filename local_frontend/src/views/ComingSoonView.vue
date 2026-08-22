<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const route = useRoute()
const title = computed(() => t(String(route.meta.titleKey ?? 'page.comingSoon')))
</script>

<template>
  <section class="page">
    <div class="toolbar">
      <label class="search">
        <span class="sr-only">{{ t('page.search') }}</span>
        <input type="search" disabled :placeholder="t('page.search')" />
      </label>
      <button type="button" disabled>{{ t('page.new') }}</button>
    </div>

    <div class="table-card">
      <table>
        <thead>
          <tr>
            <th>{{ t('page.colName') }}</th>
            <th>{{ t('page.colStatus') }}</th>
            <th>{{ t('page.colUpdated') }}</th>
          </tr>
        </thead>
      </table>
      <div class="empty">
        <strong>{{ title }}</strong>
        <p>{{ t('page.comingSoonHint') }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: grid;
  gap: 0.9rem;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}

.search {
  flex: 1;
  max-width: 18rem;
}

.search input,
.toolbar button {
  border: 1px solid var(--border);
  border-radius: 8px;
  min-height: 2.25rem;
  padding: 0 0.8rem;
}

.search input {
  width: 100%;
  background: var(--surface);
  color: var(--text);
}

.toolbar button {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}

.toolbar button:disabled,
.search input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.table-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th {
  text-align: start;
  padding: 0.75rem 1rem;
  color: var(--muted);
  font-size: 0.8rem;
  font-weight: 600;
  background: #f7faf8;
  border-bottom: 1px solid var(--border);
}

.empty {
  padding: 3.5rem 1.5rem;
  text-align: center;
}

.empty strong {
  display: block;
  margin-bottom: 0.35rem;
}

.empty p {
  margin: 0 auto;
  max-width: 28rem;
  color: var(--muted);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
