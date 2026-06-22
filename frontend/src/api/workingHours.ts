import client from './client'
import type { WorkingHours } from '../types'

/** Fetches the admin's saved working-hour windows. */
export const getWorkingHours = () => client.get<WorkingHours[]>('/working-hours')

/** Replaces all working-hour windows (full replacement semantics). */
export const saveWorkingHours = (hours: WorkingHours[]) =>
  client.put('/working-hours', hours)
