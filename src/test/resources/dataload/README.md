# Provide a sample DB in postgres/docker
Download pagila.sql into the repository root from here:  
https://neon.tech/docs/import/import-sample-data#pagila-database

Load it into Postgres
```
docker compose exec postgres /bin/bash
$ psql -U querytool -d test </opt/pagila.sql
```

# more sample dbs
- https://www.postgresqltutorial.com/postgresql-getting-started/postgresql-sample-database/
- https://www.postgresqltutorial.com/postgresql-getting-started/load-postgresql-sample-database/
- https://neon.tech/docs/import/import-sample-data
- https://github.com/morenoh149/postgresDBSamples
