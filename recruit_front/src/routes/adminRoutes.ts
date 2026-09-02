import type { RouteRecordRaw } from 'vue-router'

/*
 * 관리자 화면은 모두 AdminLayout(좌측 사이드바 + 우측 라우팅 영역) 아래에 붙인다.
 * meta는 하위 라우트로 병합되므로 인증·권한은 부모에서 한 번만 지정한다.
 * 백엔드 /api/admin/** 권한(SecurityConfig)과 같은 역할을 사용한다.
 */
export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: {
      requiresAuth: true,
      roles: ['ROLE_ADMIN', 'ROLE_RECRUIT_ADMIN'],
    },
    children: [
      {
        path: '',
        name: 'AdminHome',
        component: () => import('@/views/admin/AdminHomeView.vue'),
      },
      {
        path: 'menus',
        name: 'AdminMenuManage',
        component: () => import('@/views/admin/MenuManageView.vue'),
      },
      {
        path: 'role-mappings',
        name: 'AdminRoleMapping',
        component: () => import('@/views/admin/RoleMappingView.vue'),
      },
      {
        path: 'faqs',
        name: 'AdminFaqManage',
        component: () => import('@/views/admin/faq/AdminFaqManageView.vue'),
      },
      {
        path: 'codes',
        name: 'AdminCommonCodeManage',
        component: () => import('@/views/admin/AdminCommonCodeManageView.vue'),
      },
      {
        path: 'job-postings',
        name: 'AdminJobPostingList',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingListView.vue'),
      },
      {
        path: 'job-postings/new',
        name: 'AdminJobPostingCreate',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingFormView.vue'),
      },
      {
        path: 'job-postings/:id',
        name: 'AdminJobPostingDetail',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingDetailView.vue'),
      },
      {
        path: 'job-postings/:id/edit',
        name: 'AdminJobPostingEdit',
        component: () => import('@/views/admin/jobPosting/AdminJobPostingFormView.vue'),
      },
      {
        path: 'applications',
        name: 'AdminApplicationStatus',
        component: () => import('@/views/admin/application/ApplicationStatus.vue'),
      },
      {
        path: 'applications/:applicationId',
        name: 'AdminApplication',
        component: () => import('@/views/admin/application/Application.vue'),
      },
      {
        path: 'question-template/:id?/edit',
        name: 'AdminJobPostingQuestionTemplateEdit',
        component: () => import('@/views/admin/jobPosting/AdminQuestionTemplateEditView.vue'),
      },
      {
        path: 'question-templates',
        name: 'AdminQuestionTemplates',
        component: () => import('@/views/admin/jobPosting/AdminQuestionTemplatesView.vue')
      }
    ],
  },
]
