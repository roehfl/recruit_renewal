import { defineStore } from 'pinia'

interface UiState {
  loadingCount: number
}

export const useUiStore = defineStore('ui', {
  state: (): UiState => ({
    loadingCount: 0,
  }),

  getters: {
    isLoading: (state) => state.loadingCount > 0,
  },

  actions: {
    showLoading() {
      this.loadingCount += 1
    },
    hideLoading() {
      if (this.loadingCount > 0) {
        this.loadingCount -= 1
      }
    },
    resetLoading() {
      this.loadingCount = 0
    },
  },
})
