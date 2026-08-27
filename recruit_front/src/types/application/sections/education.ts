
export type educationLevelType =
  | "HIGH_SCHOOL"
  | "COLLEGE"
  | "UNIVERSITY"
  | "MASTER"
  | "DOCTOR"

export type graduationStatus =
  | "GRADUATED"
  | "EXPECTED"
  | "ENFOLLED"
  | "LEAVE_OF_ABSENCE"
  | "DROPPED_OUT"
  | "COMPLETED"

export type schoolSource =
  | "NEIS"
  | "UNIV_INFO"
  | "UNIV_DEPT"

export type dayNightType = 
  | "DAY"
  | "NIGHT"
  | "CYBER"
  | "UNKNOWN"

export type campusType = 
  | "MAIN"
  | "BRANCH"
  | "UNKNOWN"

export interface semesterGradeItem { 
  schoolYear: number
  semester: number
  earnedCredits?: number
  gradePoint: number | null
  maxGradePoint: number | null
  majorGradePoint?: number
  majorMaxGraedPoint?: number
}

export interface EducationItem {
  educationId?: number
  educationLevel: educationLevelType | undefined
  schoolName: string
  majorName?: string | undefined
  additionalMajorType?: string
  additionalMajorName?: string
  thesisTitle?: string | undefined
  degreeName?: string
  admissionDate?: string
  graduationDate?: string
  graduationStatus: graduationStatus | undefined
  dayNightType?: dayNightType
  campusType?: campusType
  transfer?: boolean
  countryCode?: string
  schoolCode?: string | null
  schoolSource?: schoolSource | null
  semesterGrades?: semesterGradeItem[]
  overallGradePoint?: number | null
  overallMaxGradePoint?: number | null
  overallMajorGradePoint?: number | null
  overallMajorMaxGradePoint?: number | null
}

export interface EducationRequestItem {
  educationLevel: educationLevelType | undefined
  schoolName: string
  majorName?: string
  additionalMajorType?: string
  additionalMajorName?: string
  thesisTitle?: string
  degreeName?: string
  admissionDate?: string
  graduationDate?: string
  graduationStatus: graduationStatus | undefined
  dayNightType?: dayNightType
  campusType?: campusType
  transfer?: boolean
  countryCode?: string
  sortOrder: number
  semesterGrades?: semesterGradeItem[]
  schoolCode?: string | null
  schoolSource?: schoolSource | null
  overallGradePoint?: number | null
  overallMaxGradePoint?: number | null
  overallMajorGradePoint?: number | null
  overallMajorMaxGradePoint?: number | null
}

export interface EducationReplaceRequest {
  educations: EducationRequestItem[]
}


export interface EducationResponse {
  educationId: number
  educationLevel: educationLevelType
  schoolName: string
  majorName: string
  additionalMajorType: string
  additionalMajorName: string
  thesisTitle: string
  degreeName: string
  admissionDate: string
  graduationDate: string
  graduationStatus: graduationStatus
  dayNightType: dayNightType
  campusType: campusType
  transfer: boolean
  countryCode: string
  schoolCode: string | null
  schoolSource: schoolSource | null
  sortOrder: number
  semesterGrades: semesterGradeItem[]
  overallGradePoint: number
  overallMaxGradePoint: number
  overallMajorGradePoint: number
  overallMajorMaxGradePoint: number
}

export interface shcoolSerachParams { 
  q: string
  educationLevel: educationLevelType
}

export interface schoolItem {
  schoolCode: string
  schoolName: string
  schoolSource: schoolSource
  region: string | null
}

export interface schoolResponse {
  success: boolean
  data: schoolItem[] | []
  message: string
}