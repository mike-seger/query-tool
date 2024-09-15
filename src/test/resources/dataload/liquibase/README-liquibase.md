liquibase --driver=org.postgresql.Driver \
	--classpath=postgresql-42.7.4.jar \
	--url=”jdbc:postgresql://localhost:25432/test” \
	--username=querytool --password=S3cret --changeLogFile=db.changelog.yaml generateChangeLog
