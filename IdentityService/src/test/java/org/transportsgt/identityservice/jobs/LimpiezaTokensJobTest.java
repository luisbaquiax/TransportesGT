package org.transportsgt.identityservice.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.identityservice.config.IdentidadProperties;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimpiezaTokensJobTest {

    private static final Instant AHORA = Instant.parse("2026-10-07T09:00:00Z");
    private static final OffsetDateTime HACE_7_DIAS = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC).minusDays(7);

    @Mock
    private TokenContrasenaRepository tokenRepository;

    private LimpiezaTokensJob job;

    @BeforeEach
    void setUp() {
        IdentidadProperties propiedades = new IdentidadProperties(Duration.ofMinutes(30), Duration.ofHours(48), 3,
                Duration.ofDays(7));
        job = new LimpiezaTokensJob(tokenRepository, propiedades, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void limpiarTokensVencidos_borraLosVencidosHaceMasDe7Dias() {
        // Arrange
        when(tokenRepository.eliminarVencidosAntesDe(HACE_7_DIAS)).thenReturn(5);

        // Act
        int borrados = job.limpiarTokensVencidos();

        // Assert
        assertEquals(5, borrados);
    }

    @Test
    void limpiarTokensVencidos_sinTokensVencidos_devuelveCero() {
        // Arrange
        when(tokenRepository.eliminarVencidosAntesDe(HACE_7_DIAS)).thenReturn(0);

        // Assert
        assertEquals(0, job.limpiarTokensVencidos());
    }
}
