# Rules for AI coding tools in this repo

- Never read, print, copy or send the contents of `.env*`, keystores, `google-services.json`,
  `local.properties`, signing or service-account files, or anything in `docs/private/`.
- Never put keys, tokens, passwords or personal data into any file, commit or message.
- If you find a secret in the repo, stop and tell the owner so it can be revoked.
- Ask the owner before any step that changes the repo, GitHub settings or a server.

## Owner's working rules
- Repo stays private for now. Nothing moves toward public without the owner's explicit say-so.
- Before every push: list the changed files and the secret-scan result, then wait for the owner's OK.
- After each green build: send the APK to the owner in chat. Every build shows its Build number.
