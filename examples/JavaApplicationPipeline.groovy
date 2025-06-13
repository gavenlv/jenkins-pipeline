/**
 * Java Application Complete CI/CD Pipeline Example
 * Demonstrates how to use the base framework with Java module, security scanning, and Helm deployment
 */
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.languages.JavaModule
import modules.quality.SecurityScanModule
import modules.deployment.HelmDeploymentModule
import modules.artifacts.ArtifactManager
import modules.integration.ServiceNowIntegration

// Define pipeline configuration
def pipelineConfig = [
    projectName: 'java-microservice',
    buildTool: 'maven',
    sonarProjectKey: 'com.example:java-microservice',
    environments: [
        [name: 'dev', autoPromote: true],
        [name: 'sit', autoPromote: true],
        [name: 'uat', requiresApproval: true],
        [name: 'prod', requiresApproval: true, deploymentStrategy: 'blue-green']
    ],
    helm: [
        chartPath: 'charts/java-service',
        releaseName: 'java-microservice',
        namespace: 'services',
        valuesFiles: [
            common: 'charts/java-service/values.yaml',
            dev: 'charts/java-service/values-dev.yaml',
            sit: 'charts/java-service/values-sit.yaml',
            uat: 'charts/java-service/values-uat.yaml',
            prod: 'charts/java-service/values-prod.yaml'
        ]
    ],
    nexus: [
        url: 'https://nexus.example.com',
        user: 'admin',
        password: '${NEXUS_PASSWORD}',
        repositories: [
            maven: 'maven-releases',
            docker: 'docker-releases',
            generic: 'generic-releases'
        ]
    ],
    serviceNow: [
        url: 'https://company.service-now.com',
        assignmentGroup: 'Java DevOps Team'
    ],
    qualityGateEnabled: true
]

// Initialize base pipeline
def pipeline = new BasePipeline(this, pipelineConfig)

// Initialize environment manager
def envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)

// Initialize specialized modules
def javaModule = new JavaModule(pipeline, [buildTool: pipelineConfig.buildTool])
def securityScanner = new SecurityScanModule(pipeline)
def helmDeployer = new HelmDeploymentModule(pipeline, pipelineConfig.helm)
def artifactManager = new ArtifactManager(pipeline, [
    nexusUrl: pipelineConfig.nexus.url,
    nexusUser: pipelineConfig.nexus.user,
    nexusPassword: pipelineConfig.nexus.password,
    repositories: pipelineConfig.nexus.repositories
])

def serviceNowIntegration = new ServiceNowIntegration(pipeline, pipelineConfig.serviceNow)

// Define source code checkout stage
pipeline.addStage('Source Code Checkout') {
    checkout scm
}

// Add parallel build and test stages
pipeline.addParallelStage('Build and Unit Tests') {
    javaModule.build()
    javaModule.runUnitTests()
    javaModule.generateCoverageReport()
}

pipeline.addParallelStage('Code Quality Analysis') {
    javaModule.runSonarQubeScan(pipelineConfig.sonarProjectKey)
    securityScanner.runSecretScan()
}

// Add security scanning stage
pipeline.addStage('Security Scanning') {
    securityScanner.runOwaspDependencyCheck('maven')
}

// Build and push Docker image
pipeline.addStage('Docker Image Build') {
    dockerImage = docker.build("${pipelineConfig.projectName}:${env.BUILD_NUMBER}")
    securityScanner.scanDockerImage(pipelineConfig.projectName, env.BUILD_NUMBER)
    artifactManager.pushDockerImage(
        pipelineConfig.projectName,
        env.BUILD_NUMBER,
        pipelineConfig.nexus.repositories.docker
    )
}

// Deploy to DEV environment
pipeline.addStage('Deploy to DEV') {
    def envConfig = envManager.getEnvironmentConfig('dev')
    def dockerImage = artifactManager.pullDockerImage(
        pipelineConfig.projectName,
        env.BUILD_NUMBER,
        envConfig.nexus.repositories.docker
    )
    helmDeployer.deploy('dev', "--set image=${dockerImage}")
    
    pipeline.recordDeployment('dev', [
        status: 'SUCCESS',
        image: dockerImage,
        strategy: envConfig.deploymentStrategy
    ])
}

// Run integration tests
pipeline.addStage('Integration Tests') {
    javaModule.runIntegrationTests('-Pintegration-test')
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
        helmDeployer.deploy('sit', "--set image=${dockerImage}")
        
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
    helmDeployer.deploy('uat', "--set image=${dockerImage}")
    
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
    helmDeployer.deploy('prod', "--set image=${dockerImage} --set deploymentStrategy=blue-green")
    
    pipeline.recordDeployment('prod', [
        status: 'SUCCESS',
        image: dockerImage,
        strategy: envConfig.deploymentStrategy
    ])
}

// Post-deployment activities
pipeline.addStage('Post Deployment') {
    // Save pipeline report
    pipeline.savePipelineReport("java-app-pipeline-report-${env.BUILD_NUMBER}.json")
    
    // Generate summary
    def report = pipeline.generatePipelineReport()
    echo "Pipeline execution completed successfully!"
    echo "Build duration: ${currentBuild.durationString}"
    echo "Deployed to environments: ${report.deploymentHistory.collect { it.environment }.join(', ')}"
}

// Execute pipeline
pipeline.execute()