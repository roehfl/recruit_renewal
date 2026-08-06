import type { RouteRecordRaw } from 'vue-router'

export const authRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: {
      public: true,
    },
  },
  {
    path: '/nice-auth',
    name: 'NiceAuthPopup',
    component: () => import('@/views/auth/pop-up/NiceAuthPopup.vue'),
    meta: {
      public: true,
    },
  },
]
