export interface CertificateItem {
  certificateId?: number
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumber?: string
  expiredDate?: string
  scoreOrGrade?: string
}

export interface CertificateRequestItem {
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumber?: string
  expiredDate?: string
  scoreOrGrade?: string
  sortOrder: number
}

export interface CertificateReplaceRequest {
  certificates: CertificateRequestItem[]
}

export interface CertificateResponse {
  certificateId: number
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumber?: string
  expiredDate?: string
  scoreOrGrade?: string
  sortOrder: number
}
