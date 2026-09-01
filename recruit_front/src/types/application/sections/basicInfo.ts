export type nationalityType =
    | "DOMESTIC"
    | "FOREIGN"

export type veteranStatus = 
    | "SUBJECT"
    | "NOT_SUBJECT"

export type disabilityStatus = 
    | "SUBJECT"
    | "NOT_SUBJECT"

export interface BasicInfoParams { 
    nameKorean: string
    nameEnglish?: string
    nationalityType: nationalityType | undefined
    countryCode?: string | undefined
    birthDate: string | undefined
    mobilePhone: string
    emergencyPhone?: string
    email: string
    veteranStatus: veteranStatus | undefined
    veteranType?: string | undefined
    disabilityStatus: disabilityStatus | undefined
    disabilityGradeCode?: string | undefined
    disabilityTypeCode?: string | undefined
    zipCode?: string
    addressBasic?: string
    addressDetail?: string
    applicationRouteCode?: string | undefined
}

export interface ApiResponseBasicInfoResponse {
    data: BasicInfoParams
    message: string
    success: boolean
}

export type attachmentType =
    "RESUME" | "TRANSCRIPT" | "GRADUATION_CERTIFICATE" | "CAREER_CERTIFICATE" | "CAREER_DESCRIPTION" |
    "EMPLOYMENT_CERTIFICATE" | "CERTIFICATE_PROOF" | "LANGUAGE_SCORE_REPORT" | "PORTFOLIO" | "ETC"

export type sectionType =
    "BASIC_INFO" | "APPLICATION" | "EDUCATION" | "CAREER" | "CERTIFICATE" | "LANGUAGE" | 
    "MILITARY" | "AWARD" | "GAP_PERIOD" | "QUESTION_ANSWER" | "ATTACHMENT" | "ETC"

export interface AttachmentFileRequest {
    applicationId: number
    attachmentType: attachmentType
    sectionType: sectionType
    sectionRecordId?: number
}

export interface AttachmentResponse {
    attachmentId: number
    attachmentType: attachmentType
    sectionType: sectionType
    sectionRecordId: number
    originalFileName: string
    contentType: string
    fileSize: number
    sortOrder: number
}

export interface ApiResponseListAttachmentResponse {
    data: AttachmentResponse[]
    message: string
    success: boolean
}
