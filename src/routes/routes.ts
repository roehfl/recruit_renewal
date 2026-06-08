import type { RouteRecordRaw } from 'vue-router'
import { authRoutes } from './authRoutes'
import { applicantRoutes } from './applicantRoutes'

export const routes: RouteRecordRaw[] = [
  {
    path: '/samples/antd',
    name: 'AntdSample',
    component: () => import('@/views/samples/AntdSampleView.vue'),
    meta: {
      public: true,
    },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: {
      public: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: {
      public: true,
    },
  },
  ...authRoutes,
  ...applicantRoutes,
]
