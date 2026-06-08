/**
 * API客户端基础配置和通用请求函数
 * 统一处理所有后端API请求，包含错误处理和响应解析
 */

// 后端API基础地址
const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://127.0.0.1:8080/api'
const TOKEN_KEY = 'wms-auth-token'

export function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearAuthToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 通用API请求函数
 * @param path API路径，相对于API_BASE
 * @param options fetch请求配置项，可选
 * @returns Promise<T> 返回解析后的数据对象
 * @throws Error 如果请求失败或后端返回错误，则抛出异常
 */
export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAuthToken()
  // 发起fetch请求，自动添加Content-Type头
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options?.headers ?? {}),
    },
    ...options,
  })

  // 解析响应JSON数据
  const text = await response.text()
  const payload = text ? JSON.parse(text) : { success: response.ok, message: response.statusText }
  // 检查请求是否成功，如果响应状态码非2xx或后端返回success:false则抛出错误
  if (!response.ok || payload.success === false) {
    throw new Error(response.status === 401 ? '未登录或登录已过期' : payload.message || '请求失败')
  }

  // 返回后端返回的数据部分
  return payload.data as T
}
