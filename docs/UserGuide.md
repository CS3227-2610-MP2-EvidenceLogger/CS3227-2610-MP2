# EvidenceLogger User Guide

The runnable A4 build provides sign-in and the Evidence Custodian casework workspace. Run it on
Windows from the repository root with `./gradlew.bat run`.

The fictional demo-only usernames and passwords are listed in the repository
[README](../README.md#demo-only-accounts). Do not reuse those passwords or enter real evidence
information.

Custodians can create and search cases, maintain assignments and storage locations, register
evidence, and search evidence. Investigator authentication can be exercised, but the separate
Investigator workspace and logout are planned for A6; the current build clears an Investigator
session and returns to login with an explanatory message.
