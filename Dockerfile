FROM eclipse-temurin:25-jre
WORKDIR /app
COPY ./build/libs/*.jar document.jar
COPY ./deployment/config .
ENTRYPOINT ["java","-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager","-Xmx512m","-jar","/app/document.jar"]
