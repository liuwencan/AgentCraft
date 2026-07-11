/**
 * Cookie 工具
 * <p>Sa-Token 默认走 Cookie，登录成功后服务端会下发 satoken=xxx；
 * 后续请求统一通过请求拦截器从这里读取，并塞进请求头 {@code satoken: xxx}。</p>
 */

const SATOKEN_KEY = 'satoken'

/**
 * 读取当前域名下某个 cookie 的值。
 * @param {string} name
 * @returns {string} 没找到返回空串
 */
export function getCookie(name) {
  if (typeof document === 'undefined' || !name) return ''
  const target = `${name}=`
  const parts = document.cookie ? document.cookie.split(';') : []
  for (const raw of parts) {
    const c = raw.trim()
    if (c.startsWith(target)) {
      return decodeURIComponent(c.substring(target.length))
    }
  }
  return ''
}

/**
 * 写入 cookie（默认 path=/, 30 天）
 */
export function setCookie(name, value, { days = 30, path = '/', sameSite = 'Lax' } = {}) {
  if (typeof document === 'undefined') return
  const expires = new Date(Date.now() + days * 86400000).toUTCString()
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=${path}; SameSite=${sameSite}`
}

/**
 * 删除某个 cookie
 */
export function delCookie(name, { path = '/' } = {}) {
  if (typeof document === 'undefined') return
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=${path}`
}

/**
 * 读取当前 Sa-Token
 */
export function getSatoken() {
  return getCookie(SATOKEN_KEY)
}

/**
 * 手动写入 satoken（部分场景下服务端不会下发 cookie，
 * 比如跨域 / 调试 mock，可以在此处手动塞，便于请求拦截器读取）
 */
export function setSatoken(token) {
  setCookie(SATOKEN_KEY, token)
}

/**
 * 清除 satoken
 */
export function clearSatoken() {
  delCookie(SATOKEN_KEY)
}

export const SATOKEN_COOKIE_KEY = SATOKEN_KEY
