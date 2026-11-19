# Submission package

The `distribution` folder contains the standalone demo application.

## Run the distribution

Install a Java runtime, then start `distribution/CSSECDV.jar` from inside the distribution folder:

```powershell
Set-Location .\distribution
java -jar .\CSSECDV.jar
```

Keep `database.db` and `lib/sqlite-jdbc-3.23.1.jar` beside the JAR as packaged. The application opens `database.db` relative to its working directory. The database contains one Admin, Manager, Staff, and Client account with salted password hashes, plus two catalog products. It has no purchase/history test rows. Generated demo passwords are not included here: the seeder writes them to a separate operator-only file outside the checkout.

## Rebuild and test

From the `app` directory, Ant builds the executable JAR. The `prepare-demo-database` target writes a fresh prepared DB and operator credentials when passed new paths; it refuses to overwrite an existing DB. `test-bundle-a`, `test-bundle-b`, `test-ui`, and `test-regression-final-17` run behavior checks. `test-ui` requires a real desktop display. `test-distribution` uses the external credential file and tests a disposable copy of the selected package database; it checks that the original database remains unchanged.

The tracked runtime distribution intentionally excludes `app/test-lib`; AssertJ Swing and FEST are test-only dependencies.
