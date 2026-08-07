<template>
  <div class="banner banner-4">
    <div class="accent-bar"></div>
    <div class="banner-text">
      <p>Be Different, Be Bold</p>
      <h1>신영증권 채용 <em>RECRUIT</em></h1>
      <span class="underline"></span>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.banner-4 {
  position: relative;
  height: 180px;
  overflow: hidden;
  border: 1px solid #e8ece9;
  border-radius: 8px;
  background: #ffffff;
}

.accent-bar {
  position: absolute;
  top: 0;
  left: 0;
  width: 6px;
  height: 100%;
  background: linear-gradient(180deg, #2f6f55, #7fc9a6);
}

.banner-text {
  padding: 40px 56px;
}

.banner-text p {
  margin: 0 0 10px;
  color: #97a59e;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.banner-text h1 {
  margin: 0;
  color: #20242a;
  font-size: 38px;
  font-weight: 800;
}

.banner-text h1 em {
  margin-left: 14px;
  color: #2f6f55;
  font-size: 22px;
  font-style: normal;
  font-weight: 700;
  letter-spacing: 0.3em;
  vertical-align: middle;
}

.underline {
  display: block;
  width: 64px;
  height: 3px;
  margin-top: 16px;
  background: #2f6f55;
}
</style>
