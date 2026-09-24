CREATE TEMP TABLE v002_evidence_state_preflight (
    custody_state TEXT CONSTRAINT v002_unsupported_held_evidence_state
        CHECK (custody_state <> 'HELD_FOR_REVIEW')
);

INSERT INTO v002_evidence_state_preflight(custody_state)
SELECT custody_state FROM evidence_item WHERE custody_state = 'HELD_FOR_REVIEW';

DROP TABLE v002_evidence_state_preflight;

CREATE TEMP TABLE v002_checkout_outcome_preflight (
    return_outcome TEXT CONSTRAINT v002_unsupported_held_checkout_outcome
        CHECK (return_outcome <> 'HELD_FOR_REVIEW')
);

INSERT INTO v002_checkout_outcome_preflight(return_outcome)
SELECT return_outcome FROM checkout WHERE return_outcome = 'HELD_FOR_REVIEW';

DROP TABLE v002_checkout_outcome_preflight;

CREATE TEMP TABLE v002_handoff_reversal_preflight (
    handoff_id TEXT CONSTRAINT v002_reversed_handoff_requires_reason
        CHECK (handoff_id IS NULL)
);

INSERT INTO v002_handoff_reversal_preflight(handoff_id)
SELECT id FROM handoff WHERE reversed_at IS NOT NULL;

DROP TABLE v002_handoff_reversal_preflight;

CREATE TABLE evidence_item_v002 (
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
            'HANDIN_AWAITING_ACK'
        )
    ),
    registered_at TEXT NOT NULL
);

INSERT INTO evidence_item_v002 (
    id, case_id, public_reference, description,
    storage_location_id, custody_state, registered_at
)
SELECT
    id, case_id, public_reference, description,
    storage_location_id, custody_state, registered_at
FROM evidence_item;

DROP TABLE evidence_item;
ALTER TABLE evidence_item_v002 RENAME TO evidence_item;

CREATE TABLE checkout_v002 (
    id TEXT PRIMARY KEY,
    handoff_id TEXT NOT NULL UNIQUE REFERENCES handoff(id),
    request_id TEXT NOT NULL UNIQUE REFERENCES checkout_request(id),
    evidence_id TEXT NOT NULL REFERENCES evidence_item(id),
    collector_id TEXT NOT NULL REFERENCES user_account(id),
    collected_at TEXT NOT NULL,
    return_initiated_at TEXT,
    completed_at TEXT,
    return_outcome TEXT CHECK (return_outcome = 'STORED'),
    return_comment TEXT
);

INSERT INTO checkout_v002 (
    id, handoff_id, request_id, evidence_id, collector_id,
    collected_at, return_initiated_at, completed_at,
    return_outcome, return_comment
)
SELECT
    id, handoff_id, request_id, evidence_id, collector_id,
    collected_at, return_initiated_at, completed_at,
    return_outcome, return_comment
FROM checkout;

DROP TABLE checkout;
ALTER TABLE checkout_v002 RENAME TO checkout;

CREATE UNIQUE INDEX one_active_checkout_per_evidence
ON checkout(evidence_id)
WHERE completed_at IS NULL;

ALTER TABLE handoff ADD COLUMN reversal_reason TEXT CHECK (
    (reversed_at IS NULL AND reversal_reason IS NULL)
    OR (reversed_at IS NOT NULL AND length(trim(reversal_reason)) > 0)
);

CREATE TABLE return_inspection (
    checkout_id TEXT PRIMARY KEY REFERENCES checkout(id),
    custodian_id TEXT NOT NULL REFERENCES user_account(id),
    outcome TEXT NOT NULL CHECK (outcome = 'STORED'),
    unplanned INTEGER NOT NULL CHECK (unplanned IN (0, 1)),
    reason TEXT NOT NULL,
    inspected_at TEXT NOT NULL,
    CHECK (unplanned = 0 OR length(trim(reason)) > 0)
);
