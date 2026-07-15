# Deployment Guide

This guide deploys Stratus Lite with free public hosting:

- Frontend: Vercel Hobby project
- Backend: Render free web service
- Database: Neon free PostgreSQL

Keep database credentials in provider dashboards only. Do not commit real connection strings, usernames, or passwords.

## 1. Create Neon PostgreSQL

1. Create a free Neon project.
2. Open the connection details for the default database.
3. Save these three values for Render:
   - `SPRING_DATASOURCE_URL`: Neon JDBC connection string with SSL enabled
   - `SPRING_DATASOURCE_USERNAME`: Neon database username
   - `SPRING_DATASOURCE_PASSWORD`: Neon database password

If Neon shows more than one connection format, choose the Java/JDBC format. It should include the database host, database name, and `sslmode=require`.

## 2. Deploy Backend On Render

1. Push this repo to GitHub.
2. In Render, create a new Blueprint from the GitHub repo, or create a Web Service manually.
3. Use the root `render.yaml` when using a Blueprint.
4. When Render asks for secret values, provide:
   - `SPRING_DATASOURCE_URL`
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
5. Confirm these backend settings:
   - Runtime: Docker
   - Plan: Free
   - Dockerfile path: `./backend/Dockerfile`
   - Docker context: `.`
   - Health check path: `/api/health`

After deploy, open:

```text
https://<your-render-service>.onrender.com/api/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "stratus-lite-api"
}
```

Render free services can sleep when idle, so the first request after a pause may take a little longer.

## 3. Deploy Frontend On Vercel

1. Import the same GitHub repo into Vercel.
2. Keep the project root as the repository root.
3. Vercel will use `vercel.json`:
   - Install command: `corepack enable && corepack prepare pnpm@11.7.0 --activate && pnpm install --frozen-lockfile`
   - Build command: `pnpm --dir frontend build`
   - Output directory: `frontend/dist`
4. Add this environment variable for Production and Preview:

```text
VITE_STRATUS_API_BASE_URL=https://<your-render-service>.onrender.com/api
```

5. Deploy the frontend.

## 4. Tighten CORS After Vercel Deploys

The default Render Blueprint allows Vercel preview domains:

```text
https://*.vercel.app,http://localhost:*,http://127.0.0.1:*
```

After you know the exact Vercel URL, you can tighten Render's `STRATUS_CORS_ALLOWED_ORIGIN_PATTERNS` to:

```text
https://<your-vercel-project>.vercel.app,http://localhost:*,http://127.0.0.1:*
```

Redeploy the Render service after changing the value.

## 5. Public Smoke Test

Check backend health:

```bash
curl https://<your-render-service>.onrender.com/api/health
```

Open the Vercel URL and run this flow:

1. Click Reset demo.
2. Click Create workload.
3. Select the workload and click Place workload.
4. Select the assigned cell and click Fail selected cell.
5. Review the recommendation explanation.
6. Click Execute.
7. Confirm migration history and audit events update.

For command-line API testing:

```bash
BASE_URL=https://<your-render-service>.onrender.com ./scripts/smoke-test.sh
```

## Notes

- The public demo is intentionally mutable. Visitors can create workloads, simulate incidents, run migrations, and reset demo state.
- Neon free storage is enough for this simulation, but the app seeds demo data on startup and is not meant for production customer data.
- Render's free instance is suitable for a demo. If the backend feels slow after idle time, that is usually a cold start.
