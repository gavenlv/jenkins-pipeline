package modules.integration

import modules.BasePipeline

/**
 * ServiceNow Integration Module for managing deployment tickets and change requests
 */
class ServiceNowIntegration {
    private BasePipeline pipeline
    private String baseUrl
    private String username
    private String password
    private String assignmentGroup
    private Map<String, String> fieldMappings

    /**
     * Initialize ServiceNow integration
     * @param pipeline Base pipeline instance
     * @param config ServiceNow configuration
     */
    ServiceNowIntegration(BasePipeline pipeline, Map config = [:]) {
        this.pipeline = pipeline
        this.baseUrl = config.url ?: pipeline.script.error("ServiceNow URL must be configured")
        this.username = config.username ?: pipeline.script.error("ServiceNow username must be configured")
        this.password = config.password ?: pipeline.script.error("ServiceNow password must be configured")
        this.assignmentGroup = config.assignmentGroup ?: 'DevOps Team'
        this.fieldMappings = config.fieldMappings ?: getDefaultFieldMappings()
        pipeline.script.echo "Initialized ServiceNow integration with URL: ${baseUrl}"
    }

    /**
     * Get default field mappings for ServiceNow
     */
    private Map<String, String> getDefaultFieldMappings() {
        return [
            'application': 'u_application',
            'environment': 'u_environment',
            'buildNumber': 'u_build_number',
            'gitCommit': 'u_git_commit',
            'pipelineReport': 'u_pipeline_report',
            'deploymentStrategy': 'u_deployment_strategy',
            'approver': 'u_approver'
        ]
    }

    /**
     * Create a change request for deployment
     * @param environment Target environment
     * @param deploymentDetails Deployment details
     * @return Change request number
     */
    String createChangeRequest(String environment, Map deploymentDetails) {
        pipeline.script.stage("Create ServiceNow Change Request") {
            pipeline.script.echo "Creating ServiceNow change request for ${environment} deployment..."
            
            def pipelineReport = pipeline.generatePipelineReport()
            def changeRequestPayload = buildChangeRequestPayload(environment, deploymentDetails, pipelineReport)
            
            def response = makeServiceNowRequest('POST', '/api/now/table/change_request', changeRequestPayload)
            def changeNumber = response.result.number
            
            pipeline.script.echo "ServiceNow change request created successfully: ${changeNumber}"
            
            // Record deployment information
            pipeline.recordDeployment(environment, [
                status: 'CHANGE_REQUEST_CREATED',
                changeRequestNumber: changeNumber,
                timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
            ])
            
            return changeNumber
        }
    }

    /**
     * Create an incident ticket for deployment issues
     * @param environment Environment where issue occurred
     * @param issueDetails Issue details
     * @return Incident number
     */
    String createIncident(String environment, Map issueDetails) {
        pipeline.script.stage("Create ServiceNow Incident") {
            pipeline.script.echo "Creating ServiceNow incident for ${environment} deployment issue..."
            
            def incidentPayload = buildIncidentPayload(environment, issueDetails)
            
            def response = makeServiceNowRequest('POST', '/api/now/table/incident', incidentPayload)
            def incidentNumber = response.result.number
            
            pipeline.script.echo "ServiceNow incident created successfully: ${incidentNumber}"
            
            return incidentNumber
        }
    }

    /**
     * Update change request status
     * @param changeRequestNumber Change request number
     * @param status New status
     * @param workNotes Additional work notes
     */
    void updateChangeRequestStatus(String changeRequestNumber, String status, String workNotes = '') {
        pipeline.script.stage("Update Change Request Status") {
            pipeline.script.echo "Updating change request ${changeRequestNumber} status to ${status}"
            
            def updatePayload = [
                state: getServiceNowStateValue(status),
                work_notes: workNotes ?: "Status updated by Jenkins pipeline at ${new Date().format('yyyy-MM-dd HH:mm:ss')}"
            ]
            
            makeServiceNowRequest('PATCH', "/api/now/table/change_request?number=${changeRequestNumber}", updatePayload)
            
            pipeline.script.echo "Change request status updated successfully"
        }
    }

    /**
     * Get ServiceNow state value for status
     */
    private String getServiceNowStateValue(String status) {
        switch (status.toUpperCase()) {
            case 'NEW': return '-5'
            case 'ASSESS': return '-4'
            case 'AUTHORIZE': return '-3'
            case 'SCHEDULED': return '-2'
            case 'IMPLEMENT': return '-1'
            case 'REVIEW': return '0'
            case 'CLOSED': return '3'
            case 'CANCELLED': return '4'
            default: return '-5'
        }
    }

    /**
     * Build change request payload
     */
    private Map buildChangeRequestPayload(String environment, Map deploymentDetails, Map pipelineReport) {
        return [
            short_description: "Deployment Request: ${pipeline.config.projectName} to ${environment.toUpperCase()} environment",
            description: buildDeploymentDescription(environment, deploymentDetails, pipelineReport),
            category: "Deployment",
            subcategory: "Application Deployment",
            priority: environment == 'prod' ? '2' : '3',
            risk: environment == 'prod' ? '3' : '2',
            impact: environment == 'prod' ? '2' : '3',
            assignment_group: assignmentGroup,
            cmdb_ci: pipeline.config.projectName,
            type: 'Standard',
            (fieldMappings.application): pipeline.config.projectName,
            (fieldMappings.environment): environment,
            (fieldMappings.buildNumber): pipeline.script.env.BUILD_NUMBER,
            (fieldMappings.gitCommit): pipelineReport.buildMetrics.gitCommit,
            (fieldMappings.pipelineReport): pipeline.script.writeJSON(returnText: true, json: pipelineReport),
            (fieldMappings.deploymentStrategy): deploymentDetails.strategy ?: 'rolling-update',
            (fieldMappings.approver): pipeline.script.env.BUILD_USER ?: 'Jenkins'
        ]
    }

    /**
     * Build incident payload
     */
    private Map buildIncidentPayload(String environment, Map issueDetails) {
        return [
            short_description: "Deployment Issue: ${pipeline.config.projectName} in ${environment.toUpperCase()} environment",
            description: buildIncidentDescription(environment, issueDetails),
            category: "Deployment",
            subcategory: "Application Deployment",
            priority: environment == 'prod' ? '1' : '2',
            impact: environment == 'prod' ? '1' : '2',
            urgency: environment == 'prod' ? '1' : '2',
            assignment_group: assignmentGroup,
            cmdb_ci: pipeline.config.projectName,
            (fieldMappings.application): pipeline.config.projectName,
            (fieldMappings.environment): environment,
            (fieldMappings.buildNumber): pipeline.script.env.BUILD_NUMBER
        ]
    }

    /**
     * Build deployment description for ServiceNow
     */
    private String buildDeploymentDescription(String environment, Map deploymentDetails, Map pipelineReport) {
        def description = """
Deployment Request Details:
- Application Name: ${pipeline.config.projectName}
- Target Environment: ${environment.toUpperCase()}
- Build Number: ${pipeline.script.env.BUILD_NUMBER}
- Git Branch: ${pipelineReport.buildMetrics.gitBranch}
- Git Commit: ${pipelineReport.buildMetrics.gitCommit}
- Requester: ${pipeline.script.env.BUILD_USER ?: 'Jenkins'}
- Deployment Strategy: ${deploymentDetails.strategy ?: 'rolling-update'}

Quality Gate Results:
${pipelineReport.qualityGateResults.collect { k, v -> "- ${k}: ${v.passed ? 'PASSED' : 'FAILED'}" }.join('\n')}

Test Results Summary:
${pipelineReport.testResults.collect { k, v -> "- ${k}: Passed ${v.passed ?: 0}, Failed ${v.failed ?: 0}" }.join('\n')}

Security Scan Results:
${pipelineReport.securityScanResults.collect { k, v -> "- ${k}: ${v.status}" }.join('\n')}

Artifact Information:
${pipelineReport.artifactRegistry.collect { k, v -> "- ${k}: ${v.size()} artifacts" }.join('\n')}

For detailed reports, please refer to the Jenkins build page.
        """
        return description.trim()
    }

    /**
     * Build incident description for ServiceNow
     */
    private String buildIncidentDescription(String environment, Map issueDetails) {
        def description = """
Deployment Issue Details:
- Application Name: ${pipeline.config.projectName}
- Environment: ${environment.toUpperCase()}
- Build Number: ${pipeline.script.env.BUILD_NUMBER}
- Issue Type: ${issueDetails.type ?: 'Unknown'}
- Error Message: ${issueDetails.error ?: 'No error message available'}
- Timestamp: ${new Date().format('yyyy-MM-dd HH:mm:ss')}

Additional Details:
${issueDetails.details ?: 'No additional details provided'}

Please investigate and resolve this deployment issue.
        """
        return description.trim()
    }

    /**
     * Make HTTP request to ServiceNow
     */
    private def makeServiceNowRequest(String method, String endpoint, Map payload = null) {
        def requestConfig = [
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            httpMode: method,
            url: "${baseUrl}${endpoint}",
            authentication: pipeline.script.usernamePassword(credentialsId: 'servicenow-credentials')
        ]
        
        if (payload) {
            requestConfig.requestBody = pipeline.script.writeJSON(returnText: true, json: payload)
        }
        
        def response = pipeline.script.httpRequest(requestConfig)
        return pipeline.script.readJSON(text: response.content)
    }
} 