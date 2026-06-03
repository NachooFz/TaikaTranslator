# 1. Etapa de Construcción (Maven)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
# Descargar dependencias de forma offline para acelerar builds posteriores
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# 2. Etapa de Ejecución (JRE + Python + Dependencias)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Instalar Python 3, pip y librerías del sistema requeridas por OpenCV
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Instalar PyTorch (versión CPU para no sobrecargar el contenedor) y dependencias de SAM/OpenCV
RUN pip3 install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu
RUN pip3 install --no-cache-dir ultralytics opencv-python-headless numpy

# Copiar el ejecutable JAR compilado en la etapa anteriorr
COPY --from=build /app/target/taika-translator-1.0.0-SNAPSHOT.jar app.jar

# Copiar el script de visión de Python y el checkpoint del modelo SAM
COPY vision ./vision
COPY sam2_t.pt ./sam2_t.pt

# Exponer el puerto por defecto de Javalin
EXPOSE 8080

# Definir variables de entorno de rutas para que el contenedor use el Python internos
ENV PYTHON_EXE=python3
ENV PORT=8080
ENV AZURE_ENDPOINT="https://taikatranslator.cognitiveservices.azure.com/"
ENV DEEPL_KEY="d32f63dd-63a4-4f3e-b79f-67fae91434f4:fx"
ENV DEEPL_ENDPOINT="https://api-free.deepl.com"

# Iniciar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]
