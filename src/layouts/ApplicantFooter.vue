<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <div class="footer-top">
        <div class="company-area">
          <h2 class="company-name">신영증권</h2>

          <p class="company-info">서울특별시 영등포구 국제금융로8길 16 신영증권 빌딩</p>
          <p class="company-info">02)2004-9038</p>
          <p class="company-info">02)2004-9358</p>
        </div>

        <nav class="footer-menu" aria-label="푸터 메뉴">
          <div v-for="group in footerMenus" :key="group.title" class="footer-menu-group">
            <h3 class="footer-menu-title">
              {{ group.title }}
            </h3>

            <ul class="footer-menu-list">
              <li v-for="item in group.items" :key="item.url" class="footer-menu-item">
                <RouterLink :to="item.url">
                  {{ item.title }}
                </RouterLink>
              </li>
            </ul>
          </div>
        </nav>
      </div>

      <div class="footer-bottom">
        <p class="copyright">COPYRIGHT SHINYOUNG SECURITIES CO.,LTD. ALL RIGHTS RESERVED</p>

        <RouterLink class="privacy-link" to="/privacy"> 개인정보처리방침 </RouterLink>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
interface FooterMenuItem {
  title: string
  url: string
}

interface FooterMenuGroup {
  title: string
  items: FooterMenuItem[]
}

const footerMenus: FooterMenuGroup[] = [
  {
    title: '인사제도',
    items: [
      {
        title: '보상 및 평가',
        url: '/applicant/hrRule',
      },
      {
        title: '교육제도',
        url: '/applicant/training',
      },
      {
        title: '복리후생',
        url: '/applicant/benefits',
      },
    ],
  },
  {
    title: '채용안내',
    items: [
      {
        title: '채용절차',
        url: '/guide/process',
      },
      {
        title: '직무소개',
        url: '/guide/job',
      },
    ],
  },
  {
    title: '채용공고',
    items: [
      {
        title: '공개채용',
        url: '/recruit/public',
      },
      {
        title: '수시채용',
        url: '/recruit/regular',
      },
    ],
  },
  {
    title: '지원서 및 합격자 조회',
    items: [
      {
        title: '지원서 작성/확인',
        url: '/apply',
      },
      {
        title: '전형결과 확인',
        url: '/result',
      },
      {
        title: '지원자 마이페이지',
        url: '/mypage',
      },
    ],
  },
]
</script>

<style scoped>
.app-footer {
  margin-top: 56px;
  border-top: 1px solid #e3e7ea;
  background: #f7f8f8;
}

.footer-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 36px 24px 22px;
}

.footer-top {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 64px;
}

.company-area {
  min-width: 0;
}

.company-name {
  margin: 0 0 14px;
  color: #9aa0a6;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.3;
}

.company-info {
  margin: 4px 0 0;
  color: #9aa0a6;
  font-size: 13px;
  line-height: 1.55;
  word-break: keep-all;
}

.footer-menu {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 36px;
}

.footer-menu-title {
  margin: 0 0 12px;
  color: #1f2933;
  font-size: 15px;
  font-weight: 800;
  line-height: 1.35;
}

.footer-menu-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.footer-menu-item {
  margin-top: 7px;
}

.footer-menu-item:first-child {
  margin-top: 0;
}

.footer-menu-item a {
  color: #333d46;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.45;
  text-decoration: none;
  transition: color 0.18s ease;
}

.footer-menu-item a:hover {
  color: #2f6f55;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.footer-bottom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 34px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ea;
}

.copyright {
  margin: 0;
  color: #2c3a43;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.privacy-link {
  color: #6c9b20;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.privacy-link:hover {
  color: #2f6f55;
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 960px) {
  .footer-top {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .footer-menu {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 28px 36px;
  }

  .footer-bottom {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
}

@media (max-width: 560px) {
  .app-footer {
    margin-top: 40px;
  }

  .footer-inner {
    padding: 28px 20px 22px;
  }

  .footer-menu {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
</style>
