export interface CurrentUser { id:string; username:string; displayName:string; departmentId:string; roles:string[]; permissions:string[] }
export interface ApiResponse<T> { code:number; message:string; data:T; requestId:string }
export interface Tokens { accessToken:string; refreshToken:string; expiresIn:number; user:CurrentUser }
export type Dict = Record<string, any>
