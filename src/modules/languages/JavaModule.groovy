package modules.languages

import modules.BasePipeline

/**
 * Java语言专用CI模块，提供构建、测试和质量检查功能
 */
class JavaModule {
    private BasePipeline pipeline
    private String buildTool = 'maven' // 默认构建工具为Maven
    private String testReportPath = 'target/surefire-reports'
    private String coverageReportPath = 'target/jacoco'

    /**
     * 初始化Java模块
     * @param pipeline 基础流水线实例
     * @param config 语言特定配置
     */
    JavaModule(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        if (config.buildTool) this.buildTool = config.buildTool
        if (config.testReportPath) this.testReportPath = config.testReportPath
        if (config.coverageReportPath) this.coverageReportPath = config.coverageReportPath
        pipeline.script.echo "初始化Java模块，构建工具: ${buildTool}"
    }

    /**
     * 构建Java项目
     * @param args 构建参数
     */
    void build(String args = '') {
        pipeline.script.stage("Java构建") {
            pipeline.script.echo "使用${buildTool}构建Java项目..."
            def buildCommand = buildTool == 'maven' ? "./mvnw clean package ${args}" : "./gradlew clean build ${args}"
            pipeline.script.sh(buildCommand)
            pipeline.script.echo "Java项目构建完成"
        }
    }

    /**
     * 运行单元测试
     * @param args 测试参数
     */
    void runUnitTests(String args = '') {
        pipeline.script.stage("Java单元测试") {
            pipeline.script.echo "运行Java单元测试..."
            def testCommand = buildTool == 'maven' ? "./mvnw test ${args}" : "./gradlew test ${args}"
            pipeline.script.sh(testCommand)

            // 发布测试报告
            pipeline.script.junit "${testReportPath}/**/*.xml"
            pipeline.script.echo "Java单元测试完成，报告已生成"
        }
    }

    /**
     * 运行集成测试
     * @param args 测试参数
     */
    void runIntegrationTests(String args = '') {
        pipeline.script.stage("Java集成测试") {
            pipeline.script.echo "运行Java集成测试..."
            def testCommand = buildTool == 'maven' ? "./mvnw verify ${args}" : "./gradlew integrationTest ${args}"
            pipeline.script.sh(testCommand)

            // 发布集成测试报告
            pipeline.script.junit "${testReportPath}/**/*.xml"
            pipeline.script.echo "Java集成测试完成，报告已生成"
        }
    }

    /**
     * 执行SonarQube代码质量扫描
     * @param projectKey SonarQube项目键
     * @param args 扫描参数
     */
    void runSonarQubeScan(String projectKey, String args = '') {
        pipeline.script.stage("SonarQube扫描") {
            pipeline.script.echo "执行SonarQube代码质量扫描..."
            def sonarCommand = buildTool == 'maven' ? "./mvnw sonar:sonar -Dsonar.projectKey=${projectKey} ${args}" : "./gradlew sonarqube -Dsonar.projectKey=${projectKey} ${args}"
            pipeline.script.sh(sonarCommand)

            // 记录质量检查结果（实际实现中可解析SonarQube API获取详细指标）
            pipeline.recordQualityResult("SonarQube", true, [status: "passed", projectKey: projectKey])
            pipeline.script.echo "SonarQube扫描完成"
        }
    }

    /**
     * 生成代码覆盖率报告
     */
    void generateCoverageReport() {
        pipeline.script.stage("代码覆盖率报告") {
            pipeline.script.echo "生成代码覆盖率报告..."
            def coverageCommand = buildTool == 'maven' ? "./mvnw jacoco:report" : "./gradlew jacocoTestReport"
            pipeline.script.sh(coverageCommand)

            // 发布覆盖率报告（可集成到Jenkins或其他工具）
            pipeline.script.publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: coverageReportPath,
                reportFiles: 'index.html',
                reportName: 'Java Code Coverage Report'
            ])
            pipeline.script.echo "代码覆盖率报告生成完成"
        }
    }
}