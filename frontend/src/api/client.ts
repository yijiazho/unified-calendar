import axios from 'axios'

/** Base Axios instance for authenticated admin endpoints. */
const client = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

client.interceptors.response.use(
  res => res,
  err => {
    const onAuthPage = ['/', '/login', '/signup'].includes(window.location.pathname)
    if (err.response?.status === 401 && !onAuthPage) {
      window.location.href = '/login'
    }
    return Promise.reject(err)
  },
)

export default client
