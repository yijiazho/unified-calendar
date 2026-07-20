# ICS Calendar Client Compatibility

TASK-021 requires generated invitations to import successfully into Apple Calendar, Google Calendar,
and Outlook. Automated coverage parses each generated invitation with iCal4j, an independent RFC
5545 parser. That check supplements, but does not replace, the client-specific import tests below.

## Manual test procedure

1. Create a booking whose visitor name or notes contain a comma, semicolon, newline, and non-ASCII
   character.
2. Download `invite.ics` from the visitor confirmation email.
3. Import the file into each client and verify the summary, description, organizer, start, and end.
4. Reschedule the booking and import the updated attachment.
5. Verify that the existing event is updated instead of duplicated and that its new times are correct.

## Results

| Client | Booking import | Reschedule updates existing event | Evidence |
| --- | --- | --- | --- |
| Apple Calendar | Pending manual verification | Pending manual verification | Not recorded |
| Google Calendar | Pending manual verification | Pending manual verification | Not recorded |
| Outlook | Pending manual verification | Pending manual verification | Not recorded |

Do not mark TASK-021 calendar-client compatibility complete until all three rows contain the tested
client version, test date, result, and supporting screenshot or recording.
