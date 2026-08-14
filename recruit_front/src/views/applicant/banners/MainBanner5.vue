<template>
  <div class="banner banner-5">
    <div class="block block-deep"></div>
    <div class="block block-light"></div>
    <div class="banner-text">
      <p>Be Different, Be Bold</p>
      <h1>신영증권 채용</h1>
      <span>RECRUIT</span>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.banner-5 {
  position: relative;
  height: 180px;
  overflow: hidden;
  border: 1px solid #e3e8e5;
  border-radius: 8px;
  background: #f4f8f6;
}

.block {
  position: absolute;
  top: 0;
  height: 100%;
  pointer-events: none;
}

.block-deep {
  right: 0;
  width: 42%;
  background: linear-gradient(135deg, #2f6f55, #1d4634);
  clip-path: polygon(32% 0, 100% 0, 100% 100%, 0 100%);
}

.block-light {
  right: 26%;
  width: 22%;
  background: rgba(47, 111, 85, 0.18);
  clip-path: polygon(40% 0, 100% 0, 60% 100%, 0 100%);
}

.banner-text {
  position: relative;
  padding: 32px 48px;
}

.banner-text p {
  margin: 0 0 8px;
  color: #2f6f55;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.banner-text h1 {
  margin: 0;
  color: #16382b;
  font-size: 42px;
  font-weight: 900;
}

.banner-text span {
  display: block;
  margin-top: 10px;
  color: #5f8272;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.7em;
}
</style>
