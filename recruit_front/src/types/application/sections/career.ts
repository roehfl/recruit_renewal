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
    currentSalary: number,
    resignationReason: string,
    sortOrder: number
}

export interface CareerRepuestItme {
    companyName: string,
    departmentName: string,
    positionTitle: string,
    employmentType: string,
    startDate: string,
    endDate: string,
    currentlyEmployed: boolean,
    promotionDate: string,
    currentSalary: number,
    resignationReason: string,
    sortOrder: number
}

export interface CareerReplaceRequest {
    careers: CareerRepuestItme[],
}

export interface CareerResponse {
    careers: CareerItem[],
}