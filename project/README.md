This directory contains the web application "Voter Central".

To run a local server, execute this command:

```bash
mvn package appengine:run
```

The project requires the use of AngularJS. To install, make sure you have
a recent version of `npm` installed. Then run this command under
`src/main/webapp/`:

```bash
npm install angular
```

The project backend requires the use of the
[Civic Information API](https://developers.google.com/civic-information).
Set the API key in project/src/main/java/com/google/sps/servlets/Config.java.
