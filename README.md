# WattSmart

WattSmart is a local-first energy monitoring demo with a Spring Boot backend, PostgreSQL, Apache Ignite, Kafka, and a Vite React frontend.

## Run Locally

Start the infrastructure services:

```powershell
cd C:\Users\PC1\Desktop\VoltWise
docker compose -f src\docker\docker-compose.yml up -d
```

Start the backend core API:

```powershell
cd C:\Users\PC1\Desktop\VoltWise\src\backend
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=core"
```

Start the telemetry simulator in a second terminal:

```powershell
cd C:\Users\PC1\Desktop\VoltWise\src\backend
mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=simulator" "-Dspring-boot.run.jvmArguments=-Dserver.port=8081"
```

Start the frontend in a third terminal:

```powershell
cd C:\Users\PC1\Desktop\VoltWise\src\frontend
npm.cmd run dev
```

Open the app at:

```text
http://localhost:5173
```

The seeded demo admin account is prefilled on the sign-in page.

## Swagger UI

After the core backend starts, open Swagger UI at:

```text
http://localhost:8080/swagger-ui/index.html
```

To call protected endpoints:

1. Use the auth login endpoint in Swagger, or sign in through the frontend.
2. Copy the returned session token.
3. Click `Authorize` in Swagger.
4. Enter the token as the bearer/auth value expected by the API.

Useful health checks:

```text
http://localhost:8080/health
http://localhost:8080/v3/api-docs
```

## Reset Local Data

If Flyway complains because local migrations changed during development, recreate the PostgreSQL volume:

```powershell
cd C:\Users\PC1\Desktop\VoltWise
docker compose -f src\docker\docker-compose.yml down -v
docker compose -f src\docker\docker-compose.yml up -d
```

Then restart the backend core and simulator.
