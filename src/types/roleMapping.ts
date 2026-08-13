// 관리자 권한 관리 (api-contract.md "관리자 권한 관리" 섹션)

/** 매핑 화면에서 부여 가능한 role. 단일 출처는 백엔드 RoleNames — 프론트에 role 목록을 하드코딩하지 않는다. */
export interface AssignableRole {
  name: string
  label: string
}

export interface DeptRoleMapping {
  id: number
  deptName: string
  roleName: string
}

/** userName/userDeptName은 loginId가 users에 있을 때만 채워지는 참고 표시(없으면 null — 최초 로그인 전 직원). */
export interface UserRoleMapping {
  id: number
  loginId: string
  roleName: string
  userName: string | null
  userDeptName: string | null
}

export interface DeptRoleMappingSaveRequest {
  deptName: string
  roleName: string
}

export interface UserRoleMappingSaveRequest {
  loginId: string
  roleName: string
}
