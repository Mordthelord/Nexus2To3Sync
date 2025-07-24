# Nexus2To3Sync

This Java tool migrates artifacts from a Nexus 2 repository to a Nexus 3 repository by:

- Crawling Nexus 2 for all files.
- Comparing them to Nexus 3.
- Downloading missing files from Nexus 2.
- Uploading them to Nexus 3 via the REST API.
- Reporting skipped, successful, and failed uploads.

---

## Need to Know

- Before continuing, switch to "code View" for the README to be able to see the "mvn exec" command.
- Usernames, passwords, GroupID's, artifacts, versions, Packages, Paths, nexus links (for both nexus 2 and 3), File extensions, and Computer paths will ALL have to be updated with your own project information.
- You will need a maven exec command to put in project configurations in the form of: mvn exec:java -Dexec.args="<repositoryFormat> <nexus2RepositoryBase> <nexus2RepositoryName> <nexus3RepositoryBase> <nexus3RepositoryName> <nexus3RestApiBase>"

## Prerequisites

- Java 17+ (or compatible version)
- Maven 3.6+
- Credentials for Nexus 3
- Access to both Nexus 2 and Nexus 3 repositories

---

## Installation

Clone the repo and build it:

git clone https://github.com/your-username/nexus2-to-3-sync.git
cd nexus2-to-3-sync
mvn clean compile

---

## Usage

Since the pom.xml is preconfigured with the exec-maven-plugin, you can run:

mvn exec:java -Dexec.args="<repositoryFormat> <nexus2RepositoryBase> <nexus2RepositoryName> <nexus3RepositoryBase> <nexus3RepositoryName> <nexus3RestApiBase>"


---

## Example Run

mvn exec:java -Dexec.mainClass="com.company.Nexus2To3Sync" -Dexec.args=
"maven2
https://nexus2link.company.com:8443/content/repositories/
repository
https://nexus.smartstartinc.com/repository/
repository
https://nexus.company.com/service/rest/v1/components?repository="

---

## Required pom.xml Configuration

Make sure you have this in your pom.xml:

<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <version>3.1.0</version>
      <configuration>
        <mainClass>com.upload.Nexus2To3Sync</mainClass>
      </configuration>
    </plugin>
  </plugins>
</build>

---

## Configuring Credentials

Update the following static fields in Nexus2To3Sync:


private static final String NEXUS3_USERNAME = "your-username";
private static final String NEXUS3_PASSWORD = "your-password";

---

## Output
At the end of the run, you will see a summary such as:

Num skips:  42
Num successful uploads:  100
Num hacked uploads:  3
Num failed uploads:  1

Here are the failed uploads:

com/example/artifactID/version/artifactID-version.package

---

## Project Structure

src/main/java/com/upload/
 ├─ Nexus2To3Sync.java        # Main class
 ├─ Nexus2Crawler.java        # Recursive file crawler for Nexus 2
 ├─ Uploader.java             # Interface for uploaders
 ├─ NexusUploaderMaven.java   # Maven uploader implementation
 ├─ NexusUploaderNuget.java   # NuGet uploader implementation

 --- 
 
## License
This project is licensed under the MIT License.
