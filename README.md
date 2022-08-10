[![Build status](https://github.com/navikt/smregistrering-backend/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/smregistrering-backend/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# Manual registration of paper sykmelding
Repo for manual registration of paper sykmelding, receives paper sykmelding and in turns them into digital sykmeldings
<img src="./src/svg/flyttdiagram.svg" alt="Image of the flow of the smregistrering-backend application">

## Technologies used
* Kotlin
* Ktor
* Gradle
* Junit
* Jackson

#### Requirements

* JDK 17

### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common)
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

## Testing the whole flow for handling paper sykmelding in preprod
For triggering this app https://github.com/navikt/syfosmpapirmottak#testing-the-whole-flow-for-handling-paper-sykmelding-in-preprod
And put in a invalid diagnose, then it will end up in this app

### Verification in Gosys:
1. Login User (Case managers / supervisors):
   Z992389
2. Check that the sykmelding is placed in gosys:
   - Log in at https://gosys-q1.dev.intern.nav.no/gosys
   - Search for user with fnr
3. Verify that there is a sykmelding task under tasks overview and 
   that this is the sykmelding you submitted
4. Click on the "Start buttom" for that task.   
5. You may need to login, with the Login User, the mail adress follows this pattern:
    F_ZXXXXXX.E_ZXXXXXX@trygdeetaten.no, where you change F_ZXXXXXX to F_Z992389 and E_ZXXXXXX to E_Z992389
    Use the same passord that you used to login in gosys.
    Username and password for testing can be found here(NAV-internal sites):
    https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod
6. TODO
7. TODO
8. Then check that the task has been closed and completed in gosys


### Verification in «ditt sykefravær»:
1. Check that the sykmelding is on ditt sykefravær
2. Go to https://tjenester-q1.nav.no/sykefravaer
3. Log in with the fnr for the user as the username and a password
3. Then select "Uten IDPorten"
4. Enter the user's fnr again and press sign-in
5. Verify that a new task has appeared for the user

### Verification in Modia:
1. Log in to the modes, https://syfomodiaperson.nais.preprod.local/sykefravaer/$fnr
2. You may need to login, with the Login User, the mail adress follows this pattern:
    F_ZXXXXXX.E_ZXXXXXX@trygdeetaten.no, where you change F_ZXXXXXX to F_Z992389 and E_ZXXXXXX to E_Z992389
    Use the same passord that you used to login in gosys.
    Username and password for testing can be found here(NAV-internal sites):
    https://confluence.adeo.no/display/KES/Generell+testing+av+sykemelding+2013+i+preprod under "Verifisering i Modia"
3. See "Sykmeldt enkeltperson" verifying that the sykmelding that is there is correct


### Importing flowchart from gliffy confluence
1. Open a web browser and go the confluence site that has the gliffy diagram you want to import, example site:
https://confluence.adeo.no/display/KES/SyfoSmMottak.
2. Click on the gliffy diagram and the "Edit Digram" buttom
3. Then go to File -> Export... and choose the Gliffy File Format (The gliffy diagram, should now be downloaded to you computer)
4. Open a web browser and go to: https://app.diagrams.net/
5. Choose the "Open Existing Diagram", then choose the file that was downloaded from step 3.
6. Click on File -> Save (The diagram is now saved as a drawio format, store it in the source code)
7. Click on File -> Export as SVG...(The diagram is now saved as SVG, store it in the source code)
8. Commit and push the changes so its up to date

### Editing existing flowchart
1. Open a web browser and go to: https://app.diagrams.net/
2. Choose the "Open Existing Diagram", then choose the file /src/flowchart/flyttdiagram.drawio
3. Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
4. Click on File -> Export as SVG... save the file to here: file here: /src/svg/flytdiagram.svg
5. Commit and push the changes so its up to date

### Creating a new flowchart
1. Open a web browser and go to: https://app.diagrams.net/
2. Choose the "Create New diagram",
3. Do the changes you want, and the save it as a drawio, back to /src/flowchart/flyttdiagram.drawio
4. Click on File -> Export as SVG... save the file to here: file here: /src/svg/flytdiagram.svg
5. Commit and push the changes so its up to date

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t smregistrering-backend .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 smregistrering-backend`

### Access to the Postgres database

For utfyllende dokumentasjon se [Postgres i NAV](https://github.com/navikt/utvikling/blob/master/PostgreSQL.md)

#### Tldr

The application uses dynamically generated user / passwords for the database.
To connect to the database one must generate user / password (which lasts for one hour)
as follows:

Use The Vault Browser CLI that is build in https://vault.adeo.no


Preprod credentials:

```
read postgresql/preprod-fss/creds/smregistrering-backend-admin

```

Prod credentials:

```
read postgresql/prod-fss/creds/smregistrering-backend-readonly

```

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```

### Inquiries
Questions related to the code or the project can be asked as issues here on GitHub

### For NAV employees
We are available at the Slack channel #team-sykmelding