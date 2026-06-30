/** 本文件封装前端通用 API 请求客户端。 */
const API_BASE =
  import.meta.env.VITE_API_BASE ??
  '/api'

const TOKEN_KEY = 'wms-auth-token'
const DEFAULT_TIMEOUT_MS = Number(import.meta.env.VITE_API_TIMEOUT_MS ?? 20000)

const statusMessageMap: Record<number, string> = {
  400: '请求参数有误',
  401: '未登录或登录已过期',
  403: '没有权限执行该操作',
  404: '请求的资源不存在',
  405: '请求方法不受支持',
  409: '数据状态冲突，请刷新后重试',
  500: '服务器处理失败，请稍后重试',
}

export class ApiRequestError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

export function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuthToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearAuthToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAuthToken()
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS)
  const externalSignal = options?.signal
  const abortFromExternal = () => controller.abort()
  if (externalSignal?.aborted) {
    controller.abort()
  } else {
    externalSignal?.addEventListener('abort', abortFromExternal, { once: true })
  }

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options?.headers ?? {}),
      },
    })

    const text = await response.text()
    let payload: { success?: boolean; message?: string; data?: T }
    try {
      payload = text ? JSON.parse(text) : { success: response.ok, message: response.statusText }
    } catch {
      payload = { success: response.ok, message: text || response.statusText }
    }

    if (!response.ok || payload.success === false) {
      throw new ApiRequestError(payload.message || statusMessageMap[response.status] || '请求失败', response.status)
    }

    return payload.data as T
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiRequestError('请求超时，请检查网络或稍后重试', 408)
    }
    throw error
  } finally {
    window.clearTimeout(timeoutId)
    externalSignal?.removeEventListener('abort', abortFromExternal)
  }
}
