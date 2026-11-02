# CSSECDV-Machine-Project

Secure Web Development machine project for De La Salle University. The course provides a Java Swing application with SQLite; our task is to implement its required security controls and role-based features.

## Start here

- [Development guide](docs/development.md): tools, build, run, and supplied starter limitations.
- [Specifications](docs/specifications.md) and [checklist](docs/checklist.md): cleaned conversions of the assignment handouts.
- [Readiness TODO](docs/TODO.md): grading and submission evidence.
- [Bundle B specification and implementation plan](docs/bundle-b-spec.md): settled spec, confirmed testing seam and approved B-only tickets with acceptance criteria.
- [GitHub Issues](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues): implementation tasks and assignments.

Bundle B catalog, purchase, and history are merged to `main` in [PR #10](https://github.com/qu1r0ra/CSSECDV-Machine-Project/pull/10) under [issue #6](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/6); tickets [#7](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/7) and [#8](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/8) are closed. Run the B service checks with `ant -f app/build.xml test-bundle-b`. Actual identity/session and authorization provider integration remains tracked by [issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9); until it lands, the app's adapter denies all B operations.

## Submission documents

- [Security documentation](docs/security-documentation.md)
- [Access control matrix](docs/access-control-matrix.md)
- [Demo test cases](docs/demo-test-cases.md)

See each document's status note for implemented Bundle B evidence and outstanding integration work. The project-wide security requirements remain pending until all bundle owners' work is integrated and verified.

## Group

See [the agreed bundle allocation](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/4) for ownership and [issue #9](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues/9) for the shared-provider integration gate.

- Christian Joseph Bunyi
- Liam Michael Alain Ancheta
- Roan Cedric Campo
