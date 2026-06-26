# Metro-Parking-Web-Service

## Release workflow (manual, no CI)

> Assumes Maven Wrapper + Git tags  
> Uses Semantic Versioning (MAJOR.MINOR.PATCH)

```shell
# 1. Run tests + verify build
.\mvnw.cmd clean verify

# 2. Set release version (replace X.X.X with actual release version)
.\mvnw.cmd --% versions:set -DnewVersion=0.0.1-SNAPSHOT -DgenerateBackupPoms=false

# 3. Build artifact
.\mvnw.cmd clean package

# 4. Commit release version
git add pom.xml
git commit -m "chore(release): v0.0.1"

# 5. Tag release
git tag v0.0.1
git push origin main --tags

# 6. Bump to next snapshot (increment PATCH)
.\mvnw.cmd versions:set -DnewVersion=0.0.(1+1)-SNAPSHOT -DgenerateBackupPoms=false
git add pom.xml
git commit -m "chore: start 0.0.(1+1)-SNAPSHOT"
git push origin main