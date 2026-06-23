# Railway Environment Variables — REQUIRED

Set these in Railway → Your Service → Variables tab:

| Variable                     | Value                                                                                                                                                 | Notes                                      |
|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| JWT_SECRET                   | (64+ random chars)                                                                                                                                    | Generate: openssl rand -hex 64             |
| SPRING_DATASOURCE_URL        | jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true | Ensure query params are included!          |
| SPRING_DATASOURCE_USERNAME   | ${{MySQL.MYSQLUSER}}                                                                                                                                  |                                            |
| SPRING_DATASOURCE_PASSWORD   | ${{MySQL.MYSQLPASSWORD}}                                                                                                                              |                                            |
| REDIS_URL                    | ${{Redis.REDIS_URL}}                                                                                                                                  | Provided by Railway Redis plugin           |
| FRONTEND_ORIGIN              | https://your-app-name.up.railway.app                                                                                                                  | Your exact Railway public domain           |
| JAVA_OPTS                    | -Xmx256m -Xms256m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m                                                                                          | Memory optimization for free tier          |
| MAVEN_OPTS                   | -Xmx256m                                                                                                                                              | Prevents build crashes on free tier        |

Railway automatically sets `PORT` — do not set it manually.

*(Note: Spring Boot automatically maps `SPRING_DATASOURCE_*` to the `spring.datasource.*` settings inside your `application.yml`.)*
