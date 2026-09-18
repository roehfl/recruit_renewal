export interface CareerItem {
    careerId: number
    companyName: string,
    departmentName: string,
    positionTitle: string,
    employmentType: string,
    startDate: string,
    endDate: string,
    currentlyEmployed: boolean,
    promotionDate: string,
    currentSalary: number | null,
    resignationReason: string,
    sortOrder: number
}

export interface CareerRepuestItme {
    companyName: string,
    departmentName: string,
    positionTitle: string,
    employmentType: string | null,
    startDate: string,
    endDate: string | null,
    currentlyEmployed: boolean,
    promotionDate: string,
    currentSalary: number | null,
    resignationReason: string,
    sortOrder: number
}

export interface CareerReplaceRequest {
    careers: CareerRepuestItme[],
}

export interface CareerResponse {
    careers: CareerItem[],
}