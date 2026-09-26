CREATE TABLE evidence_item_v003 (
    id TEXT PRIMARY KEY,
    case_id TEXT NOT NULL REFERENCES case_record(id),
    public_reference TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL CHECK (length(trim(description)) > 0),
    storage_location_id TEXT NOT NULL REFERENCES storage_location(id),
    custody_state TEXT NOT NULL CHECK (
        custody_state IN (
            'IN_STORAGE',
            'HANDOFF_AWAITING_ACK',
            'CHECKED_OUT',
            'HANDIN_AWAITING_ACK',
            'VOIDED'
        )
    ),
    registered_at TEXT NOT NULL
);

INSERT INTO evidence_item_v003 (
    id, case_id, public_reference, description,
    storage_location_id, custody_state, registered_at
)
SELECT
    id, case_id, public_reference, description,
    storage_location_id, custody_state, registered_at
FROM evidence_item;

DROP TABLE evidence_item;
ALTER TABLE evidence_item_v003 RENAME TO evidence_item;
