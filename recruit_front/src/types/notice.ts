export interface NoticeListItem {
  id: number
  title: string
  pinned: boolean
  content: string
  createdAt: string
}

export interface NoticeSearchParams {
  page: number
  size: number
  searchType?: 'ALL' | 'TITLE' | 'CONTENT'
  keyword?: string
}

export interface NoticeDetail {
  id: number
  title: string
  contentHtml: string
  pinned: boolean
  createdAt: string
}

/* ---------- 관리자 공지 관리 (/admin/notices, /board/notices 쓰기) ---------- */

/**
 * createdBy·updatedBy 는 백엔드에 AuditorAware 빈이 없어 항상 null 이다.
 * 화면에서 작성자를 보여주려면 백엔드 감사 주체 설정이 먼저 필요하다.
 */
export interface AdminNoticeListItem {
  id: number
  title: string
  pinned: boolean
  deleted: boolean
  createdAt: string
  createdBy: string | null
  updatedAt: string | null
  updatedBy: string | null
}

export interface AdminNoticeDetail {
  id: number
  title: string
  contentHtml: string
  pinned: boolean
  deleted: boolean
  createdAt: string
  createdBy: string | null
  updatedAt: string | null
  updatedBy: string | null
}

export interface AdminNoticeSearchParams {
  page: number
  size: number
  searchType?: 'ALL' | 'TITLE' | 'CONTENT'
  keyword?: string
  /** 생략하면 전체, true 면 삭제됨만, false 면 정상만. */
  deleted?: boolean
  pinnedOnly?: boolean
}

export interface NoticeSaveRequest {
  title: string
  content: string
  isPinned: boolean
}
