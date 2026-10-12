package mx.edu.backendacademico.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;

class AlumnoRepositoryEnMemoriaTest {

    @Test
    void dosAltasSimultaneasDejanUnRegistroYUnConflicto() throws Exception {
        var repositorio = new AlumnoRepositoryEnMemoria();
        // listos: ambos hilos llegan a la línea de salida; fin: ambos ya terminaron.
        var listos = new CountDownLatch(2);
        var fin = new CountDownLatch(2);
        var exitos = new AtomicInteger();
        var conflictos = new AtomicInteger();

        Runnable alta = () -> {
            try {
                listos.countDown();
                if (!listos.await(5, TimeUnit.SECONDS)) {
                    fail("Los hilos no coincidieron en la línea de salida");
                }
                repositorio.guardar(new Alumno(null, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
                exitos.incrementAndGet();
            } catch (MatriculaDuplicadaException e) {
                conflictos.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            } finally {
                fin.countDown();
            }
        };

        try (var pool = Executors.newFixedThreadPool(2)) {
            pool.submit(alta);
            pool.submit(alta);
            // Espera por el latch, no por sleep: el tiempo lo marca el trabajo real.
            assertTrue(fin.await(5, TimeUnit.SECONDS), "Las tareas no terminaron a tiempo");
        }

        assertEquals(1, exitos.get(), "Debió persistir exactamente un alta");
        assertEquals(1, conflictos.get(), "La otra tarea debió chocar por matrícula");
        assertEquals(1, repositorio.buscarTodos().size());
        assertEquals("A001", repositorio.buscarTodos().getFirst().matricula());
        // Un AtomicLong solo serializa el id. Dos hilos podrían tomar 1 y 2 y
        // aún así insertar la misma matrícula si la comprobación no comparte
        // el monitor con la escritura. El contador no observa el mapa.
        // Esta corrida muestra un entrelazado de dos hilos; no cubre todos
        // los interleavings posibles ni un número mayor de contendientes.
    }
}
