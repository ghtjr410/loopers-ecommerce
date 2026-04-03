FROM eclipse-temurin:21-jre
COPY apps/commerce-api/build/libs/commerce-api-*.jar /app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]
