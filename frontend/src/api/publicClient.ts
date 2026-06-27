import { makeClient } from './client'

/** Axios instance for unauthenticated visitor endpoints — no 401 redirect. */
const publicClient = makeClient(false)

export default publicClient
