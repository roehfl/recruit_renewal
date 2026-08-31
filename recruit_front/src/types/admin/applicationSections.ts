export interface AdminBasicInfoResponse { 
  basicInfoId: number
  nameKorean: string
  nameEnglish: string
  nationalityType: "DOMESTIC" | "FOREIGN"
  countryCode: string
  birthDate: string
  mobilePhone: string
  emergencyPhone: string
  email: string
  veteranStatus: "SUBJECT" | "NOT_SUBJECT"
  veteranType: string
  disabilityStatus: "SUBJECT" | "NOT_SUBJECT"
  disabilityGradeCode: string
  disabilityTypeCode: string
  zipCode: string
  addressBasic: string
  addressDetail: string
}

export interface AdminMilitaryResponse { 
  militaryId: number
  militarySubjectType: "SUBJECT" | "NOT_SUBJECT" | "COMPLETED" | "EXEMPTED"
  serviceType: string
  militaryBranch: string
  rank: string
  serviceStartDate: string
  serviceEndDate: string
  nonServiceReasonMasked: string
}

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
  gradePoint: number | null
  maxGradePoint: number | null
  majorGradePoint?: number
  majorMaxGraedPoint?: number
}
export interface AdminEducationResponse { 
  educationId: number
  educationLevel: educationLevelType
  schoolName: string
  majorName: string
  additionalMajorType: string
  additionalMajorName: string
  thesisTitle: string
  admissionDate: string
  graduationDate: string
  graduationStatus: graduationStatus
  dayNightType: dayNightType
  campusType: campusType
  transfer: boolean
  countryCode: string
  schoolId: number
  sortOrder: number
  semesterGrades: semesterGradeItem[]
  overallGradePoint: number
  overallMaxGradePoint: number
  overallMajorGradePoint: number
  overallMajorMaxGradePoint: number
}

export interface CareerItem {
    careerId: number
    companyName: string,
    departmentName: string,
    positionTitle: string,
    employmentType: string,
    startDate: string,
    endDate: string,
    promotionDate: string,
    currentlyEmployed: boolean,
    currentSalary: number,
    resignationReason: string,
    sortOrder: number
}
export interface AdminCareerResponse { 
  careers: CareerItem[],
}

export interface AdminCertificateResponse { 
  certificateId: number
  certificateName: string
  issuingOrganization: string
  acquiredDate: string
  certificateNumberMasked?: string
  expiredDate?: string
  scoreOrGrade?: string
  sortOrder: number
}

export interface AdminLanguageResponse { 
  languageId: number
  languageName: string
  testName: string
  scoreOrGrade?: string
  conversationalAbility?: string
  examDate: string
  expiredDate?: string
  issuingOrganization?: string
  sortOrder: number
}

export interface AdminAwardResponse { 
  awardId: number
  awardName: string
  awardingOrganization: string
  awardDate: string
  description?: string
  sortOrder: number
}

export interface AdminGapPeriodResponse {
  gapPeriodId: number
  startDate: string
  endDate: string
  gapType: 'EDUCATION' | 'CAREER' | 'OTHER'
  reason: string
  description?: string
  sortOrder: number
}

export interface AdminApplicationAnswerResponse { 
  questionId: number
  questionText: string
  category: 'SELF_INTRODUCTION' | 'GENERAL' | 'JOB_SPECIFIC' | 'ETC'
  answerType: 'SHORT_TEXT' | 'LONG_TEXT'
  required: boolean
  minLength?: number | null
  maxLength?: number | null
  sortOrder: number
  answerId?: number | null
  answerText?: string | null
  updatedAt?: string | null
}

export type attachmentType =
    "RESUME" | "TRANSCRIPT" | "GRADUATION_CERTIFICATE" | "CAREER_CERTIFICATE" | "CAREER_DESCRIPTION" |
    "EMPLOYMENT_CERTIFICATE" | "CERTIFICATE_PROOF" | "LANGUAGE_SCORE_REPORT" | "PORTFOLIO" | "ETC"
export type sectionType =
    "BASIC_INFO" | "APPLICATION" | "EDUCATION" | "CAREER" | "CERTIFICATE" | "LANGUAGE" | 
    "MILITARY" | "AWARD" | "GAP_PERIOD" | "QUESTION_ANSWER" | "ATTACHMENT" | "ETC"
export interface AdminAttachmentResponse { 
  attachmentId: number
  attachmentType: attachmentType
  sectionType: sectionType
  sectionRecordId: number
  originalFileName: string
  contentType: string
  fileSize: number
  sortOrder: number
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

export const SECTION_MAP: Record<
  string, 
  Record<string, Record<string, string>>
> = {
  BASIC_INFO: {},
  MILITARY: {
    militaryBranchType: {
      ARMY: '육군',
      NAVY: '해군',
      AIR_FORCE: '공군',
      MARINE: '해병대',
      FIRE_SERVICE: '의무소방',
      POLICE: '의무경찰',
      ETC: '기타',
    },
    militaryServiceType: {
      ACTIVE_DUTY: '현역복무',
      NON_COMMISSIONED_OFFICER: '부사관',
      OFFICER: '장교',
      PROFESSIONAL_RESEARCH: '전문연구요원',
      INDUSTRIAL_TECHNICAL: '산업기능요원',
      PUBLIC_SERVICE: '공익근무요원',
      SOCIAL_SERVICE: '사회복무요원',
      SUPPLEMENTARY: '보충역',
      ETC: '기타',
    },
    militaryRankType: {
      PRIVATE: '이병',
      PRIVATE_FIRST_CLASS: '일병',
      CORPORAL: '상병',
      SERGEANT: '병장',
      STAFF_SERGEANT: '하사',
      SERGEANT_FIRST_CLASS: '중사',
      MASTER_SERGEANT: '상사',
      WARRANT_OFFICER: '준위',
      SECOND_LIEUTENANT: '소위',
      FIRST_LIEUTENANT: '중위',
      CAPTAIN: '대위',
      MAJOR: '소령',
      ETC: '기타',
    }
  },
  EDUCATION: {
    educationLevelType: {
      HIGH_SCHOOL: '고등학교',
      COLLEGE: '전문대학교',
      UNIVERSITY: '대학교',
      MASTER: '대학원(석사)',
      DOCTOR: '대학원(박사)',
    },
    graduationStatus: {
      GRADUATED: '졸업',
      EXPECTED: '졸업예정',
      ENFOLLED: '재학',
      LEAVE_OF_ABSENCE: '휴학',
      DROPPED_OUT: '중퇴',
      COMPLETED: '수료',
    },
    dayNightType: {
      DAY: '주간',
      NIGHT: '야간',
      CYBER: '사이버',
    },
    campusType: {
      MAIN: '본교',
      BRANCH: '분교',
    },
  },
  CAREER: {
    employmentType: {
      FULL_TIME: '정규직',
      CONTRACT: '계약',
      INTERN: '인턴',
      FREELANCE: '프리랜서',
      PART_TIME: '파트',
      ETC: '기타',
    },
  },
  CERTIFICATE: {},
  LANGUAGE: {},
  AWARD: {},
  GAP_PERIOD : {
    gapType: {
      EDUCATION: '학업', 
      CAREER: '경력', 
      OTHER: '기타',
    }
  },
  QUESTION_ANSWER : {},
  ATTACHMENT : {},
}
export const getLabel = (sectionType: string, field: string, value: string, ) => {
  return SECTION_MAP[sectionType]?.[field]?.[value] ?? value;
};
