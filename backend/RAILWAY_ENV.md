# Railway Environment Variables — REQUIRED

Set these in Railway → Your Service → Variables tab:

| Variable       | Value                                      | Notes                          |
|----------------|--------------------------------------------|--------------------------------|
| JWT_SECRET     | (64+ random chars)                         | Generate: openssl rand -hex 64 |
| DB_URL         | (from Railway MySQL plugin → JDBC URL)     | Copy from MySQL plugin vars    |
| DB_USER        | (from Railway MySQL plugin)                |                                |
| DB_PASS        | (from Railway MySQL plugin)                |                                |
| REDIS_URL      | (from Railway Redis plugin → REDIS_URL)    | Copy from Redis plugin vars    |
| FRONTEND_ORIGIN| https://your-app-name.up.railway.app       | Your Railway public domain     |

Railway automatically sets PORT — do not set it manually.
Railway MySQL plugin automatically provides MYSQLHOST, MYSQLPORT,
MYSQLDATABASE, MYSQLUSER, MYSQLPASSWORD — build DB_URL from these:
  DB_URL = jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC

Alternatively set in application.yml using Railway's reference variables:
  url: jdbc:mysql://${MYSQLHOST}:${MYSQLPORT:3306}/${MYSQLDATABASE}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC

Add this alternative datasource URL format to application.yml:
  url: ${DB_URL:jdbc:mysql://${MYSQLHOST:localhost}:${MYSQLPORT:3306}/${MYSQLDATABASE:totemena}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true}
