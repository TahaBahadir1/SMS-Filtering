# Build aşaması
FROM eclipse-temurin:21 AS builder
WORKDIR /app

# Maven wrapper ve konfigürasyon dosyaları
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Satır sonu uyumluluğu ve çalıştırma izni
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Kaynak kodları kopyala ve paketi derle
COPY src ./src
RUN ./mvnw -q -DskipTests package

# Çalıştırma aşaması
FROM eclipse-temurin:21-jre
WORKDIR /app

# Root olmayan kullanıcı tanımla
RUN groupadd -r spring && useradd -r -g spring spring

# Üretilen JAR dosyasını kopyala ve izinleri ayarla
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R spring:spring /app

# Root olmayan kullanıcıya geç
USER spring:spring

# 8080 portunu aç
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

