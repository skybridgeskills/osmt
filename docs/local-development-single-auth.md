# Local Development with Single-Auth Profile

This guide explains how to run OSMT locally using the `single-auth` profile, which provides simple admin authentication without requiring an OAuth2 provider setup.

## Prerequisites

1. **Docker services running**: MySQL, Redis, and Elasticsearch must be running

   ```bash
   ./osmt_cli.sh -d
   ```

   Or manually:

   ```bash
   docker-compose --profile all up -d db-mysql db-redis db-elasticsearch
   ```

2. **Database initialized**: The database schema will be created automatically by Flyway when you start the application

## Starting the Application

### Option 1: Using IntelliJ IDEA (Recommended)

1. **Open the run configuration**:

   - Go to `Run` → `Edit Configurations...`
   - Select `OSMT Single-Auth Local Development` from the list
   - Or use the run configuration dropdown in the toolbar

2. **Configure environment variables** (optional):

   - In the run configuration, you can set admin authentication variables:
     - `SINGLE_AUTH_ADMIN_USERNAME`: Admin username (default: `admin` in dev mode)
     - `SINGLE_AUTH_ADMIN_PASSWORD`: Admin password (default: `admin` in dev mode)

3. **Start the application**:

   - Click the Run button (green play icon) or press `Shift+F10`
   - The application will start on `http://localhost:8080`

4. **Verify single-auth is active**:
   - Check the console logs for: `Detected security profile: single-auth`
   - The application should start without OAuth2 errors

### Option 2: Using Command Line

1. **Start Docker services** (if not already running):

   ```bash
   ./osmt_cli.sh -d
   ```

2. **Set environment variables** (optional):

   ```bash
   export TEST_ROLE=ROLE_Osmt_Admin
   export TEST_USER_NAME=test-user
   export TEST_USER_EMAIL=test@example.com
   ```

3. **Start the application**:

   ```bash
   ./osmt_cli.sh -s
   ```

   Or manually with Maven:

   ```bash
   cd api
   mvn -Dspring-boot.run.profiles=dev,apiserver,single-auth spring-boot:run
   ```

## Testing with Different Roles

### Via HTTP Headers

```bash
# Test as Admin
curl -H "X-Test-Role: ROLE_Osmt_Admin" http://localhost:8080/api/v3/skills

# Test as Curator
curl -H "X-Test-Role: ROLE_Osmt_Curator" http://localhost:8080/api/v3/skills

# Test as View-only
curl -H "X-Test-Role: ROLE_Osmt_View" http://localhost:8080/api/v3/skills
```

### Via Query Parameters

```bash
curl "http://localhost:8080/api/v3/skills?testRole=ROLE_Osmt_Admin"
```

### Via Environment Variables

Set the `TEST_ROLE` environment variable before starting the application:

```bash
export TEST_ROLE=ROLE_Osmt_Curator
# Then start the application
```

## UI Development with NoAuth

1. **Start the backend** (using one of the methods above)

2. **Start the Angular UI**:

   ```bash
   cd ui
   npm install  # First time only
   npm start
   ```

3. **Access the UI**:
   - Open `http://localhost:4200` in your browser
   - Login with test role: `http://localhost:4200/login?testRole=ROLE_Osmt_Admin`

## Available Roles

- `ROLE_Osmt_Admin` - Full administrative access (create, update, publish, delete)
- `ROLE_Osmt_Curator` - Can create and update skills/collections (cannot publish or delete)
- `ROLE_Osmt_View` - Read-only access
- `SCOPE_osmt.read` - Basic read scope (for machine-to-machine access)

## Troubleshooting

### Application won't start

- **Check Docker services are running**:

  ```bash
  docker ps
  ```

  You should see MySQL, Redis, and Elasticsearch containers running

- **Check database connection**:

  - Default: `localhost:3306`
  - Database: `osmt_db`
  - User: `osmt_db_user`
  - Password: `password`

- **Check Elasticsearch**:

  - Default: `http://localhost:9200`
  - Verify it's accessible: `curl http://localhost:9200`

- **Check Redis**:
  - Default: `localhost:6379`

### Authentication not working

- Verify the single-auth profile is active by checking logs for `Detected security profile: single-auth`
- Check that test role is set (via header, query param, or environment variable)
- Verify CORS is configured correctly if accessing from Angular dev server

### Port conflicts

- Backend default: `8080`
- Angular dev server default: `4200`
- If port 8080 is in use, you can change it in `application-dev.properties`:
  ```properties
  server.port=8081
  ```

## Security Warning

⚠️ **IMPORTANT**: The `single-auth` profile is intended **ONLY** for local development and testing. **DO NOT** use this profile in production, staging, or any environment accessible from the internet. The single-auth profile provides simple admin authentication and should never be used where security is a concern.
