
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
  gradePoint: number
  maxGradePoint: number
  majorGradePoint?: number
  majorMaxGraedPoint?: number
}

export interface EducationRequest {
  educationLevel: educationLevelType
  schoolName: string
  majorName?: string
  degreeName?: string
  admissionDate?: string
  graduationDate?: string
  graduationStatus: graduationStatus
  dayNightType?: dayNightType
  campusType?: campusType
  transfer?: boolean
  countryCode?: string
  sortOrder: number
  semesterGrades?: semesterGradeItem[]
  schoolId: number
  overallGradePoint?: number
  overallMaxGradePoint?: number
  overallMajorGradePoint?: number
  overallMajorMaxGradePoint?: number
}

export interface EducationResponse {
  educationId: number
  educationLevel: educationLevelType
  schoolName: string
  majorName: string
  degreeName: string
  admissionDate: string
  graduationStatus: graduationStatus
  dayNightType: dayNightType
  campusType: campusType
  transfer: boolean
  countryCode: string
  schoolId: number
  sortOrder: number
  overallGradePoint?: number
  overallMaxGradePoint?: number
  overallMajorGradePoint?: number
  overallMajorMaxGradePoint?: number
  semesterGrades: semesterGradeItem[]
}