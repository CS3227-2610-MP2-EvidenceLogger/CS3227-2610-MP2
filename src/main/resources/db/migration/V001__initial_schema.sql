CREATE TABLE user_account (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    display_name TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('EVIDENCE_CUSTODIAN', 'INVESTIGATOR')),
    password_algorithm TEXT NOT NULL,
    password_iterations INTEGER NOT NULL CHECK (password_iterations > 0),
    password_salt BLOB NOT NULL,
    password_hash BLOB NOT NULL
);

CREATE TABLE case_record (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL CHECK (length(trim(title)) > 0),
    created_at TEXT NOT NULL
);

CREATE TABLE case_assignment (
    case_id TEXT NOT NULL REFERENCES case_record(id),
    investigator_id TEXT NOT NULL REFERENCES user_account(id),
    assigned_at TEXT NOT NULL,
    PRIMARY KEY (case_id, investigator_id)
);

CREATE TABLE storage_location (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(trim(name)) > 0),
    created_at TEXT NOT NULL
);

CREATE TABLE evidence_item (
    id TEXT PRIMARY KEY,
    case_id TEXT NOT NULL REFERENCES case_record(id),
    public_reference TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL CHECK (length(trim(description)) > 0),
    storage_location_id TEXT NOT NULL REFERENCES storage_location(id),
    custody_state TEXT NOT NULL CHECK (
        custody_state IN ('IN_STORAGE', 'HANDOFF_AWAITING_ACK', 'CHECKED_OUT', 'HELD_FOR_REVIEW')
    ),
    registered_at TEXT NOT NULL
);

CREATE TABLE checkout_request (
    id TEXT PRIMARY KEY,
    evidence_id TEXT NOT NULL REFERENCES evidence_item(id),
    requester_id TEXT NOT NULL REFERENCES user_account(id),
    purpose TEXT NOT NULL CHECK (length(trim(purpose)) > 0),
    expected_return_at TEXT NOT NULL,
    status TEXT NOT NULL CHECK (
        status IN ('PENDING', 'APPROVED', 'CONSUMED', 'REJECTED', 'WITHDRAWN', 'CANCELLED')
    ),
    requested_at TEXT NOT NULL,
    decided_at TEXT
);

CREATE UNIQUE INDEX one_active_request_per_evidence
ON checkout_request(evidence_id)
WHERE status IN ('PENDING', 'APPROVED');

CREATE TABLE handoff (
    id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL UNIQUE REFERENCES checkout_request(id),
    evidence_id TEXT NOT NULL REFERENCES evidence_item(id),
    custodian_id TEXT NOT NULL REFERENCES user_account(id),
    recorded_at TEXT NOT NULL,
    reversed_at TEXT,
    acknowledged_at TEXT
);

CREATE UNIQUE INDEX one_unacknowledged_handoff_per_evidence
ON handoff(evidence_id)
WHERE reversed_at IS NULL AND acknowledged_at IS NULL;

CREATE TABLE checkout (
    id TEXT PRIMARY KEY,
    handoff_id TEXT NOT NULL UNIQUE REFERENCES handoff(id),
    request_id TEXT NOT NULL UNIQUE REFERENCES checkout_request(id),
    evidence_id TEXT NOT NULL REFERENCES evidence_item(id),
    collector_id TEXT NOT NULL REFERENCES user_account(id),
    collected_at TEXT NOT NULL,
    return_initiated_at TEXT,
    completed_at TEXT,
    return_outcome TEXT CHECK (return_outcome IN ('STORED', 'HELD_FOR_REVIEW')),
    return_comment TEXT
);

CREATE UNIQUE INDEX one_active_checkout_per_evidence
ON checkout(evidence_id)
WHERE completed_at IS NULL;

CREATE TABLE examination_note (
    id TEXT PRIMARY KEY,
    checkout_id TEXT NOT NULL REFERENCES checkout(id),
    author_id TEXT NOT NULL REFERENCES user_account(id),
    note_text TEXT NOT NULL CHECK (length(trim(note_text)) > 0),
    created_at TEXT NOT NULL
);

CREATE TABLE note_correction (
    id TEXT PRIMARY KEY,
    note_id TEXT NOT NULL REFERENCES examination_note(id),
    author_id TEXT NOT NULL REFERENCES user_account(id),
    correction_text TEXT NOT NULL CHECK (length(trim(correction_text)) > 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TEXT NOT NULL
);

CREATE TABLE audit_event (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    actor_id TEXT NOT NULL REFERENCES user_account(id),
    actor_role TEXT NOT NULL CHECK (actor_role IN ('EVIDENCE_CUSTODIAN', 'INVESTIGATOR')),
    event_time TEXT NOT NULL,
    case_id TEXT REFERENCES case_record(id),
    evidence_id TEXT REFERENCES evidence_item(id),
    request_id TEXT REFERENCES checkout_request(id),
    handoff_id TEXT REFERENCES handoff(id),
    checkout_id TEXT REFERENCES checkout(id),
    assigned_investigator_id TEXT REFERENCES user_account(id),
    storage_location_id TEXT REFERENCES storage_location(id),
    previous_request_status TEXT,
    resulting_request_status TEXT,
    previous_custody_state TEXT,
    resulting_custody_state TEXT,
    reason TEXT,
    comment TEXT,
    correction_text TEXT,
    corrected_event_id TEXT REFERENCES audit_event(id),
    corrected_note_id TEXT REFERENCES examination_note(id)
);

CREATE INDEX audit_event_case_order ON audit_event(case_id, event_time, id);
CREATE INDEX audit_event_evidence_order ON audit_event(evidence_id, event_time, id);

INSERT INTO user_account (
    id, username, display_name, role, password_algorithm,
    password_iterations, password_salt, password_hash
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'custodian',
    'Morgan Custodian',
    'EVIDENCE_CUSTODIAN',
    'PBKDF2WithHmacSHA256',
    210000,
    X'20F0C8640D3DF0A633B3A12F35D72E64',
    X'E7F4451AA6BE7985B33FF73211C02E39957F961C4606D4907EEA45DF40A5EDB5'
);

INSERT INTO user_account (
    id, username, display_name, role, password_algorithm,
    password_iterations, password_salt, password_hash
) VALUES (
    '00000000-0000-0000-0000-000000000002',
    'investigator.alex',
    'Alex Investigator',
    'INVESTIGATOR',
    'PBKDF2WithHmacSHA256',
    210000,
    X'2D5828E8E0D66E027EE32821ABF983EA',
    X'9573E35D508E1A50602BF5167B8CCB8EBF70FC79BFAA50F3F0FD9F73749FF32C'
);

INSERT INTO user_account (
    id, username, display_name, role, password_algorithm,
    password_iterations, password_salt, password_hash
) VALUES (
    '00000000-0000-0000-0000-000000000003',
    'investigator.blair',
    'Blair Investigator',
    'INVESTIGATOR',
    'PBKDF2WithHmacSHA256',
    210000,
    X'BC551E1A0EC1C8AD41134201E5668D19',
    X'A4D90F6396808E84043B15A8F1365DF598206935DED1881230547A516093C641'
);
