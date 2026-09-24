package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.repository.EvidenceRecord;

class JdbcEvidenceRepositoryTest {
    private static final EvidenceId EVIDENCE_ID = new EvidenceId(UUID.randomUUID());

    @TempDir
    Path temporaryDirectory;

    private CheckoutRepositoryTestDatabase database;
    private JdbcEvidenceRepository repository;

    @BeforeEach
    void setUp() {
        database = new CheckoutRepositoryTestDatabase(
                temporaryDirectory.resolve("evidence.db"));
        database.insertEvidence(EVIDENCE_ID);
        repository = new JdbcEvidenceRepository();
    }

    @Test
    void mapsEvidenceCaseAndCustodyStateFromProductionSchema() {
        EvidenceRecord evidence = database.inTransaction(connection ->
                repository.findById(connection, EVIDENCE_ID).orElseThrow());

        assertEquals(EVIDENCE_ID, evidence.evidenceId());
        assertEquals(CaseId.parse("00000000-0000-0000-0000-000000000500"), evidence.caseId());
        assertEquals(EvidenceCustodyState.IN_STORAGE, evidence.custodyState());
    }

    @Test
    void missingEvidenceReturnsEmpty() {
        EvidenceId missing = new EvidenceId(UUID.randomUUID());

        boolean missingEvidence = database.inTransaction(connection ->
                repository.findById(connection, missing).isEmpty());
        assertTrue(missingEvidence);
    }
}
