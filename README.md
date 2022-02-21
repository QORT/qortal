# Qortal Project - Official Repo

## Build / run

- Requires Java 11. OpenJDK 11 recommended over Java SE.
- Install Maven
- Use Maven to fetch dependencies and build: `mvn clean package`
- Built JAR should be something like `target/qortal-1.0.jar`
- Create basic *settings.json* file: `echo '{}' > settings.json`
- Run JAR in same working directory as *settings.json*: `java -jar target/qortal-1.0.jar`
- Wrap in shell script, add JVM flags, redirection, backgrounding, etc. as necessary.
- Or use supplied example shell script: *start.sh*
