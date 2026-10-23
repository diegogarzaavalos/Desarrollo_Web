package mx.edu.backendacademico.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AlumnoControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void rechazaMatriculaConEspaciosInternos() throws Exception {
        var cuerpo = """
                {"matricula":"A 001","nombre":"Ada Lovelace","correo":"ada@universidad.mx"}
                """;
        mvc.perform(post("/api/v1/alumnos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos[0].campo").value("matricula"))
                .andExpect(jsonPath("$.campos[0].mensaje").value("no debe contener espacios internos"));
    }
}
