import { apiClient } from './client'
import type { ApiResponse } from '@/types/api'
import type { Faq, FaqCategory, FaqCategorySaveRequest, FaqSaveRequest } from '@/types/faq'

/*
 * 관리자 FAQ 관리 전용(/api/admin/faq-categories/**, /api/admin/faqs/**, ROLE_ADMIN·ROLE_RECRUIT_ADMIN).
 * 백엔드가 GET/POST만 쓰는 관례라 수정·삭제·정렬도 POST다. 삭제는 active=false soft delete다.
 * sortOrder는 요청으로 보내지 않는다. 생성 시 서버가 자동 부여하고 변경은 reorder 전용이다.
 */
export const adminFaqApi = {
  fetchCategories() {
    return apiClient.get<ApiResponse<FaqCategory[]>>('/admin/faq-categories')
  },

  createCategory(request: FaqCategorySaveRequest) {
    return apiClient.post<ApiResponse<FaqCategory>>('/admin/faq-categories', request)
  },

  updateCategory(categoryId: number, request: FaqCategorySaveRequest) {
    return apiClient.post<ApiResponse<FaqCategory>>(`/admin/faq-categories/${categoryId}`, request)
  },

  deleteCategory(categoryId: number) {
    return apiClient.post<ApiResponse<void>>(`/admin/faq-categories/${categoryId}/delete`)
  },

  /* ids는 전체 카테고리 id 집합과 정확히 일치해야 한다(부분 정렬 불가). */
  reorderCategories(ids: number[]) {
    return apiClient.post<ApiResponse<void>>('/admin/faq-categories/reorder', { ids })
  },

  fetchFaqs(categoryId: number) {
    return apiClient.get<ApiResponse<Faq[]>>('/admin/faqs', { params: { categoryId } })
  },

  createFaq(request: FaqSaveRequest) {
    return apiClient.post<ApiResponse<Faq>>('/admin/faqs', request)
  },

  updateFaq(faqId: number, request: FaqSaveRequest) {
    return apiClient.post<ApiResponse<Faq>>(`/admin/faqs/${faqId}`, request)
  },

  deleteFaq(faqId: number) {
    return apiClient.post<ApiResponse<void>>(`/admin/faqs/${faqId}/delete`)
  },

  /* ids는 해당 카테고리의 전체 FAQ id 집합과 정확히 일치해야 한다. */
  reorderFaqs(categoryId: number, ids: number[]) {
    return apiClient.post<ApiResponse<void>>('/admin/faqs/reorder', { categoryId, ids })
  },
}
