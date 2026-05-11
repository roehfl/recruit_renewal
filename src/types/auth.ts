export interface LoginRequest {
  loginId: string
  password: string
}

export interface LoginUser {
  loginId: string
  name: string
  deptName: string
  userType: 'Applicant' | 'Employee'
  roles: string[]
}
