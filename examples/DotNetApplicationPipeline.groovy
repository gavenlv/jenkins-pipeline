/**
 * .NET Application Complete CI/CD Pipeline Example
 * Demonstrates how to use the base framework for .NET Core/Framework applications
 */
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.quality.SecurityScanModule
import modules.deployment.HelmDeploymentModule
import modules.artifacts.ArtifactManager
import modules.integration.ServiceNowIntegration

// Define pipeline configuration
def pipelineConfig = [
    projectName: 'dotnet-web-api',
    dotnetVersion: '8.0',
    framework: 'net8.0',
    projectType: 'webapi', // or 'mvc', 'blazor', 'console'
    sonarProjectKey: 'com.example:dotnet-web-api',
    environments: [
        [name: 'dev', autoPromote: true],
        [name: 'sit', autoPromote: true],
        [name: 'uat', requiresApproval: true],
        [name: 'prod', requiresApproval: true, deploymentStrategy: 'blue-green']
    ],
    nexus: [
        url: 'https://nexus.example.com',
        user: 'admin',
        password: '${NEXUS_PASSWORD}',
        repositories: [
            nuget: 'nuget-releases',
            docker: 'docker-releases'
        ]
    ],
    helm: [
        chartPath: 'charts/dotnet-app',
        releaseName: 'dotnet-web-api',
        valuesFiles: [
            common: 'charts/dotnet-app/values.yaml',
            dev: 'charts/dotnet-app/values-dev.yaml',
            sit: 'charts/dotnet-app/values-sit.yaml',
            uat: 'charts/dotnet-app/values-uat.yaml',
            prod: 'charts/dotnet-app/values-prod.yaml'
        ]
    ],
    serviceNow: [
        url: 'https://company.service-now.com',
        assignmentGroup: '.NET DevOps Team'
    ],
    qualityGateEnabled: true,
    codeAnalysis: [
        enabled: true,
        ruleset: 'sonar-dotnet-rules.xml'
    ]
]

// Initialize base pipeline
def pipeline = new BasePipeline(this, pipelineConfig)

// Initialize environment manager
def envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)

// Initialize specialized modules
def securityScanner = new SecurityScanModule(pipeline)
def helmDeployer = new HelmDeploymentModule(pipeline, pipelineConfig.helm)
def artifactManager = new ArtifactManager(pipeline, [
    nexusUrl: pipelineConfig.nexus.url,
    nexusUser: pipelineConfig.nexus.user,
    nexusPassword: pipelineConfig.nexus.password,
    repositories: pipelineConfig.nexus.repositories
])
def serviceNowIntegration = new ServiceNowIntegration(pipeline, pipelineConfig.serviceNow)

// Custom .NET module functions
def setupDotNetEnvironment() {
    pipeline.script.stage("Setup .NET Environment") {
        pipeline.script.echo "Setting up .NET ${pipelineConfig.dotnetVersion} environment..."
        
        // Install .NET SDK
        pipeline.script.sh """
            # Download and install .NET SDK
            wget https://packages.microsoft.com/config/ubuntu/20.04/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
            sudo dpkg -i packages-microsoft-prod.deb
            sudo apt-get update
            sudo apt-get install -y dotnet-sdk-${pipelineConfig.dotnetVersion}
        """
        
        // Verify installation
        pipeline.script.sh "dotnet --version"
        
        pipeline.script.echo ".NET environment setup completed"
    }
}

def restorePackages() {
    pipeline.script.stage("Restore NuGet Packages") {
        pipeline.script.echo "Restoring NuGet packages..."
        pipeline.script.sh "dotnet restore"
        pipeline.script.echo "Package restoration completed"
    }
}

def buildApplication() {
    pipeline.script.stage("Build .NET Application") {
        pipeline.script.echo "Building .NET application..."
        
        def buildConfiguration = pipeline.script.env.BUILD_CONFIGURATION ?: 'Release'
        pipeline.script.sh "dotnet build --configuration ${buildConfiguration} --no-restore"
        
        // Record build metrics
        pipeline.recordBuildMetric('buildConfiguration', buildConfiguration)
        pipeline.recordBuildMetric('dotnetVersion', pipelineConfig.dotnetVersion)
        
        pipeline.script.echo ".NET application build completed"
    }
}

def runUnitTests() {
    pipeline.script.stage("Run Unit Tests") {
        pipeline.script.echo "Running .NET unit tests..."
        
        pipeline.script.sh """
            dotnet test --configuration Release --no-build --no-restore \\
                --logger trx --results-directory TestResults \\
                --collect:"XPlat Code Coverage" \\
                --settings coverlet.runsettings
        """
        
        // Publish test results
        pipeline.script.publishTestResults([
            allowEmptyResults: false,
            testResultsPattern: 'TestResults/*.trx'
        ])
        
        // Publish code coverage
        pipeline.script.publishCoverageReport([
            allowMissing: false,
            sourceFileResolver: 'NEVER_STORE',
            reportFiles: 'TestResults/*/coverage.cobertura.xml'
        ])
        
        // Record test results
        pipeline.recordTestResults('unit-tests', [
            passed: 0, // Would be populated from actual test results
            failed: 0,
            total: 0
        ])
        
        pipeline.script.echo "Unit tests completed"
    }
}

def runCodeAnalysis() {
    pipeline.script.stage("Code Analysis") {
        pipeline.script.echo "Running .NET code analysis..."
        
        // Run SonarQube analysis
        pipeline.script.sh """
            dotnet sonarscanner begin \\
                /k:"${pipelineConfig.sonarProjectKey}" \\
                /d:sonar.login="\${SONAR_TOKEN}" \\
                /d:sonar.host.url="\${SONAR_HOST_URL}" \\
                /d:sonar.cs.opencover.reportsPaths="TestResults/*/coverage.opencover.xml"
            
            dotnet build --configuration Release
            
            dotnet sonarscanner end /d:sonar.login="\${SONAR_TOKEN}"
        """
        
        pipeline.script.echo "Code analysis completed"
    }
}

def publishArtifacts() {
    pipeline.script.stage("Publish Artifacts") {
        pipeline.script.echo "Publishing .NET artifacts..."
        
        // Publish application
        pipeline.script.sh """
            dotnet publish --configuration Release --no-build --no-restore \\
                --output ./publish \\
                --framework ${pipelineConfig.framework}
        """
        
        // Create deployment package
        pipeline.script.sh """
            tar -czf ${pipelineConfig.projectName}-${pipeline.script.env.BUILD_NUMBER}.tar.gz \\
                -C ./publish .
        """
        
        // Archive artifacts
        pipeline.script.archiveArtifacts artifacts: "*.tar.gz", allowEmptyArchive: false
        
        // Register artifact
        pipeline.registerArtifact('application', [
            name: pipelineConfig.projectName,
            version: pipeline.script.env.BUILD_NUMBER,
            type: 'tar.gz',
            path: "${pipelineConfig.projectName}-${pipeline.script.env.BUILD_NUMBER}.tar.gz"
        ])
        
        pipeline.script.echo "Artifacts published successfully"
    }
}

// Define pipeline stages
pipeline.addStage('Source Code Checkout') {
    checkout scm
    
    // Record Git information
    pipeline.recordBuildMetric('gitBranch', env.GIT_BRANCH ?: 'unknown')
    pipeline.recordBuildMetric('gitCommit', env.GIT_COMMIT ?: 'unknown')
}

// Setup .NET environment
pipeline.addStage('Setup Environment') {
    setupDotNetEnvironment()
    restorePackages()
}

// Parallel build and analysis stages
pipeline.addParallelStage('Build and Test') {
    buildApplication()
    runUnitTests()
}

pipeline.addParallelStage('Quality Analysis') {
    runCodeAnalysis()
    
    // Security scanning
    securityScanner.runOwaspDependencyCheck('dotnet')
    securityScanner.runSecretScan()
}

// Publish artifacts
pipeline.addStage('Publish Artifacts') {
    publishArtifacts()
}

// Build and scan Docker image
pipeline.addStage('Docker Image Build') {
    def dockerImage = docker.build("${pipelineConfig.projectName}:${env.BUILD_NUMBER}")
    
    // Scan Docker image for vulnerabilities
    securityScanner.scanDockerImage(pipelineConfig.projectName, env.BUILD_NUMBER)
    
    // Push to registry
    def envConfig = envManager.getEnvironmentConfig('dev')
    artifactManager.pushDockerImage(
        pipelineConfig.projectName,
        env.BUILD_NUMBER,
        envConfig.nexus.repositories.docker
    )
    
    // Register artifact
    pipeline.registerArtifact('docker', [
        name: pipelineConfig.projectName,
        version: env.BUILD_NUMBER,
        registry: envConfig.nexus.url,
        repository: envConfig.nexus.repositories.docker
    ])
}

// Deploy to DEV environment
pipeline.addStage('Deploy to DEV') {
    def envConfig = envManager.getEnvironmentConfig('dev')
    
    def dockerImage = artifactManager.pullDockerImage(
        pipelineConfig.projectName,
        env.BUILD_NUMBER,
        envConfig.nexus.repositories.docker
    )
    
    helmDeployer.deploy('dev', "--set image.repository=${dockerImage.split(':')[0]} --set image.tag=${env.BUILD_NUMBER}")
    
    // Record deployment
    pipeline.recordDeployment('dev', [
        status: 'SUCCESS',
        image: dockerImage,
        strategy: envConfig.deploymentStrategy
    ])
}

// Deploy to SIT environment
pipeline.addStage('Deploy to SIT') {
    if (envManager.supportsAutoPromotion('sit')) {
        def envConfig = envManager.getEnvironmentConfig('sit')
        
        def dockerImage = artifactManager.pullDockerImage(
            pipelineConfig.projectName,
            env.BUILD_NUMBER,
            envConfig.nexus.repositories.docker
        )
        
        helmDeployer.deploy('sit', "--set image.repository=${dockerImage.split(':')[0]} --set image.tag=${env.BUILD_NUMBER}")
        
        pipeline.recordDeployment('sit', [
            status: 'SUCCESS',
            image: dockerImage,
            strategy: envConfig.deploymentStrategy
        ])
    }
}

// Manual approval for UAT deployment
pipeline.addStage('UAT Deployment Approval') {
    if (envManager.requiresApproval('uat')) {
        def changeRequestNumber = serviceNowIntegration.createChangeRequest('uat', [
            strategy: envManager.getDeploymentStrategy('uat'),
            requester: env.BUILD_USER ?: 'Jenkins'
        ])
        
        input message: 'Approve deployment to UAT environment?', 
              ok: 'Approve',
              parameters: [
                  string(name: 'CHANGE_REQUEST', defaultValue: changeRequestNumber, description: 'ServiceNow Change Request Number')
              ]
        
        serviceNowIntegration.updateChangeRequestStatus(changeRequestNumber, 'SCHEDULED', 'Deployment approved and scheduled')
    }
}

// Deploy to UAT environment
pipeline.addStage('Deploy to UAT') {
    def envConfig = envManager.getEnvironmentConfig('uat')
    
    def dockerImage = artifactManager.pullDockerImage(
        pipelineConfig.projectName,
        env.BUILD_NUMBER,
        envConfig.nexus.repositories.docker
    )
    
    helmDeployer.deploy('uat', "--set image.repository=${dockerImage.split(':')[0]} --set image.tag=${env.BUILD_NUMBER}")
    
    pipeline.recordDeployment('uat', [
        status: 'SUCCESS',
        image: dockerImage,
        strategy: envConfig.deploymentStrategy
    ])
}

// Manual approval for production deployment
pipeline.addStage('Production Deployment Approval') {
    if (envManager.requiresApproval('prod')) {
        def changeRequestNumber = serviceNowIntegration.createChangeRequest('prod', [
            strategy: envManager.getDeploymentStrategy('prod'),
            requester: env.BUILD_USER ?: 'Jenkins'
        ])
        
        input message: 'Approve deployment to PRODUCTION environment?',
              ok: 'Approve',
              parameters: [
                  string(name: 'CHANGE_REQUEST', defaultValue: changeRequestNumber, description: 'ServiceNow Change Request Number'),
                  booleanParam(name: 'EMERGENCY_DEPLOYMENT', defaultValue: false, description: 'Is this an emergency deployment?')
              ]
        
        serviceNowIntegration.updateChangeRequestStatus(changeRequestNumber, 'IMPLEMENT', 'Production deployment approved and starting')
    }
}

// Deploy to PROD environment
pipeline.addStage('Deploy to PROD') {
    def envConfig = envManager.getEnvironmentConfig('prod')
    
    def dockerImage = artifactManager.pullDockerImage(
        pipelineConfig.projectName,
        env.BUILD_NUMBER,
        envConfig.nexus.repositories.docker
    )
    
    helmDeployer.deploy('prod', "--set image.repository=${dockerImage.split(':')[0]} --set image.tag=${env.BUILD_NUMBER} --set deploymentStrategy=blue-green")
    
    pipeline.recordDeployment('prod', [
        status: 'SUCCESS',
        image: dockerImage,
        strategy: envConfig.deploymentStrategy
    ])
}

// Post-deployment activities
pipeline.addStage('Post Deployment') {
    // Save pipeline report
    pipeline.savePipelineReport("dotnet-app-pipeline-report-${env.BUILD_NUMBER}.json")
    
    // Generate summary
    def report = pipeline.generatePipelineReport()
    echo "Pipeline execution completed successfully!"
    echo "Build duration: ${currentBuild.durationString}"
    echo "Deployed to environments: ${report.deploymentHistory.collect { it.environment }.join(', ')}"
}

// Execute pipeline
pipeline.execute() 