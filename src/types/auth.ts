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

export interface SignupUser {
  loginId: string,
  password: string,
  name: string,
  phoneNumber: string,
  email: string,
  ci: string
}