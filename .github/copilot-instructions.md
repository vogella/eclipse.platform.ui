# Eclipse Platform UI - Copilot Coding Agent Instructions

## Repository Overview

**What This Repository Does:**
Eclipse Platform UI provides the basic building blocks for user interfaces built with Eclipse. It contains the core UI components for the Eclipse IDE and the Eclipse Rich Client Platform (RCP), including JFace, commands, databinding, dialogs, editors, views, perspectives, and workbench functionality. Built on top of SWT (Standard Widget Toolkit).

**Repository Statistics:**
- **Size:** ~127 MB
- **Language:** Java (7,675+ .java files)
- **Build System:** Maven with Tycho (Eclipse/OSGi plugin build)
- **Structure:** 57 bundles, 34 test bundles, 25 example bundles
- **Java Version:** Java 17 (temurin-jdk-17)
- **Maven Version:** 3.9.x required

## Project Structure

### Top-Level Directories

```
eclipse.platform.ui/
├── bundles/          # 57 OSGi bundles (production code)
├── tests/            # 34 test bundles
├── examples/         # 25 example bundles
├── features/         # Eclipse feature definitions
├── releng/           # Release engineering artifacts
├── docs/             # Extensive documentation (JFace, RCP, Commands, etc.)
├── .github/          # GitHub workflows and CI configuration
├── pom.xml           # Root Maven POM
└── Jenkinsfile       # Jenkins CI configuration
```

### Key Bundle Categories

**Core UI Bundles:**
- `bundles/org.eclipse.ui.workbench` - Main workbench implementation
- `bundles/org.eclipse.jface` - JFace UI toolkit
- `bundles/org.eclipse.ui.ide` - IDE-specific UI components
- `bundles/org.eclipse.ui.editors` - Text and generic editors

**E4 (Eclipse 4) Bundles:**
- `bundles/org.eclipse.e4.ui.workbench*` - E4 workbench model and rendering
- `bundles/org.eclipse.e4.ui.css.*` - CSS styling support
- `bundles/org.eclipse.e4.ui.di` - Dependency injection

**Data Binding:**
- `bundles/org.eclipse.core.databinding*` - Core data binding framework
- `bundles/org.eclipse.jface.databinding` - JFace data binding

**Commands & Key Bindings:**
- `bundles/org.eclipse.core.commands` - Command framework
- `bundles/org.eclipse.ui.workbench.texteditor` - Text editor commands

### Bundle Structure

Each bundle (OSGi plugin) contains:
- `META-INF/MANIFEST.MF` - OSGi bundle manifest with dependencies
- `build.properties` - Tycho build configuration
- `plugin.xml` - Eclipse extension point declarations
- `src/` or `eclipseui/` - Java source code
- `.project`, `.classpath` - Eclipse project metadata

## Build System

### Important: Build Limitations

**CRITICAL:** This repository requires the parent POM from `eclipse.platform.releng.aggregator` to build standalone. The command `mvn clean verify` in the README will **FAIL** with:
```
Non-resolvable parent POM for org.eclipse.platform:eclipse.platform.ui:4.37.0-SNAPSHOT
```

**Workarounds:**
1. **For full builds:** This repo is typically built as part of the eclipse.platform.releng.aggregator project, not standalone
2. **For individual bundle builds:** Use the profile `-Pbuild-individual-bundles` (configured in `.mvn/maven.config`)
3. **For testing code changes:** Verify syntax and logic without full Maven build, or test in Eclipse IDE
4. **For CI:** GitHub workflows use reusable workflows from eclipse.platform.releng.aggregator

### Maven Configuration

**Maven Config (`.mvn/maven.config`):**
```
-Pbuild-individual-bundles
-Dtycho.target.eager=true
-Dtycho.localArtifacts=ignore
```

### Build Profiles (from Jenkinsfile)

The Jenkins CI uses these profiles:
```bash
mvn clean verify --batch-mode --fail-at-end \
    -Pbree-libs          # Bundle Runtime Environment libraries
    -Papi-check          # API compatibility checking
    -Pjavadoc            # Generate Javadoc
    -Dmaven.test.failure.ignore=true \
    -Dcompare-version-with-baselines.skip=false
```

**Timeout:** Jenkins allows 80 minutes for full build

### Running Individual Bundle Builds

For individual bundles (when possible):
```bash
cd bundles/org.eclipse.jface  # or any bundle
mvn clean verify -Pbuild-individual-bundles
```

## Testing

### Test Execution

**Test Command (from test bundle README):**
```bash
mvn clean verify -Pbuild-individual-bundles -DskipTests=false
```

**Important Test Properties:**
- `tycho.surefire.useUIHarness=true` - Uses UI test harness by default
- `tycho.surefire.useUIThread=true` - Tests run on UI thread

### Test Structure

Tests use JUnit 4 with Eclipse-specific test runners:
- Test bundles follow naming: `org.eclipse.<component>.tests`
- Test classes extend Eclipse test base classes
- UI tests require display/shell setup (see `CodeMiningTest.java`)
- Tests use `@Rule`, `@Before`, `@After` annotations

### Test Dependencies

From README: "Several test plug-ins have a dependency to the Mockito and Hamcrest libraries. Please install them by installing 'Eclipse Test Framework' from the current release stream p2 repo."

### Example Test Pattern

```java
@Before
public void setUp() {
    fShell = new Shell(Display.getDefault());
    fViewer = new SourceViewer(fShell, null, SWT.NONE);
    // ... setup
}

@After
public void tearDown() {
    // ... cleanup
}

@Test
public void testSomething() {
    // ... test implementation
}
```

## GitHub Workflows / CI Validation

### Primary Workflows

**.github/workflows/ci.yml** - Main CI build
- Triggers on: push to master, PRs to master
- Ignores: `docs/**`, `*.md`
- Uses: `eclipse.platform.releng.aggregator/.github/workflows/mavenBuild.yml@master`
- Concurrency: Cancels previous runs on new push

**.github/workflows/pr-checks.yml** - Fast PR validation
- Checks freeze period compliance
- Checks for merge commits (should use rebase)
- Checks version increments

**.github/workflows/unit-tests.yml** - Publishes test results
- Runs after CI workflow completes
- Publishes JUnit test results

**.github/workflows/codeql.yml** - Security scanning
- CodeQL analysis for Java

### Validation Steps Performed by CI

1. **Compiler checks** - Eclipse compiler with warnings configured
2. **API compatibility** - Using API tools (pattern: `**/target/apianalysis/*.xml`)
3. **Javadoc generation** - Must not have errors (`failOnJavadocErrors=true`)
4. **Unit tests** - JUnit tests with UI harness
5. **Test reports** - Surefire reports at `**/target/surefire-reports/TEST-*.xml`
6. **Artifact logging** - Archives `*.log,**/target/**/*.log`

### Important CI Notes

- Jenkins uses Java 21 (`temurin-jdk21-latest`)
- Uses `xvnc` for UI tests (headless X server)
- Quality gates: DELTA threshold of 1 issue makes build unstable
- Maven repo: Uses local repo at `$WORKSPACE/.m2/repository`

## Making Code Changes

### Before Starting

1. **Understand the module structure** - Identify which bundle needs changes
2. **Check OSGi dependencies** - Review `META-INF/MANIFEST.MF` for required bundles
3. **Find existing tests** - Look for corresponding test bundle (e.g., `org.eclipse.jface` → `org.eclipse.jface.tests`)

### Common Pitfalls

1. **Don't add new dependencies** without careful consideration - OSGi dependency hell
2. **Maintain API compatibility** - API tools will flag breaking changes
3. **Update bundle versions** if making API changes
4. **Run on UI thread** - UI code must execute on the Display thread
5. **Dispose resources** - SWT resources (colors, fonts, images) must be explicitly disposed

### Code Patterns to Follow

**UI Resource Management:**
```java
Display display = Display.getDefault();
Shell shell = new Shell(display);
try {
    // Use resources
} finally {
    shell.dispose();
}
```

**Async UI Execution:**
```java
Display.getDefault().asyncExec(() -> {
    // UI code here
});
```

**Data Binding Example:**
```java
DataBindingContext ctx = new DataBindingContext();
IObservableValue target = WidgetProperties.text(SWT.Modify).observe(textWidget);
IObservableValue model = BeanProperties.value("property").observe(bean);
ctx.bindValue(target, model);
```

### File Locations

**Configuration Files:**
- `.settings/org.eclipse.jdt.core.prefs` - Java compiler settings (per bundle)
- `plugin.xml` - Extension point declarations
- `META-INF/MANIFEST.MF` - OSGi bundle manifest
- `build.properties` - Build configuration (what to include in binary build)

**Documentation:**
- `docs/` - Extensive developer documentation
- Key docs: `JFace.md`, `JFaceDataBinding.md`, `Eclipse4_RCP_FAQ.md`, `PlatformCommandFramework.md`

## Critical Development Rules

1. **ALWAYS check `META-INF/MANIFEST.MF`** for bundle dependencies before adding imports
2. **DO NOT break API compatibility** - API tools run in CI will fail the build
3. **Use existing test infrastructure** - Don't introduce new test frameworks
4. **Dispose SWT resources** - Memory leaks are common if resources aren't disposed
5. **Test on UI thread** - UI tests must run with UI harness
6. **Follow OSGi patterns** - Use declarative services, avoid static dependencies
7. **Update `build.properties`** - Include new resources/packages
8. **Check plugin.xml** - Update extension points if adding new UI contributions
9. **Maintain backward compatibility** - This is a platform API used by thousands of plugins

## Documentation & References

**Key Documentation Files:**
- `docs/JFace.md` - JFace UI toolkit guide
- `docs/JFaceDataBinding.md` - Data binding framework
- `docs/Eclipse4_RCP_FAQ.md` - E4 application model FAQ
- `docs/PlatformCommandFramework.md` - Command framework
- `docs/Menu_Contributions.md` - Menu contribution system
- `docs/Eclipse_Corner.md` - Eclipse Corner articles

**External Links:**
- Eclipse Platform project: https://projects.eclipse.org/projects/eclipse.platform
- Platform UI wiki: https://wiki.eclipse.org/Platform_UI
- Contributing guide: https://github.com/eclipse-platform/.github/blob/main/CONTRIBUTING.md

## Quick Reference

**Root Directory Files:**
```
.github/              GitHub workflows and CI config
.mvn/                 Maven wrapper configuration
bundles/              Production OSGi bundles (57)
tests/                Test bundles (34)
examples/             Example applications (25)
features/             Eclipse feature definitions
releng/               Release engineering
docs/                 Documentation (20+ markdown files)
pom.xml               Root Maven POM (parent: eclipse-platform-parent)
Jenkinsfile           Jenkins CI configuration
CONTRIBUTING.md       Contribution guidelines
README.md             Project README
LICENSE               EPL 2.0 license
```

## Agent Instructions

**Trust These Instructions:**
- Information in this file has been validated and tested
- Only search beyond this file if information is incomplete or found to be incorrect
- Build commands are documented with known limitations
- CI validation steps are accurately described

**When Making Changes:**
1. Identify the specific bundle(s) affected
2. Review bundle's MANIFEST.MF for dependencies
3. Check for corresponding test bundle
4. Make minimal, focused changes
5. Verify OSGi manifest updates if needed
6. Update build.properties if adding new files
7. Document any API changes

**When Tests Fail:**
1. Check if it's a UI test requiring Display/Shell
2. Verify test is using correct test harness (`useUIHarness=true`)
3. Check for resource disposal issues
4. Review test setup/teardown for proper initialization

**Common Build Issues:**
- "Non-resolvable parent POM" → Expected, this repo needs aggregator parent
- "Package does not exist" → Check MANIFEST.MF imports
- "API baseline errors" → You may have broken API compatibility
- Test hangs → Likely missing Display.syncExec/asyncExec wrapper
