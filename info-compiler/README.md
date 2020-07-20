# Voter Central
This directory contains the web crawler for "Voter Central".

---

## Workflow

The use of the Google Cloud Datastore client library requires the project ID to
be configured:
```bash
gcloud config set project <projectId>
```

```bash
cd voter-central/info-compiler/
```

To deploy InfoCompilerFunction on Google Cloud Functions, execute this command:
```bash
gcloud functions deploy <functionName>
    --project <projectId>
    --entry-point com.google.sps.infocompiler.InfoCompilerFunction
    --runtime java11
    --trigger-http
    --service-account <serviceAccountEmail>
```
([More details about the command](https://cloud.google.com/sdk/gcloud/reference/functions/deploy).)

To create a Google Cloud Scheduler to trigger InfoCompilerFunction via HTTP, execute this command:
```bash
gcloud scheduler jobs create http <jobName>
    --schedule "<cronSchedule>"
    --uri <HTTPTriggerUrlOfCloudFunctions>
    --time-zone <timeZone>
    --oidc-service-account-email=<serviceAccountEmailSameInCloudFunctions>
```
([More details about the command](https://cloud.google.com/sdk/gcloud/reference/scheduler/jobs/create/http).)
([More details about cron schedules](https://cloud.google.com/scheduler/docs/configuring/cron-job-schedules).)

---

## Local Run
To run InfoCompilerFunction locally, execute this command:
```bash
mvn function:run
```
(See pom.xml build --> plugins --> plugin for how InfoCompilerFunction is defined to run.)

---

## Unit Testing
To run unit tests, execute this command:
```bash
mvn test
```
To run a specific test class, execute this command:
```bash
mvn test -Dtest=<testClass>
```
