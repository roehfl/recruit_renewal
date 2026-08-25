<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <p class="copyright">COPYRIGHT SHINYOUNG SECURITIES CO.,LTD. ALL RIGHTS RESERVED</p>

      <RouterLink class="privacy-link" to="/applicant/privacy"> 개인정보처리방침 </RouterLink>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  margin-top: 56px;
  border-top: 1px solid #e3e7ea;
  background: #f7f8f8;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;

  max-width: var(--app-frame-width);
  margin: 0 auto;
  padding: 14px var(--app-frame-padding-x);
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
  .app-footer {
    margin-top: 40px;
  }

  .footer-inner {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
    padding: 12px 20px;
  }
}
</style>
