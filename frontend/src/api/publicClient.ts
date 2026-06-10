import axios from 'axios'

/** Axios instance for unauthenticated visitor endpoints — no 401 redirect. */
const publicClient = axios.create({
  baseURL: '/api',
  withCredentials: false,
})

export default publicClient
