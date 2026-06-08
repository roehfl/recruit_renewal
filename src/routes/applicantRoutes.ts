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
      {
        path: 'hrRule',
        name: 'ApplicantHrRule',          // 회사 소개 > 보상 및 평가
        component: () => import('@/views/applicant/ApplicantHrRule.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'training',
        name: 'ApplicantTraining',          // 회사 소개 > 교육제도
        component: () => import('@/views/applicant/ApplicantTraining.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'recruitProcedure',
        name: 'ApplicantRecruitProcedure',   // 채용안내 > 채용절차
        component: () => import('@/views/applicant/ApplicantRecruitProcedure.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: ':applicationId/form',
        name: 'application',
        component: () => import('@/views/applicant/ApplicationFormView.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'dutyIntroduction',
        name: 'ApplicantDutyIntroduction',
        component: () => import('@/views/applicant/ApplicantDutyIntroduction.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'recruits',
        name: 'ApplicantRecruits',   // 채용공고 
        component: () => import('@/views/applicant/ApplicantRecruit.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: ':jobPostingId/detail',    // 채용공고 > 공고 상세
        name: 'ApplicationDetail',
        component: () => import('@/views/applicant/ApplicationDetailView.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'profile',
        name: 'ApplicantProfile',
        component: () => import('@/views/applicant/ApplicantProfile.vue'),
        meta: {
          public: true,
        },
      },
    ],
  },
]
