# Human Phase Report HTML Template

Each phase report HTML must use this structure

1. Header
   - Project name
   - Phase name
   - Status badge
   - Generatedupdated date

2. Executive Summary
   - 3~5 line summary
   - What was completed
   - Whether the phase can move forward

3. Scope Cards
   - Implemented
   - Not implemented
   - Deferred

4. Architecture  Domain Section
   - Entity relationship summary
   - Main classes
   - Data ownership rules

5. API Section
   - Method
   - Path
   - Purpose
   - Request
   - Response

6. Validation  Policy Section
   - Business rules
   - State transition rules
   - Config flag rules

7. Test Section
   - Test command
   - Test result
   - Regression tests

8. Remaining Issues
   - Open issues
   - Deferred decisions

9. Next Phase Recommendation

HTML style
- Use inline CSS.
- No external libraries.
- Use tables for APIs and changed files.
- Use cards for summary blocks.
- Use badges for status.
- Keep it readable in a browser without build tools.