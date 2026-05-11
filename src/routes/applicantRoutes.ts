import type { RouteRecordRaw } from 'vue-router'

export const applicantRoutes: RouteRecordRaw[] = [
  {
    path: '/applicant',
    component: () => import('@/layouts/ApplicantLayout.vue'),
    children: [
      {
        path: '',
        name: 'ApplicantHome',
        component: () => import('@/views/applicant/ApplicantHomeView.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'benefits',
        name: 'ApplicantBenefits',
        component: () => import('@/views/applicant/ApplicantBenefits.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'noticeList',
        name: 'NoticeList',
        component: () => import('@/views/applicant/NoticeListView.vue'),
        meta: {
          public: true,
        },
      },
      //   {
      //     path: 'recruits',
      //     name: 'RecruitList',
      //     component: () => import('@/views/applicant/RecruitListView.vue'),
      //     meta: {
      //       public: true,
      //     },
      //   },
      //   {
      //     path: 'applications/my',
      //     name: 'MyApplication',
      //     component: () => import('@/views/applicant/MyApplicationView.vue'),
      //     meta: {
      //       requiresAuth: true,
      //       roles: ['ROLE_APPLICANT'],
      //     },
      //   },
    ],
  },
]
