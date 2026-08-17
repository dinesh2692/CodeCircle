# CodeCircle

A lightweight Java 21 web application served with the JDK built-in HTTP server and packaged with Docker.

## Run locally

```bash
docker build -t codecircle .
docker run -p 8080:8080 codecircle
```

Open `http://localhost:8080`.

## Deployment

The container listens on the `PORT` environment variable and defaults to `8080`, making it suitable for container platforms such as Render, Railway, or Google Cloud Run.
