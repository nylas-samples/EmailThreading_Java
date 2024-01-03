# EmailThreading_Java

This sample will show you to create email threading using Java.

## Setup

### System dependencies

- Java 18.0.2
- Maven 3.8.6

### Gather environment variables

You'll need the following values:

```text
V3_TOKEN_API=API_KEY
GRANT_ID=GRANT_ID
```

Add the above values to a new `.env` file:

```bash
$ touch .env # Then add your env variables
```

### Install dependencies

```bash
org.slf4j / slf4j-simple / 1.7.25
com.nylas.sdk / nylas-java-sdk / 1.18.0
io.github.cdimascio / dotenv-java / 2.2.4
spark-core / Spark Java / 2.9.4
spark-template-mustache / Mustache / 2.7.1
jsoup / 1.15.3
spark-template-handlebars / Handlebars / 2.7.1
org.projectlombok / lombok / 1.18.28
```

# Compilation

To compile the comment we need to use this `maven` command:

```bash
mvn clean compile
```

## Usage

Run the application using the `maven` command:

```bash
$ mvn exec:java -Dexec.mainClass="EmailThreading"
```

If successful, you will be able to find organized email threads.


## Learn more
