package mx.edu.backendacademico.controller;

// Asocia una ruta HTTP con un método y entrega datos para serialización JSON.

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SaludoController {
    @GetMapping("/saludo")
    public Map<String, String> saludar(
            @RequestParam(defaultValue= "Mundo") String nombre) {
        // Jackson serializa este valor; no se construye JSON por concatenación.
        String mensaje = "Hola " + nombre;
        return Map.of("mensaje", mensaje);
    }
}
// el parametro se resuelve con spring antes de ejecutar el metodo
// revisa la peticion http y su tiene nombre = "Ada", en caso contrario de no
//encontrar parametro usa nombre = "Mundo"

// el spring/jackson toma el map y lo serializa a json para enviarlo como respuesta http