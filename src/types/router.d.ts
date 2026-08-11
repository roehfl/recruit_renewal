import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /*
     * 헤더 메뉴 활성 표시에 사용할 메뉴 path.
     * 메뉴에 등록되지 않은 화면(공고 상세, 지원서 작성 등)에서
     * 어느 메뉴를 활성으로 볼지 지정한다. 미지정 시 현재 경로를 그대로 사용한다.
     */
    activeMenuPath?: string
  }
}
