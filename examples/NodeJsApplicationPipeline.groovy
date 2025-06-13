/**
 * Node.js Application Complete CI/CD Pipeline Example
 * Demonstrates how to use the base framework with TypeScript/JavaScript module for React/Angular/Vue applications
 */
@Library('pipeline-library') _

// Import required modules
import modules.BasePipeline
import modules.EnvironmentConfigManager
import modules.languages.TypeScriptModule
import modules.quality.SecurityScanModule
import modules.deployment.HelmDeploymentModule
import modules.artifacts.ArtifactManager
import modules.integration.ServiceNowIntegration

// Define pipeline configuration
def pipelineConfig = [
    projectName: 'nodejs-web-app',
    nodeVersion: '18.17.0',
    packageManager: 'npm', // or 'yarn' or 'pnpm'
    framework: 'react', // or 'angular', 'vue', 'express'
    sonarProjectKey: 'com.example:nodejs-web-app',
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
            npm: 'npm-releases',
            docker: 'docker-releases'
        ]
    ],
    helm: [
        chartPath: 'charts/nodejs-app',
        releaseName: 'nodejs-web-app',
        valuesFiles: [
            common: 'charts/nodejs-app/values.yaml',
            dev: 'charts/nodejs-app/values-dev.yaml',
            sit: 'charts/nodejs-app/values-sit.yaml',
            uat: 'charts/nodejs-app/values-uat.yaml',
            prod: 'charts/nodejs-app/values-prod.yaml'
        ]
    ],
    serviceNow: [
        url: 'https://company.service-now.com',
        assignmentGroup: 'Frontend DevOps Team'
    ],
    qualityGateEnabled: true
]

// Initialize base pipeline
def pipeline = new BasePipeline(this, pipelineConfig)

// Initialize environment manager
def envManager = new EnvironmentConfigManager(pipeline, pipelineConfig)

// Initialize specialized modules
def typeScriptModule = new TypeScriptModule(pipeline, [
    nodeVersion: pipelineConfig.nodeVersion,
    packageManager: pipelineConfig.packageManager,
    framework: pipelineConfig.framework
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

// Execute pipeline
pipeline.execute() 