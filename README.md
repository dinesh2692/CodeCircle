# CodeCircle — Secure Collaborative Java IDE

CodeCircle is a full-stack collaborative coding platform built around **Java 21 + Spring Boot + REST API + WebSocket + WebRTC + PostgreSQL + Docker**.

## Architecture

```text
Browser
  │
  ├── HTTPS/REST ────────> Spring Boot
  │                         ├── JWT Auth
  │                         ├── Project APIs
  │                         ├── Compile API
  │                         └── PostgreSQL
  │
  ├── WSS ───────────────> Room WebSocket
  │                         ├── Live code
  │                         ├── Chat
  │                         ├── Presence
  │                         └── WebRTC signaling
  │
  └── WebRTC ─────────────> Peer audio (signaling via backend)
```

## Current features

- Secure registration/login with BCrypt password hashing and JWT authentication
- Protected project CRUD APIs with per-user ownership checks
- Real-time room collaboration over authenticated WebSocket connections
- Live code synchronization and room chat
- Java compilation API with source-size and timeout limits
- API rate limiting
- PostgreSQL persistence
- Health endpoint
- Dockerized Spring Boot deployment
- Browser-ready WebRTC signaling foundation
- Professional IDE-style frontend

## REST endpoints

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/projects
POST /api/projects
GET  /api/projects/{id}
PUT  /api/projects/{id}
POST /api/compiler/compile
GET  /health
```

## WebSocket

```text
/ws?token=<JWT>&room=<6-char-room>&name=<display-name>
```

Events include `code`, `chat`, `signal`, `user-joined`, and `user-left`.

## Local development

```bash
docker compose up --build
```

Open `http://localhost:8080`.

Set a strong `JWT_SECRET` before public deployment. Do not commit production database passwords or secrets.

## Important execution security

The current compile endpoint only compiles Java source. Do **not** expose arbitrary program execution directly from the main application process. A production Run feature should execute untrusted programs in isolated short-lived containers with non-root users, CPU/memory/time limits, no network access, and temporary storage.
