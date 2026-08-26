import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { PublicFaqCategory } from '@/types/faq'

/*
 * 지원자 FAQ 공개 조회. 인증 불필요이며 페이징 없이 전체를 한 번에 받는다.
 * 활성 카테고리 × 활성 FAQ만, 노출 가능한 FAQ가 없는 카테고리는 서버에서 제외된다.
 */
export const faqApi = {
  fetchFaqs() {
    return apiClient.get<ApiResponse<PublicFaqCategory[]>>('/faqs')
  },
}
