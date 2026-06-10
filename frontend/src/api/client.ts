import axios from 'axios'

/** Base Axios instance for authenticated admin endpoints. */
const client = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

export default client
