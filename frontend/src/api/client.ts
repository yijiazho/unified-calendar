import axios from 'axios'
import type { AxiosInstance } from 'axios'

/** Creates an Axios instance pointed at the API proxy with consistent defaults. */
export function makeClient(withCredentials: boolean): AxiosInstance {
  return axios.create({ baseURL: '/api', withCredentials })
}

const client = makeClient(true)

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
