export type nationalityType =
    | "DOMESTIC"
    | "FOREIGN"
    | ""

export type veteranStatus = 
    | "SUBJECT"
    | "NOT_SUBJECT"
    | ""

export type disabilityStatus = 
    | "SUBJECT"
    | "NOT_SUBJECT"
    | ""

export interface BasicInfoParams { 
    nameKorean: string
    nameEnglish?: string
    nationalityType: nationalityType
    countryCode?: string
    birthDate: string
    mobilePhone: string
    emergencyPhone?: string
    email: string
    veteranStatus: veteranStatus
    disabilityStatus: disabilityStatus
    disabilityGradeCode?: string
    disabilityTypeCode?: string
    zipCode?: string
    addressBasic?: string
    addressDetail?: string
}

export interface ApiResponseBasicInfoResponse {
  data: BasicInfoParams
  message: string
  success: boolean
}