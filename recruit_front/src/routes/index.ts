import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { useAuthStore } from '@/stores/authStore'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if(!authStore.initialized) {
    await authStore.fetchMe()
  }

  if (to.meta.public) {
    return true
  }

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    const restored = await authStore.fetchMe()

    if (!restored) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  }

  const requiredRoles = to.meta.roles as string[] | undefined

  if (requiredRoles && requiredRoles.length > 0) {
    const hasRole = requiredRoles.some((role) => authStore.roles.includes(role))

    if (!hasRole) {
      return '/403'
    }
  }

  return true
})
