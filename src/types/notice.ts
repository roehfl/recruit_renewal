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
  title: String
  contentHtml: String
  pinned: boolean
  createdAt: string
}
