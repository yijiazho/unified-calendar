/** Returns the IANA timezone string reported by the browser (e.g. "America/New_York"). */
export function useBrowserTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}
