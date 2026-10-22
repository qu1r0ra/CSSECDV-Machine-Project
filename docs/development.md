# Development

## Tools

Use a JDK and Apache Ant for the supplied NetBeans Ant project. Java source and target remain Java 8 as specified in `app/nbproject/project.properties`. JDK 21 can compile the starter; a separate Java 8 installation is unnecessary for this setup.

- Check tools: `java -version`, `javac -version`, `jar --version`, `ant -version`.
- Set `JAVA_HOME` to the JDK directory and add its `bin` to `PATH`.
- Follow [Apache Ant's installation instructions](https://ant.apache.org/manual/install.html) to set `ANT_HOME` and add its `bin` to `PATH`.
- Use `git` and `gh` for collaboration and GitHub Issues.

The provided SQLite JDBC dependency is included in `app/`; no database server or separate SQLite CLI is needed. Maven and Gradle are unnecessary for this build. NetBeans is optional for editing and command-line builds. It provides a visual editor for the supplied Swing forms; the assignment also asks the team to try the finished application in NetBeans.

## Build and run

From the repository root:

```powershell
ant -f app/build.xml clean init-db jar
Push-Location app/dist
java -jar CSSECDV.jar
Pop-Location
```

Completion: `app/dist/` contains `CSSECDV.jar`, `lib/sqlite-jdbc-3.23.1.jar`, and `database.db`. Launching from that directory uses the packaged database. A Java runtime is still required to run the executable JAR.

For development against the local working database:

```powershell
ant -f app/build.xml init-db run
```

`init-db` copies `app/fixtures/starter.db` to `app/database.db` only when the working file is absent. The application uses the relative path `database.db`, so its working directory matters. The `run` target uses `app/`; invoking the JAR manually uses your current directory.

## Databases and distributions

- `app/fixtures/starter.db` is the unchanged course sample database. Its accounts and plaintext sample passwords are part of the vulnerable starter. It is not a completed secure demo fixture.
- `app/database.db` is an ignored working copy. `init-db` preserves an existing copy. To reset, close the application, back up any needed data, then replace it explicitly with the fixture.
- A normal JAR build packages the fixture into `app/dist/database.db`, replacing the previous distribution copy. It preserves `app/database.db`.
- To package a deliberately prepared demo database, close the application and run:

  ```powershell
  ant -f app/build.xml -Ddistribution.database=database.db jar
  ```

  The override path is relative to `app/`. Review the selected database before distributing it. After security implementation, prepare a database that satisfies the [account and credential requirements](checklist.md), then verify the distribution independently of the development checkout.

`build/` and `dist/` are generated and ignored. Include the generated distribution in the final submission as required by the [source-code deliverable](specifications.md#source-code).

## Editing Swing forms

Keep each view's `.java` and `.form` pair together. NetBeans regenerates guarded `initComponents()` sections from `.form` files; changes made only in generated Java can be overwritten by the visual editor. For layout edits, update the form and generated Java together or use the NetBeans GUI builder. Event-handler and controller changes can be made in a text editor.

To use NetBeans, open `app/` as an existing project. Initialize its working database with `ant -f app/build.xml init-db`, then use Clean and Build and Run. Keep `nbproject/private/` local.

## Supplied baseline and setup changes

The course archive **MP - Security Svcs.zip** contains this Java/Swing MVC application, SQLite driver, and sample database. It matches the technology and screens described in the specification. The starter provides navigation and partial screens, with substantial behavior left to implement:

- Login navigates home without checking credentials; role navigation is unrestricted.
- Registration ignores password confirmation. Password fields and the user table expose plaintext values.
- Many user/product actions print values or have empty handlers instead of completing the operation.
- Database methods concatenate SQL input. Schema/bootstrap calls in `Main.java` are commented out.
- Security logging, failure handling, and permission enforcement need implementation against the checklist.

The import preserves all supplied Java and `.form` files unchanged. It retains the shared NetBeans project configuration and one JDBC JAR. It omits compiled output, private IDE settings, duplicate dependencies, platform metadata, and unused bundled SQLite executables.

Setup changes to the original build are limited to:

- `build.xml`: initialize an absent working database and package the dependency plus selected database.
- `nbproject/project.properties`: disable NetBeans-specific CopyLibs packaging so standard Ant uses the same packaging path.
- `manifest.mf`: declare the distribution's relative JDBC classpath.

The original `application.vendor` attribution is retained in the NetBeans properties. The supplied JDBC JAR retains its embedded notices at `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE` and `LICENSE.zentus`.

Original archive SHA-256: `c618c1bd927acf78adb9c10e151e536670da0e4222e1adf9d964b5ff61539b0d`.

## Work records

Use [GitHub Issues](https://github.com/qu1r0ra/CSSECDV-Machine-Project/issues) for task scope, assignments, and implementation progress. Record grading and submission evidence in [TODO](TODO.md). Use [ADR guidance](agents/domain.md) when an architectural decision warrants a record.
