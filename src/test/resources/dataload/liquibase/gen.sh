liquibase --username=querytool --password=S3cret generateChangeLog

liquibase generate-changelog --username=querytool --password=S3cret \
	--diffTypes=data --dataOutputDirectory=data --changelog-file=loadDataOnly_changelog.yaml
