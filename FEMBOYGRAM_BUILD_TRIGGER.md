# FemboyGram Android build gate

This file documents the source state that must be verified before an APK is produced.

The branding/source pass has been committed to the `femboygram` branch:
- FemboyGram application id, label, launcher icon and splash artwork
- FemboyGram onboarding and localized app names
- FemboyGram default server entry and server display-name cleanup
- Removal of raw endpoint addresses from the server profile display
- FemboyGram README and support wording

**Verification is still pending.** Do not publish or distribute an APK until the Android workflow succeeds on the current branch head and the resulting APK metadata is checked.
