/**
 * Python Application Complete CI/CD Pipeline Example
 * Demonstrates how to use the base framework with Python module for Django/Flask applications
 */
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.languages.PythonModule
import modules.quality.SecurityScanModule
import modules.deployment.HelmDeploymentModule
import modules.artifacts.ArtifactManager
import modules.integration.ServiceNowIntegration

// Define pipeline configuration
def pipelineConfig = [
    projectName: 'python-web-app',
    pythonVersion: '3.11',
    framework: 'django', // or 'flask'
    sonarProjectKey: 'com.example:python-web-app',
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
            pypi: 'pypi-releases',
            docker: 'docker-releases'
        ]
    ],
    helm: [
        chartPath: 'charts/python-app',
        releaseName: 'python-web-app',
        valuesFiles: [
            common: 'charts/python-app/values.yaml',
            dev: 'charts/python-app/values-dev.yaml',
            sit: 'charts/python-app/values-sit.yaml',
            uat: 'charts/python-app/values-uat.yaml',
            prod: 'charts/python-app/values-prod.yaml'
        ]
    ],
    serviceNow: [
        url: 'https://company.service-now.com',
        assignmentGroup: 'Python DevOps Team'
    ],
    qualityGateEnabled: true,
    testCoverage: [
        enabled: true,
        threshold: 80
    ]
]

// Initialize base pipeline
def pipeline = new BasePipeline(this, pipelineConfig)

// Initialize environment manager
def envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)

// Initialize specialized modules
def pythonModule = new PythonModule(pipeline, [
    pythonVersion: pipelineConfig.pythonVersion,
    framework: pipelineConfig.framework,
    testReportPath: 'test-results',
    coverageReportPath: 'htmlcov'
])

def securityScanner = new SecurityScanModule(pipeline)

def helmDeployer = new HelmDeploymentModule(pipeline, pipelineConfig.helm)

def artifactManager = new ArtifactManager(pipeline, [
    nexusUrl: pipelineConfig.nexus.url,
    nexusUser: pipelineConfig.nexus.user,
    nexusPassword: pipelineConfig.nexus.password,
    repositories: pipelineConfig.nexus.repositories
])

def serviceNowIntegration = new ServiceNowIntegration(pipeline, pipelineConfig.serviceNow)

// Define code checkout stage
pipeline.addStage('Source Code Checkout') {
    checkout scm
    
    // Record Git information
    pipeline.recordBuildMetric('gitBranch', env.GIT_BRANCH ?: 'unknown')
    pipeline.recordBuildMetric('gitCommit', env.GIT_COMMIT ?: 'unknown')
}

// Setup Python environment
pipeline.addStage('Setup Python Environment') {
    pythonModule.setupEnvironment()
    pythonModule.installDependencies()
}

// Parallel build and test stages
pipeline.addParallelStage('Build and Unit Tests') {
    // Build Python package
    pythonModule.buildPackage()
    
    // Run unit tests with coverage
    pythonModule.runUnitTests()
    pythonModule.generateCoverageReport()
    
    // Record test results
    def testResults = pythonModule.getTestResults()
    pipeline.recordTestResults('unit-tests', testResults)
}

pipeline.addParallelStage('Code Quality Analysis') {
    // Run linting and static analysis
    pythonModule.runLinting()
    pythonModule.runStaticAnalysis()
    
    // SonarQube analysis
    pythonModule.runSonarQubeScan(pipelineConfig.sonarProjectKey)
}

// Security scanning stage
pipeline.addStage('Security Scanning') {
    // Python dependency vulnerability scan
    securityScanner.runOwaspDependencyCheck('python')
    
    // Secrets scanning
    securityScanner.runSecretScan()
    
    // Python-specific security checks
    pythonModule.runSecurityChecks()
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

// Run integration tests in DEV
pipeline.addStage('Integration Tests') {
    pythonModule.runIntegrationTests()
    
    // Record integration test results
    def integrationResults = pythonModule.getIntegrationTestResults()
    pipeline.recordTestResults('integration-tests', integrationResults)
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

// Run system tests in SIT
pipeline.addStage('System Tests') {
    pythonModule.runSystemTests()
    
    // Record system test results
    def systemResults = pythonModule.getSystemTestResults()
    pipeline.recordTestResults('system-tests', systemResults)
}

// Manual approval for UAT deployment
pipeline.addStage('UAT Deployment Approval') {
    if (envManager.requiresApproval('uat')) {
        // Create ServiceNow change request
        def changeRequestNumber = serviceNowIntegration.createChangeRequest('uat', [
            strategy: envManager.getDeploymentStrategy('uat'),
            requester: env.BUILD_USER ?: 'Jenkins'
        ])
        
        // Manual approval
        input message: 'Approve deployment to UAT environment?', 
              ok: 'Approve',
              parameters: [
                  string(name: 'CHANGE_REQUEST', defaultValue: changeRequestNumber, description: 'ServiceNow Change Request Number')
              ]
        
        // Update change request status
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
        // Create ServiceNow change request for production
        def changeRequestNumber = serviceNowIntegration.createChangeRequest('prod', [
            strategy: envManager.getDeploymentStrategy('prod'),
            requester: env.BUILD_USER ?: 'Jenkins'
        ])
        
        // Manual approval with additional validations
        def approvalResult = input message: 'Approve deployment to PRODUCTION environment?',
                                  ok: 'Approve',
                                  parameters: [
                                      string(name: 'CHANGE_REQUEST', defaultValue: changeRequestNumber, description: 'ServiceNow Change Request Number'),
                                      booleanParam(name: 'EMERGENCY_DEPLOYMENT', defaultValue: false, description: 'Is this an emergency deployment?')
                                  ]
        
        // Update change request status
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
    
    // Use blue-green deployment for production
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
    pipeline.savePipelineReport("python-app-pipeline-report-${env.BUILD_NUMBER}.json")
    
    // Send notifications
    def report = pipeline.generatePipelineReport()
    echo "Pipeline execution completed successfully!"
    echo "Build duration: ${currentBuild.durationString}"
    echo "Deployed to environments: ${report.deploymentHistory.collect { it.environment }.join(', ')}"
}

// Execute pipeline
pipeline.execute() 