# Rylaide: build plan from here to the Play Store

Last checked: 10 October 2026. Store rules and free tiers change, so anything
marked **verify** should be re-checked when we reach that step.

## 1. Where we are

- Design: complete in Figma (light and dark, about 400 screens per theme).
- Code: Android project is renamed (`com.rylaide.app`). Step 1, the offline
  intercom, is written but **not yet compiled or tested on phones**.
- Everything else (accounts, maps, groups, navigation, safety) is still to build.

## 2. Facts checked today that change the plan

| Finding | What it means for us |
|---|---|
| New apps and updates must target **Android 16 (API 36)** from 31 Aug 2026 ([Play policy](https://support.google.com/googleplay/android-developer/answer/11926878)). The project currently targets API 34. | Raise `compileSdk` and `targetSdk` to 36 and upgrade the Android Gradle Plugin, Kotlin and Compose with Android Studio's upgrade assistant. Done in Step 4, before any UI work, so we never build on an old target. |
| New personal developer accounts need a closed test with **at least 12 testers opted in for 14 consecutive days** before applying for production ([Play help](https://support.google.com/googleplay/android-developer/answer/14151465)). A tester who leaves restarts the count. | Start recruiting 20+ riders early. The 14 days run while we keep building. |
| Oracle cut the Always Free Ampere server to **2 CPUs and 12 GB** from 15 June 2026, with no public announcement ([InfoQ](https://infoq.com/news/2026/07/oracle-cloud-free-tier-limits/), [Linuxiac](https://linuxiac.com/oracle-quietly-cuts-free-tier-ampere-a1-resources-in-half/)). Whether older accounts keep 4 CPUs and 24 GB is unclear. | Our plan used the old size. 2 CPUs and 12 GB is still plenty for voice-only LiveKit at our scale, but plan for it and expect Oracle to change terms again. Keep the server setup scripted so it can move to another host. |
| Firebase free plan (Spark): Firestore gives **50,000 reads and 20,000 writes a day**. **Phone sign-in and Cloud Storage are not available** without the paid Blaze plan ([Firebase pricing](https://firebase.google.com/pricing)). | Matches the plan: Google and email sign-in only at launch, no chat photo upload at launch. The read and write caps are the first limit we will hit, so cache data on the phone and keep reads low. |
| A `microphone` or `location` foreground service **cannot be started from the background** ([Android docs](https://developer.android.com/about/versions/14/changes/fgs-types-required)). | The ride service must start while the app is open, when the rider taps Start ride. That fits our flow. |
| Background location needs a Play declaration, an in-app disclosure, and a video of 30 seconds or less ([Play policy](https://support.google.com/googleplay/android-developer/answer/9799150)). | Design intent: start the ride service while the app is visible, so we should not need the background location permission at all. **Verify on real phones.** If Android stops location when the screen is off, we add the declaration. |

## 3. Final stack

| Part | Choice | Key or account needed | Cost |
|---|---|---|---|
| App | Kotlin, Jetpack Compose, Material 3, native Android first | None | Free |
| Offline voice and positions | Google Nearby Connections | None | Free |
| Voice codec | Opus, 16 kbit/s speech | None | Free |
| Online voice | LiveKit, self-hosted | Key pair we generate ourselves | Free on our server |
| Server | Oracle Always Free (2 CPUs, 12 GB) | Oracle account (asks for a card to verify, does not charge for free resources) | Free |
| Sign-in, chat, ride plans, push, crash reports | Firebase (Spark plan) | `google-services.json` from Firebase | Free within limits |
| Maps | MapLibre Native with OpenFreeMap tiles, styled from the Figma colours | None | Free |
| Routing | OSRM on our server, OpenRouteService as backup | One OpenRouteService key | Free tier |
| Place search | Nominatim or Photon on our server | None | Free |
| Weather | MET Norway (Open-Meteo is non-commercial only, so not used) | None, but needs a proper User-Agent | Free |
| Local data | Room database and DataStore | None | Free |
| Source and builds | GitHub and GitHub Actions | Your account | Free |

Money: **$25 once** for the Google Play developer account. Target running cost
is zero. The risks to that are in section 7.

## 4. Models

Everything a rider needs while riding runs **on the phone**. Cloud models are
optional extras and never needed mid-ride.

| Job | Model or tool | Where it runs | Notes |
|---|---|---|---|
| Voice compression | Opus | Phone | Replaces raw audio from Step 1 |
| Voice commands ("Hey Rylaide") | Android on-device speech recognition | Phone | Needs a downloaded language pack |
| Voice commands on older phones | Vosk small models | Phone | About 50 MB per language, lower accuracy |
| Spoken prompts and turn-by-turn | Android text-to-speech | Phone | Many languages built in |
| Short summaries and the on-phone assistant | Gemini Nano through ML Kit GenAI | Phone, **supported phones only** | Not every phone has it, so every feature needs a non-AI fallback |
| QR join and scanning | ML Kit barcode scanning | Phone | |
| Odometer and bill scanning | ML Kit text recognition | Phone | |
| Wind and engine noise | RNNoise (small noise-removal model) | Phone | **Candidate, not tested.** Step 3 decides. The simple loudness check does not separate wind from speech |
| "Ask Rylaide" chat for hard questions | Gemini Flash-Lite through the Gemini API | Cloud, optional | **Verify the current free limits** at Step 12. Shared across all users, may use what we send. Never send location, names or ride data |

## 5. Steps to deployment

Each step ends with something we can test, and each is committed to GitHub.
Estimates are full-time weeks for one developer and are planning ranges, not
promises. Part-time is roughly double.

| # | Step | What we build | Done when | Weeks |
|---|---|---|---|---|
| 1 | Offline intercom | Nearby link, mic, playback (written) | Two phones talk with mobile data off | 1 |
| 2 | Better voice | Opus, mixing several talkers, jitter buffer | Four phones in one group, clear voice | 1 |
| 3 | Rides in the background | Foreground service, Bluetooth headset routing, wired earphones, battery tests, noise test | A 30 minute test with the screen off and a real helmet headset | 1 |
| 4 | Foundation | Upgrade to API 36, design tokens from Figma, Day/Evening/Night themes, accent colours, Glove Mode, shared components, navigation | App shell matches the Figma look in both themes | 2 |
| 5 | Accounts and onboarding | Splash, Firebase sign-in (Google and email), permissions screens, quick setup, Room and DataStore | New user reaches Home on a clean phone | 2 |
| 6 | Garage, fuel and checklist | Vehicles, fuel log with mileage, pre-ride checklist with real sunrise and sunset | Fill-ups and checklist work offline | 1 |
| 7 | Maps and solo rides | MapLibre with the Rylaide style, live location, ride recording, solo and "Just ride" flows, summary, history | A real ride is recorded and replayed | 2 |
| 8 | Navigation | Place search, routes, turn-by-turn, spoken prompts, offline map packs | Navigate a route with no signal | 2 |
| 9 | Groups | Create or join by code or QR, invites, lobby, group chat, ride plans, rider positions over Nearby | Four riders complete a group ride | 3 |
| 10 | Online fallback | LiveKit on the server, automatic switch between phone link and internet | Riders 1 km apart stay connected, then reconnect on their own | 1.5 |
| 11 | Safety | SOS by text and call from the rider's own SIM, emergency contacts, medical card, lock-screen card | SOS sent from a test phone to test contacts | 1 |
| 12 | Voice assistant | Commands and assistant, on-phone first, cloud optional | Hands-free commands work on two phone models | 1.5 |
| 13 | Settings and languages | All settings, text moved into translation files, Hindi and Marathi first, accessibility and large text | Language switch works everywhere | 1.5 |
| 14 | Hardening | Security rules for the database, group join checks, crash reports, battery and performance, privacy policy and delete-account page | Security checklist passed | 1 |
| 15 | Testing | Unit and phone tests, 20+ riders recruited, real rides | Two weeks of rides with no blocking bugs | 1 plus rides |
| 16 | Play Store | Developer account, closed test, store listing, data safety form, signed bundle, apply for production, staged rollout | App is live | 2 plus the 14 days |

Total: about 23 weeks of building before the store review. That is a little
more than the earlier 20 week estimate. To get back toward 20, ship these
**after** version 1: voice assistant (12), crash detection, languages beyond
three, and the paid tier.

## 6. Play Store checklist (Step 16)

- [ ] Developer account ($25) and identity checks done early, they can take days
- [ ] Closed test with 20+ testers, 12 must stay opted in 14 days in a row
- [ ] Target API 36, Android App Bundle, Play App Signing
- [ ] Store listing: name, descriptions, screenshots, icon, feature graphic
- [ ] Privacy policy page and a web page for deleting an account and data
- [ ] Data safety form: location, audio, account details, ride history
- [ ] Permission declarations: foreground service types (microphone, location), Nearby devices, SMS if SOS texts are sent
- [ ] Background location declaration **only if** testing shows we need it
- [ ] Test sign-in details for reviewers
- [ ] Credit OpenStreetMap on the map and MET Norway for weather
- [ ] Safety wording: Rylaide is not an emergency service, rider calls 112 themselves when they can
- [ ] Trademark check done and name final before the first upload

## 7. Risks

| Risk | Plan |
|---|---|
| Offline voice is choppy or drops in real riding | Step 1 and 3 tests come first. We change the approach before building screens on top of it |
| Phone makers (Xiaomi, Oppo, Samsung) kill background apps | Test on several brands in Step 3, add a battery-settings guide in the app |
| Oracle changes its free terms again | Scripted server setup, voice fallback only needs the server for far-apart riders |
| Firestore limits reached | Cache on the phone, batch writes, watch usage. Moving to the paid Blaze plan needs a card, so set budget alerts first |
| Gemini Nano missing on most phones | AI is only ever an extra, every feature has a normal fallback |
| Wind noise passes the loudness check | Test RNNoise in Step 3 |
| Anyone who guesses a group code can join | Fix in Step 9 with a shared secret and leader approval |
| Play rejects a permission | Keep permissions to what the features need and explain each in the app |
| Getting 12 testers for 14 days | Recruit from riding groups from Step 9 onward, ask for 20+ |

## 8. Decisions still open

1. **Name:** Rylaide has had no trademark search. Check WIPO, TMview and IP India before the first store upload.
2. **Default accent colour:** blue in Figma, amber earlier in the code.
3. **Paid tier ("Rylaide Plus") on screen 72:** conflicts with "everything free". Keep, hide, or remove.
4. **SOS scope:** only the group, or also emergency contacts and services.
5. **Jacket and gloves in the checklist:** required, or only recommended on short commutes.
6. **Splash screen:** not a separate frame in Figma yet.
7. **Licence for this repository:** none chosen, so all rights are reserved.
8. **Contact email and web domain:** still use the old Relaide name.

## 9. How we work on GitHub

- One commit per step, pushed to `main`, with a short message saying what changed and what is untested.
- A GitHub Actions build runs on every push, so a red cross shows a compile error straight away.
- The README progress table is updated at the end of each step.

## Sources

- [Play target API level requirement](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Play testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Play background location policy](https://support.google.com/googleplay/android-developer/answer/9799150)
- [Android 14 foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Firebase pricing](https://firebase.google.com/pricing)
- [InfoQ: Oracle Cloud free tier limits](https://infoq.com/news/2026/07/oracle-cloud-free-tier-limits/)
- [Linuxiac: Oracle cuts free Ampere A1](https://linuxiac.com/oracle-quietly-cuts-free-tier-ampere-a1-resources-in-half/)
