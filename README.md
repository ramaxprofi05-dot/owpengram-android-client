<p align="center">
  <img src="media/readme/femboygram_logo.svg" alt="FemboyGram" width="220">
</p>

# 💜 FemboyGram for Android

**One familiar messenger. Your own FemboyGram network.**

FemboyGram for Android is a Telegram-compatible, multi-server messenger with
its own visual identity and a self-hosted default server. Each account stays
bound to its selected server, so identities and data remain separated.

> **Android client source** — this repository contains the FemboyGram Android
> client. APK releases will be published in the
> [Releases](https://github.com/ramaxprofi05-dot/owpengram-android-client/releases)
> tab after the branded build has passed verification.

> Built on **MTProto API layer 232**, using the Telegram for Android
> `v13.1.1` codebase as its protocol-compatible foundation.

<p align="center">
  <img src="media/readme/android_hero.png" alt="FemboyGram for Android — chats and calls" width="820">
</p>

---

## 📥 Download

The APK will be published in the
[Releases](https://github.com/ramaxprofi05-dot/owpengram-android-client/releases)
tab after the current FemboyGram source changes have been verified and built.
The Android project targets **Android 6.0+**.

## 🔄 Updates

The app checks the project's GitHub releases on start (and from **Settings**
→ check for updates). When a newer release is published it shows the usual
update window with the release notes and an install prompt.

## 🚀 Connect to FemboyGram

The FemboyGram server is preconfigured in the client. Choose **FemboyGram**
on the server-selection screen to connect. You can also add another
Telegram-compatible server using its `host:port` address; the client can
retrieve its public server identity and icon automatically.

## ✨ Why you'll like it

- 🌐 **Multi-server** — add accounts on different compatible servers.
- 🏠 **Self-hosted** — connect to infrastructure you control.
- 🔎 **Simple server setup** — add a `host:port` address and fetch public server details.
- 💜 **FemboyGram identity** — branded app name, launcher icon, splash screen and onboarding.
- 🔒 **Separated accounts** — each account remains bound to its selected server.
- 🆓 **Open source** — inspect the source and build it yourself.

## 🌐 How multi-server works

Every account is tied to a server, and you choose that server when you sign in.
The client comes with the FemboyGram server preconfigured and also supports
custom Telegram-compatible servers.

Add several accounts on different servers and they stay cleanly separated —
different identities, different data, one app. Each account's cached peers,
files and ids are scoped to its server.

Remove a server and every account on it goes with it — no orphaned logins left
behind pointing at a host that no longer exists.

## 🔌 Connect your own server

On the **server selection screen** (shown when you log in or add a new account),
tap **➕ Add server** and type the address — `chat.example.com:2398`, or
`203.0.113.10:2398`. That is the only field you have to fill in.

The app then asks the server who it is (`/owpengram/server-info` on that same
port) and fills in the rest by itself: RSA public key, data-centre id, and the
name, description and icon the server's operator set. Rename it or swap the icon
if you like — those are yours to edit. Save, pick the server, log in as usual.

Everything it fetched is still editable under **Advanced** — multi-DC mode, DC
id, RSA key — for a server that doesn't answer that endpoint, or when you want
to pin the key yourself.

> Only `host:port` is ever taken from a link or typed in. The identity a server
> claims is fetched from that address directly, so nobody can hand you a link
> that misrepresents whose server you're about to trust.

Operators can hand out a ready-made **`owpg://addserver?host=...&port=...`**
link: opening it pops the Add server screen pre-filled with the address. By
design it carries nothing else — no key, name or DC.

Server name, description and icon refresh on their own whenever a server list is
shown, so a rebrand on the operator's side appears without you re-adding
anything. Only those cosmetics — host, port, key and DC id are never touched
after you've saved them.

Don't have a server yet? Spin one up in one command:
👉 [FemboyGram server source](https://github.com/ramaxprofi05-dot/owpengram-server)

## 🛠️ Build (Windows)

Run the interactive build script — double-click it or run from a terminal:

```bat
build-android.bat
```

It guides you through API credentials, the keystore, SDK setup and the Gradle
build, and remembers your answers in `.owpengram-build.local.json` (gitignored).
It builds the `:TMessagesProj_AppStandalone` flavour — the unrestricted,
Firebase-free one that ships in Releases.

The script also asks for the **release number** of the build — the N in the
GitHub tag `ON` (for example `8` for `O8`). The in-app updater offers any
published release with a higher number. Leave it blank (or `0`) for a local
build that never checks for updates. For scripted builds set the
`OWPENGRAM_BUILD` environment variable and the question is skipped.

**Requirements:** JDK 17+, Android SDK (compile/target API 36, build-tools
36.0.0, NDK 27.2.12479018), Git. Minimum supported device API is 23
(Android 6.0).

## 📦 Related source

- 🚀 [FemboyGram-compatible server source](https://github.com/ramaxprofi05-dot/owpengram-server)

Community links and additional clients will be published here when the FemboyGram channels are ready.

## 📄 License

Based on [Telegram for Android](https://github.com/DrKLO/Telegram) — licensed
under **GNU GPL v2 or later** ([LICENSE](LICENSE)).

---

⭐ If FemboyGram is useful to you, a star on GitHub helps a lot.


## FemboyGram fork: API credentials

The FemboyGram build deliberately does not reuse upstream Telegram API credentials, Google client IDs, SafetyNet keys, store links, or release updater. Create an application at https://my.telegram.org/apps and configure the repository Actions secrets `FEMBOYGRAM_API_ID` and `FEMBOYGRAM_API_HASH`. The Android build compiles with an empty credential fallback for CI, but authentication is not expected to work until the FemboyGram credentials are supplied. Passkeys and store billing remain disabled until FemboyGram has its own verified app integrations.
