# Security and privacy

This repository is **public**. Treat everything in it as readable by anyone.

## What must never be in this repo

- API keys, tokens, passwords, signing keys (`*.jks`, `*.keystore`, `*.pem`, `*.key`)
- `google-services.json`, `GoogleService-Info.plist`, service-account files
- `local.properties`, `.env` files, relay server secrets
- Real rider data: names, phone numbers, locations, ride logs, recordings
- Business or legal details that are not meant to be public

`.gitignore` blocks these files, and the CI build has a **secret check** that fails
if a private file is tracked or a key-like string appears in the code.

## Where secrets live instead

| Secret | Where it goes |
|---|---|
| Play Store signing key | Your own computer and a backup. Later, GitHub Actions encrypted secrets |
| Firebase config | `android/app/google-services.json` on your computer only (ignored by git) |
| Relay server settings | Environment variables on the host (Cloudflare or VM), never in the repo |
| Ride secret (per ride) | Created on the phone at ride start, shared by QR or code, never stored on a server |

If a secret is ever committed: treat it as leaked. Revoke or rotate it first, then
remove it from history. Deleting it in a new commit is not enough.

## Privacy rules for the app

- Collect the minimum. No ads, no selling data, no tracking SDKs.
- Voice is encrypted per ride (see `SessionCrypto`). The relay server sees only encrypted
  packets, who is connected and how much traffic there is.
- Location is shared only inside a ride, only while the ride is running, and expires after
  30 seconds.
- Nothing is recorded or stored on a server. Test logs stay on the phone.
- Before release: a privacy policy, a data-safety form for Google Play, and a way to
  delete an account and its data.

## Copying and the limits of protection

A public repository can be viewed, cloned and forked by anyone, and GitHub does not tell
the owner who cloned it. No setting can prevent copying of a public repo. What we do:

- **Copyright:** no license is granted, so by default all rights are reserved and
  copying for reuse is not allowed. The name, logo and brand files are not licensed.
- **Forks:** GitHub shows the owner a list of forks of a public repo, so those are visible.
- **Stronger protection:** make the repository private. Then only invited people can see
  the code, forking can be disabled, and the CI build still works (within GitHub's free
  limits). Releases to riders go through the Play Store, not through the repo.

## Reporting a problem

Email the project contact in the README. Please do not post security issues publicly.
