export interface AddressSearchParams {
  keyword: string
  currentPage?: number
  countPerPage?: number
}

export interface AddressItem {
  roadAddr: string      // 전체 도로명 주소
  jibunAddr: string     // 지번주소
  zipNo: string         // 우편번호
  siNm: string          // 시도명
  sggNm: string         // 시군구명
  emdNm: string         // 읍면동명
  bdNm: string          // 건물명
  engAddr: string       // 도로명주소(영문)
}

export interface AddressSearchResponse {
  totalCount: number
  currentPage: number
  countPerPage: number
  addresses: AddressItem[]
}