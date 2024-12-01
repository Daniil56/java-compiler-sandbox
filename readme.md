# Java Compiler Sandbox API

## Overview

Java Compiler Sandbox API is a Spring Boot project that provides an online Java compiler through a RESTful API. It
allows users to compile and execute Java code snippets securely and efficiently.

🌐 **Live Demo**: [java-sandbox.sajit.me](https://java-sandbox.sajit.me)

## 🚀 Features

-   Compile and execute Java code snippets
-   Built-in rate-limiting using Bucket4j
-   Caching support with Caffeine
-   Ready for containerized deployment with Jib

## 🛠️ Building and Running the Project Locally

### Clone the Repository

```bash
git clone https://github.com/sajitkhadka/online-java-compiler.git
cd online-java-compiler
```

### Verify Installations

```bash
java -version
mvn -version
```

### Build the Project

```bash
mvn clean install
```

### Run the Application

```bash
mvn spring-boot:run
```

The application will be accessible at `http://localhost:8080`

### Example API Request

**Endpoint**: `POST /api/compile`

**Request Body**:

```json
{
    "code": "public class Main { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }"
}
```

**Response**:

```json
{
    "output": "Hello, World!\n",
    "errors": "",
    "success": true
}
```

## 🐳 Dockerized Deployment

### Prerequisites

-   Docker installed and running

### Build and Run Docker Image

```bash
# Build using Jib
mvn compile jib:dockerBuild

# Run the container
docker run -p 8080:8080 online-java-compiler:latest
```

## 🤝 Contributions

Contributions, issues, and feature requests are welcome!

Feel free to:

-   Submit a pull request
-   Create an issue on GitHub
-   Provide feedback or suggestions
