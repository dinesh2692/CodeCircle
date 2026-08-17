# CodeCircle IDE

A real-time collaborative Java coding workspace powered by a dependency-free Java 21 backend.

## Features

- 6-character realtime rooms
- Live collaborative Java code editing
- Server-side Java compilation using `javac`
- Compiler diagnostics displayed in the browser
- Realtime room chat over WebSocket
- Browser microphone voice chat using WebRTC + server signaling
- Participant presence (join/leave)
- Docker deployment with `PORT` support

## Run locally

```bash
docker build -t codecircle .
docker run -p 8080:8080 codecircle
```

Open `http://localhost:8080`.

## Architecture

```text
Browser
  ├── HTTP /compile ──> Java backend ──> javac
  └── WebSocket ──────> room server
                          ├── code sync
                          ├── chat
                          └── WebRTC signaling
```

Voice media itself is peer-to-peer through WebRTC; the Java server handles room signaling.
