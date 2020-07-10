This directory contains the web crawler for "Voter Central".

The use of the Google Cloud Datastore client library requires the project ID to
be configured:
```bash
gcloud config set project <projectId>
```

To compile and execute WebCrawler's main, execute this command:

```bash
mvn clean compile exec:java
```
