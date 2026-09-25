# Reto - Ejecución en puerto 8081 mediante configuración externa

## Comando utilizado

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

La aplicación se ejecutó utilizando el puerto 8081 sin modificar la clase principal ni la lógica Java.

## Resultado

La aplicación inició correctamente y estuvo disponible en:

`http://localhost:8081`

## Configuración externa

El puerto se modificó mediante el argumento:

`--server.port=8081`

Esto permite cambiar la configuración de ejecución de Spring Boot sin modificar el código fuente de la aplicación.

## ¿Por qué no es necesario recompilar la lógica Java?

Cambiar el puerto es un cambio de configuración de ejecución, no un cambio en la lógica del programa. La aplicación compilada contiene la misma lógica Java; Spring Boot simplemente recibe el valor `8081` al iniciar y utiliza ese puerto para levantar el servidor web.

Por lo tanto, no es necesario modificar ni recompilar las clases Java para realizar este cambio.

## Evidencia

Se conserva la salida de versiones, el resultado de las pruebas y la ejecución de la aplicación en el puerto 8081 mediante las capturas correspondientes.
