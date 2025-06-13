package tests.unit

import spock.lang.Specification
import spock.lang.Unroll
import modules.BasePipeline

/**
 * Unit tests for BasePipeline module
 */
class BasePipelineTest extends Specification {
    
    def mockScript
    def pipeline
    def testConfig
    
    def setup() {
        // Mock Jenkins script context
        mockScript = Mock()
        mockScript.env >> [
            BUILD_NUMBER: '123',
            BUILD_ID: 'test-build-id',
            JOB_NAME: 'test-job',
            GIT_BRANCH: 'main',
            GIT_COMMIT: 'abc123',
            BUILD_USER: 'test-user'
        ]
        
        // Test configuration
        testConfig = [
            projectName: 'test-project',
            environments: [
                [name: 'dev', autoPromote: true],
                [name: 'prod', requiresApproval: true]
            ],
            qualityGateEnabled: true
        ]
        
        pipeline = new BasePipeline(mockScript, testConfig)
    }
    
    def "should initialize pipeline with correct configuration"() {
        expect:
        pipeline.config == testConfig
        pipeline.script == mockScript
        pipeline.buildMetrics.buildNumber == '123'
        pipeline.buildMetrics.projectName == 'test-project'
    }
    
    def "should initialize build metrics correctly"() {
        expect:
        pipeline.buildMetrics.buildNumber == '123'
        pipeline.buildMetrics.buildId == 'test-build-id'
        pipeline.buildMetrics.jobName == 'test-job'
        pipeline.buildMetrics.gitBranch == 'main'
        pipeline.buildMetrics.gitCommit == 'abc123'
        pipeline.buildMetrics.status == 'RUNNING'
    }
    
    def "should add stages correctly"() {
        given:
        def stageName = 'Test Stage'
        def stageAction = { -> println 'test action' }
        
        when:
        pipeline.addStage(stageName, stageAction)
        
        then:
        pipeline.stages.size() == 1
        pipeline.stages[0].name == stageName
        pipeline.stages[0].action == stageAction
        1 * mockScript.echo("Added stage: ${stageName}")
    }
    
    def "should add parallel stages correctly"() {
        given:
        def stageName = 'Parallel Test Stage'
        def stageAction = { -> println 'parallel test action' }
        
        when:
        pipeline.addParallelStage(stageName, stageAction)
        
        then:
        pipeline.parallelStages.size() == 1
        pipeline.parallelStages[stageName] == stageAction
        1 * mockScript.echo("Added parallel stage: ${stageName}")
    }
    
    def "should record build metrics"() {
        given:
        def metricKey = 'testMetric'
        def metricValue = 'testValue'
        
        when:
        pipeline.recordBuildMetric(metricKey, metricValue)
        
        then:
        pipeline.buildMetrics[metricKey] == metricValue
        1 * mockScript.echo("Recorded build metric: ${metricKey} = ${metricValue}")
    }
    
    def "should record test results"() {
        given:
        def testType = 'unit-tests'
        def results = [passed: 10, failed: 2, total: 12]
        
        when:
        pipeline.recordTestResults(testType, results)
        
        then:
        pipeline.testResults[testType] == results
        1 * mockScript.echo("Recorded test results: ${testType} - passed: 10, failed: 2")
    }
    
    def "should record security scan results"() {
        given:
        def scanType = 'owasp-check'
        def results = [status: 'PASSED', vulnerabilities: 0]
        
        when:
        pipeline.recordSecurityScanResults(scanType, results)
        
        then:
        pipeline.securityScanResults[scanType] == results
        1 * mockScript.echo("Recorded security scan results: ${scanType} - PASSED")
    }
    
    def "should register artifacts"() {
        given:
        def artifactType = 'docker'
        def artifactDetails = [name: 'test-app', version: '1.0.0']
        
        when:
        pipeline.registerArtifact(artifactType, artifactDetails)
        
        then:
        pipeline.artifactRegistry[artifactType].size() == 1
        pipeline.artifactRegistry[artifactType][0] == artifactDetails
        1 * mockScript.echo("Registered artifact: ${artifactType} - test-app:1.0.0")
    }
    
    def "should record deployment history"() {
        given:
        def environment = 'dev'
        def deploymentDetails = [status: 'SUCCESS', strategy: 'rolling-update']
        
        when:
        pipeline.recordDeployment(environment, deploymentDetails)
        
        then:
        pipeline.deploymentHistory.size() == 1
        pipeline.deploymentHistory[0].environment == environment
        pipeline.deploymentHistory[0].details == deploymentDetails
        1 * mockScript.echo("Recorded deployment history: ${environment} - SUCCESS")
    }
    
    def "should record quality results"() {
        given:
        def checkName = 'SonarQube'
        def passed = true
        def result = [status: 'PASSED', projectKey: 'test-project']
        
        when:
        pipeline.recordQualityResult(checkName, passed, result)
        
        then:
        pipeline.qualityGateResults[checkName].passed == passed
        pipeline.qualityGateResults[checkName].result == result
    }
    
    @Unroll
    def "should generate pipeline report with all data"() {
        given:
        pipeline.recordTestResults('unit-tests', [passed: 10, failed: 0])
        pipeline.recordSecurityScanResults('owasp', [status: 'PASSED'])
        pipeline.registerArtifact('docker', [name: 'test-app', version: '1.0.0'])
        pipeline.recordDeployment('dev', [status: 'SUCCESS'])
        
        when:
        def report = pipeline.generatePipelineReport()
        
        then:
        report.buildMetrics != null
        report.testResults.size() == 1
        report.securityScanResults.size() == 1
        report.artifactRegistry.size() == 1
        report.deploymentHistory.size() == 1
        report.generatedAt != null
    }
    
    def "should save pipeline report to file"() {
        given:
        def reportPath = 'test-report.json'
        mockScript.writeJSON(_ as Map) >> null
        mockScript.archiveArtifacts(_ as Map) >> null
        
        when:
        pipeline.savePipelineReport(reportPath)
        
        then:
        1 * mockScript.writeJSON([file: reportPath, json: _, pretty: 4])
        1 * mockScript.archiveArtifacts([artifacts: reportPath, allowEmptyArchive: true])
        1 * mockScript.echo("Pipeline report saved to: ${reportPath}")
    }
    
    def "should execute stages in correct order"() {
        given:
        def stage1Called = false
        def stage2Called = false
        def parallelStageCalled = false
        
        pipeline.addStage('Stage 1') { stage1Called = true }
        pipeline.addStage('Stage 2') { stage2Called = true }
        pipeline.addParallelStage('Parallel Stage') { parallelStageCalled = true }
        
        mockScript.stage(_ as String, _ as Closure) >> { name, closure -> closure.call() }
        mockScript.parallel(_ as Map) >> { stages -> stages.each { k, v -> v.call() } }
        
        when:
        pipeline.execute()
        
        then:
        stage1Called
        stage2Called
        parallelStageCalled
        1 * mockScript.echo("Starting pipeline execution, 2 serial stages and 1 parallel stages")
    }
    
    def "should handle quality gate validation"() {
        given:
        pipeline.recordQualityResult('test1', true, [status: 'PASSED'])
        pipeline.recordQualityResult('test2', false, [status: 'FAILED'])
        
        mockScript.stage(_ as String, _ as Closure) >> { name, closure -> closure.call() }
        
        when:
        pipeline.executeQualityGate()
        
        then:
        1 * mockScript.error("Quality gate check failed, please review reports and fix issues")
    }
    
    def "should pass quality gate when all checks pass"() {
        given:
        pipeline.recordQualityResult('test1', true, [status: 'PASSED'])
        pipeline.recordQualityResult('test2', true, [status: 'PASSED'])
        
        mockScript.stage(_ as String, _ as Closure) >> { name, closure -> closure.call() }
        
        when:
        pipeline.executeQualityGate()
        
        then:
        1 * mockScript.echo("Quality gate check passed")
        0 * mockScript.error(_)
    }
    
    def "should handle empty configuration"() {
        when:
        def emptyPipeline = new BasePipeline(mockScript, [:])
        
        then:
        emptyPipeline.config == [:]
        emptyPipeline.buildMetrics.buildNumber == '123'
    }
    
    def "should handle null configuration"() {
        when:
        def nullConfigPipeline = new BasePipeline(mockScript, null)
        
        then:
        nullConfigPipeline.config == [:]
        nullConfigPipeline.buildMetrics.buildNumber == '123'
    }
} 