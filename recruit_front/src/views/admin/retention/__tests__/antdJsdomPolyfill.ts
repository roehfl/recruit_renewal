/*
 * ant-design-vue의 일부 컴포넌트(a-descriptions 등)는 반응형 레이아웃 계산에 window.matchMedia를
 * 쓰는데 jsdom에는 구현이 없어 그대로 마운트하면 unhandled rejection이 난다. 테스트 전용 최소 폴리필.
 */
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList) as typeof window.matchMedia
}
