export interface LoginRequest {
  loginId: string
  password: string
}

export interface LoginUser {
  loginId: string
  name: string
  deptName: string
  userType: 'Applicant' | 'Employee'
  phoneNumber: string
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

export interface checkEmailRequest {
  success: boolean,
  data: {available: boolean},
  message: string
}