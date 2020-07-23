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

For `com.google.sps.webcrawler.NewsContentProcessor.summarize` to work, download the OpenNLP
"Sentence Detector" and "Tokenizer" model files [here](http://opennlp.sourceforge.net/models-1.5/).
Set their paths in `com.google.sps.infocompiler.Config` appropriately, and assign their values to
the corresponding constants in `com.google.sps.webcrawler.NewsContentProcessor`.
