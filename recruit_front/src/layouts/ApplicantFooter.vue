<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <p class="copyright">COPYRIGHT SHINYOUNG SECURITIES CO.,LTD. ALL RIGHTS RESERVED</p>

      <RouterLink class="privacy-link" to="/applicant/privacy"> 개인정보처리방침 </RouterLink>
    </div>
  </footer>
</template>

<style scoped>
/* 화면 하단에 항상 떠 있는 고정 높이 푸터 */
.app-footer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 90;

  width: var(--app-frame-width);
  height: var(--app-footer-height);
  margin: 0 auto;
  border: 1px solid var(--app-border-color);
  border-top: 1px solid #e3e7ea;
  background: #f7f8f8;
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
