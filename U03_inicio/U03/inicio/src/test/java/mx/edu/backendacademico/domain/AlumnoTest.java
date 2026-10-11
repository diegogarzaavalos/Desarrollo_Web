package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        // Comprueba tanto el resultado como la ausencia de efectos laterales.
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
                new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }

    @Test void reactivarConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.BAJA);
        var activo = alumno.reactivar();
        assertEquals("A001", activo.matricula());
        assertEquals(alumno.id(), activo.id());
        assertEquals(EstatusAlumno.ACTIVO, activo.estatus());
        // El valor en BAJA no muta: la transición vive en otra instancia.
        assertEquals(EstatusAlumno.BAJA, alumno.estatus());
    }

    @Test void mismoIdDistintoCorreoNoSonElMismoValor() {
        var original = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var actualizado = new Alumno(1L, "A001", "Ada", "ada.nueva@u.mx", EstatusAlumno.ACTIVO);
        // equals del record es valores: todos los componentes deben coincidir.
        assertNotEquals(original, actualizado);
        // Identidad académica: el mismo id (y matrícula) nombran al mismo alumno,
        // aunque el correo —un dato de contacto— haya cambiado.
        assertEquals(original.id(), actualizado.id());
        assertEquals(original.matricula(), actualizado.matricula());
    }
}