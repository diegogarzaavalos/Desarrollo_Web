package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import org.junit.jupiter.api.Test;

class AlumnoServiceListadoTest {
    @Test
    void filtrarPorEstatusNoModificaElRepositorio() {
        var repository = new AlumnoRepositoryEnMemoria();
        var service = new AlumnoService(repository);
        var ada = service.crear("A001", "Ada Lovelace", "ada@universidad.mx");
        // La baja se guarda aparte: el GET solo debe leer, no producir este cambio.
        var adaBaja = repository.guardar(ada.darDeBaja());
        var grace = service.crear("A002", "Grace Hopper", "grace@universidad.mx");
        var antes = repository.buscarTodos();

        assertEquals(List.of(adaBaja, grace), service.listar());
        assertEquals(List.of(grace), service.listar(EstatusAlumno.ACTIVO));
        assertEquals(List.of(adaBaja), service.listar(EstatusAlumno.BAJA));
        assertEquals(antes, repository.buscarTodos());
    }
}
