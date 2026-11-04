package mx.edu.backendacademico.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.AlumnoEntity;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import mx.edu.backendacademico.repository.jpa.MateriaEntity;
import mx.edu.backendacademico.repository.jpa.MateriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Requiere el PostgreSQL de compose.yaml: el bloqueo que se demuestra vive en la base,
 * así que H2 o un mock no servirían como evidencia. Ejecutar con -Dpostgres=true.
 * La explicación de esperas y evidencia está al final del archivo.
 */
@EnabledIfSystemProperty(named = "postgres", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InscripcionConcurrenciaTests {

    private static final String ESPERAS_POR_MATERIA = """
            select pid, wait_event_type, wait_event, state, pg_blocking_pids(pid)::text as bloqueadores,
                   left(query, 90) as consulta
            from pg_stat_activity
            where datname = current_database() and pid <> pg_backend_pid()
              and wait_event_type = 'Lock' and query ilike '%from materias%for%update%'
            """;

    @Autowired InscripcionService service;
    @Autowired MateriaRepository materias;
    @Autowired AlumnoSpringDataRepository alumnos;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaccion;

    private Long materiaId;
    private final List<Long> alumnoIds = new ArrayList<>();

    @AfterEach
    void limpiar() {
        // Los datos se confirman en hilos distintos, así que no hay rollback automático de prueba.
        if (materiaId != null) jdbc.update("delete from inscripciones where materia_id = ?", materiaId);
        alumnoIds.forEach(id -> jdbc.update("delete from alumnos where id = ?", id));
        if (materiaId != null) jdbc.update("delete from materias where id = ?", materiaId);
    }

    @Test
    void dosAdmisionesSimultaneasConUnLugarSoloPersistenUna() throws Exception {
        String sufijo = Long.toString(System.nanoTime(), 36).toUpperCase();
        materiaId = materias.save(new MateriaEntity("CONC-" + sufijo, "Concurrencia", 4, 1)).getId();
        Long alumnoA = crearAlumno("A" + sufijo);
        Long alumnoB = crearAlumno("B" + sufijo);
        System.out.printf("== Preparación == materia=%d cupo=1 alumnos=[%d, %d]%n", materiaId, alumnoA, alumnoB);

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Thread> trabajadores = new ArrayList<>();
        List<Future<String>> resultados = new ArrayList<>();
        try {
            // La "compuerta" toma la fila de la materia antes que los hilos; así ambos
            // quedan esperando en PostgreSQL al mismo tiempo de forma determinista.
            transaccion.executeWithoutResult(estado -> {
                jdbc.queryForObject("select id from materias where id = ? for update", Long.class, materiaId);
                // Identifica la sesión que encabeza la cadena de pg_blocking_pids.
                System.out.println("== Compuerta == pid=" + jdbc.queryForObject("select pg_backend_pid()", Integer.class));

                resultados.add(hilos.submit(admision(alumnoA, salida, trabajadores)));
                resultados.add(hilos.submit(admision(alumnoB, salida, trabajadores)));
                salida.countDown();

                List<String> esperas = esperarBloqueados(2, Duration.ofSeconds(10));
                System.out.println("== Sesiones esperando en PostgreSQL ==");
                esperas.forEach(System.out::println);
                synchronized (trabajadores) {
                    trabajadores.forEach(InscripcionConcurrenciaTests::imprimirHilo);
                }
                assertThat(esperas).hasSize(2);
            });
            // Al confirmar la compuerta, PostgreSQL concede la fila a un hilo; el otro
            // sigue esperando hasta el commit del ganador y entonces ve el cupo lleno.

            List<String> salidas = new ArrayList<>();
            for (Future<String> r : resultados) salidas.add(r.get(15, TimeUnit.SECONDS));
            System.out.println("== Resultados == " + salidas);

            assertThat(salidas).filteredOn(s -> s.startsWith("OK")).hasSize(1);
            assertThat(salidas).containsOnlyOnce("SIN_CUPO");
            assertThat(jdbc.queryForObject(
                    "select count(*) from inscripciones where materia_id = ?", Long.class, materiaId))
                    .isEqualTo(1L);
        } finally {
            hilos.shutdownNow();
        }
    }

    private Callable<String> admision(Long alumnoId, CountDownLatch salida, List<Thread> trabajadores) {
        return () -> {
            synchronized (trabajadores) { trabajadores.add(Thread.currentThread()); }
            salida.await();
            try {
                // Bean real: el proxy abre la transacción y ejecuta SELECT ... FOR UPDATE.
                return "OK id=" + service.inscribir(alumnoId, materiaId);
            } catch (OperacionAcademicaException ex) {
                return ex.getCodigo();
            }
        };
    }

    private List<String> esperarBloqueados(int esperados, Duration limite) {
        long fin = System.nanoTime() + limite.toNanos();
        List<String> filas;
        do {
            // pg_stat_activity se congela por transacción; sin limpiar, el sondeo vería datos viejos.
            jdbc.execute("select pg_stat_clear_snapshot()");
            filas = jdbc.query(ESPERAS_POR_MATERIA, (rs, i) -> "pid=%d %s/%s %s bloqueadoPor=%s | %s".formatted(
                    rs.getInt("pid"), rs.getString("wait_event_type"), rs.getString("wait_event"),
                    rs.getString("state"), rs.getString("bloqueadores"), rs.getString("consulta")));
            if (filas.size() >= esperados) return filas;
            Thread.onSpinWait();
        } while (System.nanoTime() < fin);
        return filas;
    }

    // Un hilo detenido por PostgreSQL aparece RUNNABLE leyendo el socket del driver,
    // no BLOCKED/WAITING sobre un monitor de Java.
    private static void imprimirHilo(Thread hilo) {
        System.out.printf("%s estado=%s%n", hilo.getName(), hilo.getState());
        StackTraceElement[] pila = hilo.getStackTrace();
        for (int i = 0; i < pila.length; i++) {
            String marco = pila[i].toString();
            if (marco.startsWith("org.postgresql") || marco.contains("InscripcionService")
                    || marco.contains("bloquearPorId") || i < 3) {
                System.out.println("    at " + marco);
            }
        }
    }

    private Long crearAlumno(String matricula) {
        var alumno = new Alumno(null, matricula, "Alumno " + matricula,
                matricula.toLowerCase() + "@universidad.mx", EstatusAlumno.ACTIVO);
        Long id = alumnos.save(new AlumnoEntity(alumno)).getId();
        alumnoIds.add(id);
        return id;
    }
}

/*
 * DÓNDE ESPERAN LOS HILOS
 *   InscripcionService.inscribir -> materias.bloquearPorId(...) = SELECT ... FOR NO KEY UPDATE.
 *   Ambos esperan a la compuerta; el perdedor espera además el commit del ganador,
 *   luego ve count >= cupo y recibe SIN_CUPO (sólo se persiste una inscripción).
 *
 * EVIDENCIA: EXCLUSIÓN EN POSTGRESQL, NO BLOQUEO LOCAL DE JAVA
 *   - pg_stat_activity: wait_event_type = Lock en ambas sesiones.
 *   - pg_blocking_pids: cadena compuerta -> hilo 1 -> hilo 2 entre procesos de la base.
 *   - Hilos Java RUNNABLE leyendo el socket de org.postgresql; con synchronized/ReentrantLock
 *     estarían BLOCKED/WAITING y la base no registraría espera alguna.
 *   - El candado vive en la fila: excluye también entre varias instancias de la aplicación;
 *     un candado de JVM sólo protege los hilos de un proceso.
 */
