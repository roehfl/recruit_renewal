<template>
  <div class="banner banner-1">
    <div class="deco deco-a"></div>
    <div class="deco deco-b"></div>
    <div class="banner-text">
      <p>Be Different, Be Bold</p>
      <h1>신영증권 채용</h1>
      <span>RECRUIT</span>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.banner-1 {
  position: relative;
  height: 180px;
  overflow: hidden;
  border: 1px solid #e3e8e5;
  border-radius: 8px;
  background:
    radial-gradient(circle at 85% 30%, rgba(47, 111, 85, 0.14), transparent 28%),
    linear-gradient(135deg, #ffffff 0%, #f7faf8 55%, #eef6f1 100%);
}

.deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.deco-a {
  top: -60px;
  right: 120px;
  width: 200px;
  height: 200px;
  border: 2px solid rgba(47, 111, 85, 0.18);
}

.deco-b {
  bottom: -70px;
  right: 40px;
  width: 160px;
  height: 160px;
  background: rgba(47, 111, 85, 0.1);
}

.banner-text {
  position: relative;
  padding: 34px 48px;
}

.banner-text p {
  margin: 0 0 8px;
  color: #2f6f55;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.banner-text h1 {
  margin: 0;
  margin-left: 36px;
  color: #20242a;
  font-size: 40px;
  font-weight: 800;
}

.banner-text span {
  display: block;
  margin-top: 10px;
  margin-left: 36px;
  color: #2f6f55;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.7em;
}
</style>
