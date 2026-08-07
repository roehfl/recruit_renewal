<template>
  <div class="banner banner-2">
    <div class="banner-text">
      <p>Be Different, Be Bold</p>
      <h1>신영증권 채용</h1>
      <span>RECRUIT</span>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.banner-2 {
  position: relative;
  height: 180px;
  overflow: hidden;
  border-radius: 8px;
  background:
    radial-gradient(circle at 90% 10%, rgba(127, 201, 166, 0.22), transparent 40%),
    linear-gradient(135deg, #0e2a1f 0%, #16382b 55%, #1d4634 100%);
}

.banner-text {
  padding: 32px 48px;
}

.banner-text p {
  margin: 0 0 8px;
  color: #7fc9a6;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.banner-text h1 {
  margin: 0;
  color: #ffffff;
  font-size: 44px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.banner-text span {
  display: block;
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.8em;
}
</style>
