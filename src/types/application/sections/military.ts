//기본적인 Military 형식
export interface MilitaryResponse {
    militaryId: number,
    militarySubjectType: string | null,
    serviceType: string | null,
    militaryBranch: string | null,
    rank: string | null,
    serviceStartDate: string | null,
    serviceEndDate: string | null,
    nonServiceReason: string | null
}

// request에 들어가는 Military 형식
export interface MilitaryRepuest {
    militarySubjectType: string
    serviceType: string | null,
    militaryBranch: string | null,
    rank: string | null,
    serviceStartDate: string | null,
    serviceEndDate: string | null,
    nonServiceReason: string | null
}