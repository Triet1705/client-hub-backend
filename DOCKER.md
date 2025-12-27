# Docker Development Environment

## Quick Start

```bash
# 1. Copy environment file
cp .env.example .env

# 2. Start all services
docker-compose -f docker-compose.dev.yml up -d

# 3. Check status
docker-compose -f docker-compose.dev.yml ps

# 4. View logs
docker-compose -f docker-compose.dev.yml logs -f postgres
```

## Services

| Service        | URL                      | Description           |
| -------------- | ------------------------ | --------------------- |
| **PostgreSQL** | `localhost:5432`         | Main database         |
| **PgAdmin**    | `http://localhost:5050`  | Database GUI          |
| **Redis**      | `localhost:6379`         | Cache & session store |
| **Ollama**     | `http://localhost:11434` | AI/LLM service        |

## Database Access

### Via PgAdmin

1. Open http://localhost:5050
2. Login: `admin@clienthub.com` / `admin`
3. Add Server:
   - Name: `ClientHub Dev`
   - Host: `postgres` (Docker network)
   - Port: `5432`
   - Username: `postgres`
   - Password: `password`

### Via psql

```bash
# Connect to database
docker exec -it clienthub-db psql -U postgres -d clienthub

# List schemas
\dn

# Connect to schema
SET search_path TO core;

# List tables
\dt
```

## Commands

```bash
# Start services
docker-compose -f docker-compose.dev.yml up -d

# Stop services
docker-compose -f docker-compose.dev.yml down

# Restart specific service
docker-compose -f docker-compose.dev.yml restart postgres

# View logs
docker-compose -f docker-compose.dev.yml logs -f

# Remove volumes (CAUTION: deletes all data)
docker-compose -f docker-compose.dev.yml down -v

# Rebuild and restart
docker-compose -f docker-compose.dev.yml up -d --build
```

## Troubleshooting

### Port Already in Use

```bash
# Find process using port
netstat -ano | findstr :5432

# Kill process (replace PID)
taskkill /PID <PID> /F
```

### Database Not Initializing

```bash
# Remove volume and recreate
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
```

### Redis Authentication Error

Check `REDIS_PASSWORD` in `.env` matches your application config.

### Ollama GPU Not Available

Comment out the GPU section in `docker-compose.dev.yml`:

```yaml
# deploy:
#   resources:
#     reservations:
#       devices: ...
```

## Network

All services are connected via `clienthub-network` bridge network:

- Services can communicate using container names
- Example: Spring Boot connects to `postgres:5432` instead of `localhost:5432`

## Volumes

- `postgres_data`: Database files
- `redis_data`: Redis persistence
- `ollama_data`: AI model storage
- `pgadmin_data`: PgAdmin settings

## Security Notes

⚠️ **Development only** - Do NOT use these credentials in production:

- Database password: `password`
- Redis password: `redis_password`
- PgAdmin password: `admin`

For production, use strong passwords and secure secrets management.
