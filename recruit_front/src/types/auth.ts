export interface LoginRequest {
  loginId: string
  password: string
}

/** GET /auth/login-options. 로그인 화면이 NICE 팝업을 띄울지 정하는 데 쓴다. */
export interface LoginOptions {
  twoFactorEnabled: boolean
}

export interface LoginUser {
  loginId: string
  name: string
  deptName: string
  userType: 'Applicant' | 'Employee'
  roles: string[]
}