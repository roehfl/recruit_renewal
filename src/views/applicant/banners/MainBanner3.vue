<template>
  <div class="banner banner-3">
    <div class="text-area">
      <p>Be Different, Be Bold</p>
      <h1>신영증권 채용</h1>
      <span>RECRUIT</span>
    </div>
    <div class="pattern-area"></div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.banner-3 {
  display: flex;
  height: 180px;
  overflow: hidden;
  border: 1px solid #e3e8e5;
  border-radius: 8px;
  background: #ffffff;
}

.text-area {
  flex: 1 1 60%;
  padding: 32px 48px;
}

.text-area p {
  margin: 0 0 8px;
  color: #2f6f55;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.text-area h1 {
  margin: 0;
  color: #20242a;
  font-size: 40px;
  font-weight: 800;
}

.text-area span {
  display: block;
  margin-top: 10px;
  color: #8aa397;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.7em;
}

.pattern-area {
  flex: 0 0 36%;
  background-color: #2f6f55;
  background-image: radial-gradient(rgba(255, 255, 255, 0.22) 2px, transparent 2px);
  background-size: 22px 22px;
  clip-path: polygon(18% 0, 100% 0, 100% 100%, 0 100%);
}
</style>
