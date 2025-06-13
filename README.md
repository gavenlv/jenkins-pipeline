# Universal Jenkins Pipeline Framework

A comprehensive, enterprise-ready Jenkins pipeline framework that supports multiple programming languages, multi-environment deployments, and integrates with various DevOps tools including ServiceNow, Nexus, SonarQube, and security scanning tools.

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Installation](#installation)
5. [Quick Start](#quick-start)
6. [Configuration Guide](#configuration-guide)
7. [Language Modules](#language-modules)
8. [Environment Management](#environment-management)
9. [Security & Quality](#security--quality)
10. [ServiceNow Integration](#servicenow-integration)
11. [Pipeline Examples](#pipeline-examples)
12. [Testing](#testing)
13. [Troubleshooting](#troubleshooting)
14. [Best Practices](#best-practices)

## Features

- **🌐 Multi-Language Support**: Java, Python, Node.js/TypeScript, .NET
- **🏗️ Multi-Environment Management**: dev, sit, uat, prod with automated promotion
- **🔒 Security Scanning**: OWASP dependency check, container scanning, secrets detection
- **✅ Quality Gates**: SonarQube integration with configurable quality standards
- **📦 Artifact Management**: Nexus integration for multi-environment artifact storage
- **🚀 Deployment Automation**: Helm-based Kubernetes deployments with blue-green support
- **🎫 ServiceNow Integration**: Automated change request creation and incident management
- **📊 Comprehensive Reporting**: Pipeline execution reports with metrics and deployment history
- **🔄 Parallel Execution**: Optimized pipeline performance with parallel stages
- **🛡️ Error Handling**: Robust error handling with automatic incident creation

## Architecture

```
pipeline-framework/
├── src/modules/
│   ├── BasePipeline.groovy                    # Core pipeline framework
│   ├── EnvironmentConfigManager.groovy        # Multi-environment configuration
│   ├── artifacts/
│   │   └── ArtifactManager.groovy             # Nexus artifact management
│   ├── deployment/
│   │   └── HelmDeploymentModule.groovy        # Kubernetes deployment via Helm
│   ├── integration/
│   │   └── ServiceNowIntegration.groovy       # ServiceNow ITSM integration
│   ├── languages/
│   │   ├── JavaModule.groovy                  # Java/Maven/Gradle support
│   │   ├── PythonModule.groovy                # Python/Django/Flask support
│   │   ├── TypeScriptModule.groovy            # Node.js/TypeScript/React support
│   │   └── SQLModule.groovy                   # Database deployment support
│   └── quality/
│       └── SecurityScanModule.groovy          # Security scanning integration
├── examples/
│   ├── JavaApplicationPipeline.groovy         # Complete Java pipeline example
│   ├── PythonApplicationPipeline.groovy       # Complete Python pipeline example
│   ├── NodeJsApplicationPipeline.groovy       # Complete Node.js pipeline example
│   └── DotNetApplicationPipeline.groovy       # Complete .NET pipeline example
├── tests/
│   └── (test files)
└── docs/
    └── (additional documentation)
```

## Prerequisites

### Required Tools

- **Jenkins**: Version 2.400+ with Pipeline plugin
- **Docker**: For containerization and image scanning
- **Kubernetes**: For application deployment
- **Helm**: Version 3.x for Kubernetes package management

### Required Jenkins Plugins

```bash
# Core plugins
- pipeline-stage-view
- workflow-aggregator
- docker-workflow
- kubernetes
- helm

# Quality & Security plugins
- sonar
- owasp-dependency-check
- htmlpublisher

# Integration plugins
- http_request
- credentials-binding
- build-user-vars-plugin
```

### External Systems

- **Nexus Repository**: For artifact management
- **SonarQube**: For code quality analysis
- **ServiceNow**: For change management (optional)

## Installation

### 1. Setup Jenkins Shared Library

1. In Jenkins, go to `Manage Jenkins` > `Configure System`
2. Navigate to `Global Pipeline Libraries`
3. Add a new library:
   - **Name**: `pipeline-library`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Repository URL**: `https://your-git-repo/pipeline-framework.git`

### 2. Configure Credentials

Create the following credentials in Jenkins:

```bash
# Nexus credentials
nexus-credentials (Username with password)

# SonarQube token
sonar-token (Secret text)

# ServiceNow credentials
servicenow-credentials (Username with password)

# Container registry credentials
docker-registry-credentials (Username with password)
```

### 3. Global Tool Configuration

Configure the following tools in `Manage Jenkins` > `Global Tool Configuration`:

- **JDK**: Java 11, 17, 21
- **Maven**: Version 3.8+
- **Node.js**: Version 16, 18, 20
- **Docker**: Latest stable version

## Quick Start

### 1. Basic Pipeline Setup

Create a `Jenkinsfile` in your project root:

```groovy
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.languages.JavaModule

// Define your pipeline configuration
def pipelineConfig = [
    projectName: 'my-awesome-app',
    buildTool: 'maven',
    sonarProjectKey: 'com.company:my-awesome-app',
    environments: [
        [name: 'dev', autoPromote: true],
        [name: 'sit', autoPromote: true],
        [name: 'uat', requiresApproval: true],
        [name: 'prod', requiresApproval: true, deploymentStrategy: 'blue-green']
    ],
    nexus: [
        url: 'https://nexus.company.com',
        user: 'deployment-user',
        password: '${NEXUS_PASSWORD}'
    ],
    qualityGateEnabled: true
]

// Initialize pipeline
def pipeline = new BasePipeline(this, pipelineConfig)
def envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)
def javaModule = new JavaModule(pipeline, [buildTool: pipelineConfig.buildTool])

// Define stages
pipeline.addStage('Checkout') {
    checkout scm
}

pipeline.addStage('Build & Test') {
    javaModule.build()
    javaModule.runUnitTests()
}

// Execute pipeline
pipeline.execute()
```

### 2. Jenkins Job Configuration

1. Create a new Pipeline job in Jenkins
2. In the Pipeline section, select "Pipeline script from SCM"
3. Configure your Git repository containing the Jenkinsfile
4. Save and run the job

## Configuration Guide

### Environment Configuration

The framework supports four standard environments with automatic promotion:

```groovy
environments: [
    [
        name: 'dev',
        displayName: 'Development',
        autoPromote: true,
        replicas: 1,
        resources: [
            requests: [cpu: '100m', memory: '256Mi'],
            limits: [cpu: '500m', memory: '512Mi']
        ]
    ],
    [
        name: 'sit',
        displayName: 'System Integration Testing',
        autoPromote: true,
        replicas: 1,
        resources: [
            requests: [cpu: '200m', memory: '512Mi'],
            limits: [cpu: '1000m', memory: '1Gi']
        ]
    ],
    [
        name: 'uat',
        displayName: 'User Acceptance Testing',
        requiresApproval: true,
        replicas: 2,
        resources: [
            requests: [cpu: '500m', memory: '1Gi'],
            limits: [cpu: '2000m', memory: '2Gi']
        ]
    ],
    [
        name: 'prod',
        displayName: 'Production',
        requiresApproval: true,
        deploymentStrategy: 'blue-green',
        replicas: 3,
        resources: [
            requests: [cpu: '1000m', memory: '2Gi'],
            limits: [cpu: '4000m', memory: '4Gi']
        ]
    ]
]
```

### Nexus Configuration

Configure environment-specific Nexus repositories:

```groovy
nexus: [
    url: 'https://nexus.company.com',
    user: 'deployment-user',
    password: '${NEXUS_PASSWORD}',
    
    // Environment-specific URLs (optional)
    devUrl: 'https://nexus-dev.company.com',
    sitUrl: 'https://nexus-sit.company.com',
    uatUrl: 'https://nexus-uat.company.com',
    prodUrl: 'https://nexus-prod.company.com',
    
    repositories: [
        maven: 'maven-releases',
        docker: 'docker-releases',
        npm: 'npm-releases',
        nuget: 'nuget-releases'
    ]
]
```

### Helm Configuration

Configure Helm charts and values files:

```groovy
helm: [
    chartPath: 'charts/my-app',
    releaseName: 'my-awesome-app',
    namespace: 'applications',
    timeout: '10m',
    valuesFiles: [
        common: 'charts/my-app/values.yaml',
        dev: 'charts/my-app/values-dev.yaml',
        sit: 'charts/my-app/values-sit.yaml',
        uat: 'charts/my-app/values-uat.yaml',
        prod: 'charts/my-app/values-prod.yaml'
    ]
]
```

### ServiceNow Configuration

Enable change management integration:

```groovy
serviceNow: [
    url: 'https://company.service-now.com',
    username: 'jenkins-integration',
    password: '${SERVICENOW_PASSWORD}',
    assignmentGroup: 'DevOps Engineering',
    fieldMappings: [
        application: 'u_application',
        environment: 'u_environment',
        buildNumber: 'u_build_number',
        gitCommit: 'u_git_commit'
    ]
]
```

## Language Modules

### Java Module

Complete Java/Spring Boot support:

```groovy
def javaModule = new JavaModule(pipeline, [
    buildTool: 'maven',  // or 'gradle'
    javaVersion: '17',
    testReportPath: 'target/surefire-reports',
    coverageReportPath: 'target/jacoco',
    integrationTestProfile: 'integration-test'
])

// Available methods
javaModule.build()
javaModule.runUnitTests()
javaModule.runIntegrationTests()
javaModule.runSonarQubeScan('my-project-key')
javaModule.generateCoverageReport()
```

### Python Module

Django/Flask application support:

```groovy
def pythonModule = new PythonModule(pipeline, [
    pythonVersion: '3.11',
    framework: 'django',  // or 'flask'
    requirementsFile: 'requirements.txt',
    testFramework: 'pytest',
    testReportPath: 'test-results'
])

// Available methods
pythonModule.setupEnvironment()
pythonModule.installDependencies()
pythonModule.runUnitTests()
pythonModule.runLinting()
pythonModule.runSecurityChecks()
pythonModule.buildPackage()
```

### TypeScript/Node.js Module

React/Angular/Vue.js support:

```groovy
def typeScriptModule = new TypeScriptModule(pipeline, [
    nodeVersion: '18.17.0',
    packageManager: 'npm',  // or 'yarn', 'pnpm'
    framework: 'react',     // or 'angular', 'vue', 'express'
    buildCommand: 'npm run build',
    testCommand: 'npm test'
])

// Available methods
typeScriptModule.setupNodeEnvironment()
typeScriptModule.installDependencies()
typeScriptModule.runLinting()
typeScriptModule.runUnitTests()
typeScriptModule.buildApplication()
typeScriptModule.runE2ETests()
```

## Environment Management

### Automatic Promotion

The framework supports automatic promotion between environments:

```groovy
// DEV: Auto-deploy on successful build
pipeline.addStage('Deploy to DEV') {
    if (envManager.supportsAutoPromotion('dev')) {
        deployToEnvironment('dev')
    }
}

// SIT: Auto-promote from DEV if tests pass
pipeline.addStage('Deploy to SIT') {
    if (envManager.supportsAutoPromotion('sit') && allTestsPassed()) {
        deployToEnvironment('sit')
    }
}

// UAT: Manual approval required
pipeline.addStage('UAT Deployment Approval') {
    if (envManager.requiresApproval('uat')) {
        def changeRequest = serviceNow.createChangeRequest('uat', deploymentDetails)
        
        input message: 'Approve UAT deployment?',
              ok: 'Approve',
              parameters: [
                  string(name: 'CHANGE_REQUEST', defaultValue: changeRequest)
              ]
    }
}
```

### Environment Validation

Validate environment configurations before deployment:

```groovy
def validation = envManager.validateEnvironmentConfig('prod')
if (!validation.valid) {
    error "Environment configuration invalid: ${validation.errors.join(', ')}"
}
```

## Security & Quality

### Security Scanning

Comprehensive security scanning pipeline:

```groovy
def securityScanner = new SecurityScanModule(pipeline, [
    owaspCheckVersion: '8.4.0',
    trivyVersion: '0.45.0',
    reportPath: 'security-reports'
])

// Dependency vulnerability scanning
securityScanner.runOwaspDependencyCheck('maven')

// Container image scanning
securityScanner.scanDockerImage('my-app', env.BUILD_NUMBER)

// Secrets detection
securityScanner.runSecretScan()
```

### Quality Gates

Configure SonarQube quality gates:

```groovy
// Quality gate configuration
qualityGate: [
    enabled: true,
    sonarUrl: 'https://sonar.company.com',
    projectKey: 'com.company:my-app',
    qualityGateId: 'Production',
    conditions: [
        coverage: 80,
        duplicatedLines: 3,
        maintainabilityRating: 'A',
        reliabilityRating: 'A',
        securityRating: 'A'
    ]
]
```

## ServiceNow Integration

### Change Request Workflow

Automatic change request creation for production deployments:

```groovy
// Create change request
def changeRequestNumber = serviceNow.createChangeRequest('prod', [
    strategy: 'blue-green',
    requester: env.BUILD_USER,
    scheduledStart: '2024-01-15 02:00:00',
    scheduledEnd: '2024-01-15 04:00:00'
])

// Update status during deployment
serviceNow.updateChangeRequestStatus(changeRequestNumber, 'IMPLEMENT', 
    'Deployment started at ${new Date()}')

// Close change request on success
serviceNow.updateChangeRequestStatus(changeRequestNumber, 'CLOSED', 
    'Deployment completed successfully')
```

### Incident Management

Automatic incident creation on failures:

```groovy
try {
    // Deployment logic
    deployToProduction()
} catch (Exception e) {
    // Create incident for production issues
    def incidentNumber = serviceNow.createIncident('prod', [
        type: 'Deployment Failure',
        error: e.getMessage(),
        severity: '1',  // Critical for production
        details: "Pipeline failed during production deployment"
    ])
    
    throw e
}
```

## Pipeline Examples

### Complete Java Spring Boot Pipeline

```groovy
@Library('pipeline-library') _

import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.languages.JavaModule
import modules.quality.SecurityScanModule
import modules.integration.ServiceNowIntegration

def pipelineConfig = [
    projectName: 'spring-boot-api',
    buildTool: 'maven',
    sonarProjectKey: 'com.company:spring-boot-api',
    environments: [
        [name: 'dev', autoPromote: true],
        [name: 'sit', autoPromote: true],
        [name: 'uat', requiresApproval: true],
        [name: 'prod', requiresApproval: true, deploymentStrategy: 'blue-green']
    ],
    nexus: [
        url: 'https://nexus.company.com',
        repositories: [maven: 'maven-releases', docker: 'docker-releases']
    ],
    serviceNow: [
        url: 'https://company.service-now.com',
        assignmentGroup: 'Java DevOps Team'
    ]
]

def pipeline = new BasePipeline(this, pipelineConfig)
def envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)
def javaModule = new JavaModule(pipeline, [buildTool: 'maven'])
def securityScanner = new SecurityScanModule(pipeline)
def serviceNow = new ServiceNowIntegration(pipeline, pipelineConfig.serviceNow)

// Pipeline stages
pipeline.addStage('Source Checkout') {
    checkout scm
    pipeline.recordBuildMetric('gitCommit', env.GIT_COMMIT)
}

pipeline.addParallelStage('Build & Unit Tests') {
    javaModule.build()
    javaModule.runUnitTests()
    javaModule.generateCoverageReport()
}

pipeline.addParallelStage('Code Quality') {
    javaModule.runSonarQubeScan(pipelineConfig.sonarProjectKey)
    securityScanner.runSecretScan()
}

pipeline.addStage('Security Scanning') {
    securityScanner.runOwaspDependencyCheck('maven')
}

pipeline.addStage('Docker Build') {
    def image = docker.build("${pipelineConfig.projectName}:${env.BUILD_NUMBER}")
    securityScanner.scanDockerImage(pipelineConfig.projectName, env.BUILD_NUMBER)
}

pipeline.addStage('Deploy to DEV') {
    deployToEnvironment('dev')
    javaModule.runIntegrationTests()
}

pipeline.addStage('Deploy to SIT') {
    if (envManager.supportsAutoPromotion('sit')) {
        deployToEnvironment('sit')
    }
}

pipeline.addStage('UAT Approval') {
    if (envManager.requiresApproval('uat')) {
        def changeRequest = serviceNow.createChangeRequest('uat', [:])
        input message: 'Approve UAT deployment?', ok: 'Approve'
    }
}

pipeline.addStage('Deploy to UAT') {
    deployToEnvironment('uat')
}

pipeline.addStage('Production Approval') {
    if (envManager.requiresApproval('prod')) {
        def changeRequest = serviceNow.createChangeRequest('prod', [:])
        input message: 'Approve PRODUCTION deployment?', ok: 'Approve'
    }
}

pipeline.addStage('Deploy to PROD') {
    deployToEnvironment('prod')
}

pipeline.addStage('Post Deployment') {
    pipeline.savePipelineReport("pipeline-report-${env.BUILD_NUMBER}.json")
}

def deployToEnvironment(environment) {
    def envConfig = envManager.getEnvironmentConfig(environment)
    // Deployment logic here
    pipeline.recordDeployment(environment, [status: 'SUCCESS'])
}

pipeline.execute()
```

## Testing

### Running Unit Tests

The framework includes comprehensive unit tests for all modules:

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew test --tests "*BasePipelineTest*"
./gradlew test --tests "*JavaModuleTest*"
./gradlew test --tests "*SecurityScanModuleTest*"
```

### Integration Testing

Test the framework with sample applications:

```bash
# Test with sample Java app
cd tests/sample-apps/java-app
jenkins-cli build java-app-pipeline

# Test with sample Python app
cd tests/sample-apps/python-app
jenkins-cli build python-app-pipeline
```

### Pipeline Validation

Validate your pipeline configuration:

```groovy
// Add to your Jenkinsfile for validation
pipeline.addStage('Validate Configuration') {
    def validation = envManager.validateEnvironmentConfig('prod')
    if (!validation.valid) {
        error "Configuration validation failed: ${validation.errors}"
    }
    
    if (validation.warnings) {
        echo "Configuration warnings: ${validation.warnings}"
    }
}
```

## Troubleshooting

### Common Issues

#### 1. Library Not Found
```
ERROR: Library 'pipeline-library' not found
```
**Solution**: Ensure the shared library is properly configured in Jenkins Global Pipeline Libraries.

#### 2. Credential Issues
```
ERROR: Could not resolve credentials 'nexus-credentials'
```
**Solution**: Create the required credentials in Jenkins credential store.

#### 3. SonarQube Quality Gate Timeout
```
ERROR: Timeout waiting for quality gate result
```
**Solution**: Increase the timeout or check SonarQube webhook configuration.

#### 4. Docker Image Build Failures
```
ERROR: Cannot connect to Docker daemon
```
**Solution**: Ensure Docker is available on Jenkins agents and proper permissions are set.

### Debug Mode

Enable debug logging for troubleshooting:

```groovy
def pipelineConfig = [
    projectName: 'my-app',
    debug: true,  // Enable debug logging
    // ... other config
]
```

### Log Analysis

Pipeline execution logs are structured for easy analysis:

```bash
# Search for specific errors
grep "ERROR" jenkins-build.log

# Check deployment status
grep "recordDeployment" jenkins-build.log

# Analyze quality gate results
grep "qualityGateResults" jenkins-build.log
```

## Best Practices

### 1. Pipeline Design

- **Use Parallel Stages**: Maximize efficiency with parallel execution
- **Fail Fast**: Run quick checks (linting, unit tests) before expensive operations
- **Clean Workspace**: Always clean workspace between builds
- **Resource Management**: Set appropriate timeouts and resource limits

### 2. Security

- **Credential Management**: Use Jenkins credential store, never hardcode secrets
- **Least Privilege**: Grant minimum required permissions
- **Scan Everything**: Scan dependencies, code, and container images
- **Quality Gates**: Enforce security quality gates

### 3. Environment Management

- **Configuration as Code**: Store environment configs in version control
- **Environment Parity**: Keep environments as similar as possible
- **Automated Testing**: Test in each environment before promotion
- **Rollback Strategy**: Always have a rollback plan

### 4. Monitoring & Observability

- **Comprehensive Logging**: Log all important pipeline events
- **Metrics Collection**: Track build times, success rates, deployment frequency
- **Alerting**: Set up alerts for pipeline failures
- **Dashboard**: Create dashboards for pipeline visibility

### 5. Performance Optimization

- **Caching**: Cache dependencies (npm modules, Maven artifacts)
- **Incremental Builds**: Use incremental builds where possible
- **Resource Allocation**: Optimize Jenkins agent resources
- **Pipeline Parallelization**: Identify and parallelize independent tasks

## Contributing

### Development Guidelines

1. **Code Style**: Follow Groovy coding standards
2. **Testing**: Add unit tests for all new functionality
3. **Documentation**: Update documentation for new features
4. **Backward Compatibility**: Maintain backward compatibility

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Update documentation
5. Submit pull request with detailed description

## Support

### Getting Help

- **Documentation**: Check this README and inline code documentation
- **Issues**: Create GitHub issues for bugs and feature requests
- **Community**: Join our Slack channel for discussions

### Reporting Issues

When reporting issues, please include:

- Jenkins version and installed plugins
- Pipeline configuration (sanitized)
- Complete error logs
- Steps to reproduce

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

**Happy Building! 🚀**