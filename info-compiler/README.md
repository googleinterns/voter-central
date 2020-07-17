This directory contains the information compiler for "Voter Central".

The use of the Google Cloud Datastore client library requires the project ID to
be configured:
```bash
gcloud config set project <projectId>
```

To use the code, please prepare and put the following in com.google.google.sps.infocompiler.Config:
- Civic Information API key (referenced in com.google.google.sps.infocompiler.InfoCompiler)
- Custom Search JSON API key (referenced in com.google.google.sps.webcrawler.WebCrawler)
- Custom Engine ID (referenced in com.google.google.sps.webcrawler.WebCrawler)

To run unit tests, execute this command:
```bash
mvn test
```
To run a specific test class, execute this command:
```bash
mvn test -Dtest=<testClass>
```
