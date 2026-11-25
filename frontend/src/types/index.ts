/**
 * 当前登录用户：身份、部门与扁平化权限码；can() 只读 roles/permissions，ADMIN 直接放行。
 */
export interface CurrentUser { id:string; username:string; displayName:string; departmentId:string; roles:string[]; permissions:string[] }
/**
 * 后端统一响应契约 { code, message, data, requestId }；http 拦截器已拆包，
 * 页面拿到的直接是 data，requestId 用于排障与审计对账。
 */
export interface ApiResponse<T> { code:number; message:string; data:T; requestId:string }
/**
 * 登录/刷新返回的令牌组：access 进请求头，refresh 只用于换新 access；
 * expiresIn 为 access 有效秒数，前端不单独计时，以 401 触发刷新。
 */
export interface Tokens { accessToken:string; refreshToken:string; expiresIn:number; user:CurrentUser }
/**
 * 万能行数据字典：各模块接口字段差异大，列表行统一用它承接；
 * 代价是失去字段级检查，只在确实需要动态结构时使用。
 */
export type Dict = Record<string, any>
