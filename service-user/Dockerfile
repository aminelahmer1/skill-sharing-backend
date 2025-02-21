# Utilise une image de base avec Java 17
FROM eclipse-temurin:17-jdk-alpine

# Définit le répertoire de travail dans le conteneur
WORKDIR /app

# Copie le fichier JAR de l'application dans le conteneur
COPY target/service-user-0.0.1-SNAPSHOT.jar app.jar

# Expose le port sur lequel l'application écoute
EXPOSE 8081

# Commande pour exécuter l'application
ENTRYPOINT ["java", "-jar", "app.jar"]