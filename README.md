# query-tool

## Features

This project provides the following features:

1. **Dynamic SQL Query UI**
    - The UI allows querying the connected database with predefind queries, custom queries
      and display the results 

2. **Dynamic SQL Query API**
    - The application exposes an API that allows the execution of SQL queries and returns the results in TSV format.

3. **A Sample DB**
    - The application comes with a default DB based on the
      (DVD rental project)[https://github.com/gordonkwokkwok/DVD-Rental-PostgreSQL-Project].

4. **H2 Database Console**
    - The H2 database is an in-memory database used for testing.

5. **Spring Boot Actuators**
    - All Spring Boot actuators are enabled, providing insights into the health and metrics of the application.

## How to Run

1. Clone the repository.
2. Run `./gradlew bootRun`.
3. Open a browser and navigate to `http://localhost:8080/`.
