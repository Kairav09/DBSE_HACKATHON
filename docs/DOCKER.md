# Running ParkGrid with Docker

One command brings up the whole stack — MySQL 8, the Spring Boot backend,
the Python prediction service, and the React frontend — with the demo data
seeded automatically.

## Prerequisites

- Docker Engine + Docker Compose v2 (`docker compose` command)
- Nothing else: no local JDK, Node, Python, or MySQL needed

## Start

```bash
docker compose up --build
```

| Service | URL | Notes |
|---|---|---|
| Frontend (nginx) | http://localhost:5173 | The app — start here |
| Backend API | http://localhost:8080 | Swagger UI at `/swagger-ui.html` |
| Python prediction | http://localhost:8000 | Internal; only the backend calls it |
| MySQL 8 | localhost:3306 | DB `smart_parking`, user `root` |

First boot takes a while (image builds + MySQL initialisation). The MySQL
container runs `database/schema.sql` then `database/seed.sql` automatically
on a **fresh** data volume (mounted as `01-schema.sql` / `02-seed.sql` in
`/docker-entrypoint-initdb.d`), so the demo accounts and data are ready:

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@smartparking.local` | `admin123` |
| USER | `user1@example.com` | `user123` |
| USER | `user2@example.com` | `user123` |

The backend waits for MySQL to pass its healthcheck before starting, and
talks to the prediction service at `http://python-service:8000` (compose
hostname). The frontend is built with `VITE_API_BASE_URL=http://localhost:8080`
because the **browser** calls the backend directly — override it if the
backend is reachable at a different host/port:

```bash
VITE_API_BASE_URL=http://192.168.1.10:8080 docker compose up --build frontend
```

## Stop / reset

```bash
docker compose down      # stop containers, keep the database volume
docker compose down -v   # stop AND delete the volume (next up re-seeds)
```

Changing `database/schema.sql` or `database/seed.sql` only takes effect on a
fresh volume — use `down -v` if you edit them.

## Configuration

| Variable | Default | Used by |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `parkgrid_root` | MySQL root password; also handed to backend + Python service |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Frontend build-time API base URL |

## How the images are built

- `backend/Dockerfile` — multi-stage: `maven:3.9-eclipse-temurin-17` builds
  the jar (`smart-parking-backend-0.0.1-SNAPSHOT.jar`), `eclipse-temurin:17-jre`
  runs it on port 8080.
- `frontend/Dockerfile` — `node:20` runs `npm ci` + `npm run build`;
  `nginx:alpine` serves `dist/` with SPA fallback (`frontend/nginx.conf`),
  exposed on container port 80 (mapped to host 5173).
- `python-service/Dockerfile` — `python:3.12-slim`, installs
  `requirements.txt`, runs `uvicorn main:app` on port 8000.

## Honest note

These Docker files were written to the standard Compose/Dockerfile patterns
but **were not executed in the build VM used to develop this project** (no
Docker daemon was available there). They are expected to work as-is on any
normal Docker install; if a build step fails on your machine, the equivalent
non-Docker run instructions are in the root `README.md` (Ways 1 and 2).
