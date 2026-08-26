<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <p class="copyright">COPYRIGHT SHINYOUNG SECURITIES CO.,LTD. ALL RIGHTS RESERVED</p>

      <RouterLink class="privacy-link" to="/applicant/privacy"> 개인정보처리방침 </RouterLink>
    </div>
  </footer>
</template>

<style scoped>
/*
 * 프레임 안쪽 맨 아래에 놓이는 고정 높이 푸터. 앱 테마인 다크그린으로 채운다.
 * 본문이 짧으면 프레임의 min-height와 flex 배치 덕분에 화면 바닥에 붙고,
 * 길면 콘텐츠 뒤로 밀린다.
 */
.app-footer {
  flex: 0 0 auto;
  width: 100%;
  height: var(--app-footer-height);
  background: var(--app-color-primary);
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;

  width: 100%;
  height: 100%;
  padding: 0 var(--app-frame-padding-x);
}

.copyright {
  margin: 0;
  color: rgb(255 255 255 / 72%);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.privacy-link {
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.privacy-link:hover {
  color: var(--tap-accent-bar);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 560px) {
  .footer-inner {
    align-items: flex-start;
    justify-content: center;
    flex-direction: column;
    gap: 4px;
    padding: 0 20px;
  }
}
</style>
