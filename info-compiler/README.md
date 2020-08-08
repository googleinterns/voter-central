# Voter Central
This directory contains the web crawler for "Voter Central".

---

## Configurations and Dependencies
For `com.google.sps.infocompiler.InfoCompiler` to work, store the National Address Database
(Release 3) [data file](https://www.transportation.gov/gis/national-address-database/national-address-database-0)
on Google Cloud Storage. These addresses will serve as the starting point for `InfoCompiler` to gather all election
contest information (positions, candidates, etc.) spanning the United States, using voterInfoQuery (which requires
address and election ID as query parameters) from the Civic Information API. Specically, we extracted a total of
1,216 arbitrary addresses from every 40,000 addresses in the National Address Database. We chose this amount based
on how well our extracted addresses can cover all elections and based on the query limit for the Civic Information
API. To extract those 1,216 addresses, execute this command (where NAD_r3.txt is the complete database):
```bash
sed -n '0~40000p' NAD_r3.txt > NAD_r3_every40000.txt
```
For `com.google.sps.webcrawler.NewsContentProcessor.summarize` to work, download the OpenNLP
"Sentence Detector" and "Tokenizer" model files [here](http://opennlp.sourceforge.net/models-1.5/) and upload them
to Google Cloud Storage.

To use the code, please prepare and put the following configurations in com.google.google.sps.infocompiler.Config:
- Project ID (referenced in com.google.sps.infocompiler.InfoCompiler)
- Name of Cloud Storage bucket that holds the addresses file (referenced in com.google.sps.infocompiler.InfoCompiler)
- Name of the addresses file on Cloud Storage (referenced in com.google.sps.infocompiler.InfoCompiler)
- Civic Information API key (referenced in com.google.sps.infocompiler.InfoCompiler)
- Custom Search JSON API key (referenced in com.google.sps.webcrawler.WebCrawler)
- Custom Engine ID (referenced in com.google.sps.webcrawler.WebCrawler)
- Name of Cloud Storage bucket that holds the OpenNLP model files (referenced in com.google.sps.webcrawler.NewsContentProcessor)
- Name of the OpenNLP "Sentence Detector" model file (referenced in com.google.sps.webcrawler.NewsContentProcessor)
- Name of the OpenNLP "Tokenizer" model file (referenced in com.google.sps.webcrawler.NewsContentProcessor)
- Maximum duration for compiled data to be considered outdated. This should be smaller than the time it takes for InfoCompiler
    to run again/enter the next cycle (referenced in com.google.sps.infocompiler.InfoCompiler)

Additionally, for respecting the query rate limit (250 queries/100 seconds) of the Civic Information API, InfoCompiler
needs to pause between queries. Set how much to shorten/extend the pause between queries, relative to the minimum pause
(0.4 seconds) required in com.google.google.sps.infocompiler.Config. The recommended value is 2.
Due to Cloud Functions' 540 seconds execution limit: we deploy multiple Cloud Functions and each will process only a
subset of addresses. Set the starting and ending indices of the subset of adresses in com.google.google.sps.infocompiler.Config.
For instance: [0, 300), [301, 600), [601, 1000) respectively for three Cloud Functions. Note that the starting
index will be safely lower-bounded by 0 while the ending index will be safely upper-bounded by the total number of addresses.
If deploying on Compute Engine, there doesn't need to be bounds. We can set the indices to [0, 1000).

---

## Workflow for Direct Execution

The use of the Google Cloud Datastore client library requires the project ID to
be configured:
```bash
gcloud config set project <projectId>
```
```bash
cd voter-central/info-compiler/
```
Execute the information compilation process.
```bash
mvn clean compile exec:java
```

---

## Workflow for Deployment on Cloud Functions and Cloud Scheduler

The use of the Google Cloud Datastore client library requires the project ID to
be configured:
```bash
gcloud config set project <projectId>
```
```bash
cd voter-central/info-compiler/
```

To deploy InfoCompilerUtils on Google Cloud Functions, execute this command:
```bash
gcloud functions deploy <functionName> \
    --project <projectId> \
    --entry-point com.google.sps.infocompiler.InfoCompilerUtils \
    --runtime java11 \
    --trigger-http \
    --service-account <serviceAccountEmail> \
    --timeout 540 \
```
([More details about the command](https://cloud.google.com/sdk/gcloud/reference/functions/deploy).)

To create a Google Cloud Scheduler to trigger InfoCompilerUtils via HTTP, execute this command:
```bash
gcloud scheduler jobs create http <jobName> \
    --schedule "<cronSchedule>" \
    --uri <HTTPTriggerUrlOfCloudFunctions> \
    --time-zone <timeZone> \
    --oidc-service-account-email=<serviceAccountEmailSameInCloudFunctions>
```
([More details about the command](https://cloud.google.com/sdk/gcloud/reference/scheduler/jobs/create/http).)

([More details about cron schedules](https://cloud.google.com/scheduler/docs/configuring/cron-job-schedules).)

---

## Local Run
To run InfoCompilerUtils locally, execute this command:
```bash
mvn function:run
```
(See pom.xml build --> plugins --> plugin for how InfoCompilerUtils is defined to run.)

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
