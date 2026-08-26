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
        name: 'ApplicantBenefits',   // 인사제도 (보상 및 평가 · 교육제도 · 복리후생 통합)
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
        name: 'ApplicantHrRule',          // 인사제도 > 보상 및 평가 (benefits 화면으로 통합)
        redirect: { path: '/applicant/benefits', query: { tab: 'reward' } },
      },
      {
        path: 'training',
        name: 'ApplicantTraining',          // 인사제도 > 교육제도 (benefits 화면으로 통합)
        redirect: { path: '/applicant/benefits', query: { tab: 'training' } },
      },
      {
        path: 'faq',
        name: 'ApplicantFaq',   // 채용안내 > 자주 묻는 질문
        component: () => import('@/views/applicant/FaqView.vue'),
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
          activeMenuPath: '/applicant/recruits',
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
          activeMenuPath: '/applicant/recruits',
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
      {
        path: 'privacy',
        name: 'ApplicantPrivacy',
        component: () => import('@/views/applicant/ApplicantPrivacy.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'signup',
        name: 'Signup',
        component: () => import('@/views/applicant/SignupView.vue'),
        meta: {
          public: true,
        },
      },
      {
        path: 'accountRecovery',
        name: 'accountRecovery',
        component: () => import('@/views/applicant/AccountRecovery.vue'),
        meta: {
          public: true,
        },
      },
    ],
  },
]
