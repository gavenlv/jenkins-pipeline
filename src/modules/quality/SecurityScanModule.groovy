package modules.quality

import modules.BasePipeline

/**
 * 安全扫描模块，提供依赖检查和容器镜像扫描功能
 */
class SecurityScanModule {
    private BasePipeline pipeline
    private String reportPath = 'security-reports'
    private String owaspCheckVersion = '7.4.4'
    private String trivyVersion = '0.44.1'

    /**
     * 初始化安全扫描模块
     * @param pipeline 基础流水线实例
     * @param config 扫描配置
     */
    SecurityScanModule(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        if (config.reportPath) this.reportPath = config.reportPath
        if (config.owaspCheckVersion) this.owaspCheckVersion = config.owaspCheckVersion
        if (config.trivyVersion) this.trivyVersion = config.trivyVersion
        pipeline.script.echo "初始化安全扫描模块，报告路径: ${reportPath}"
    }

    /**
     * 执行OWASP依赖检查
     * @param projectType 项目类型(maven/gradle/npm等)
     * @param path 项目路径
     */
    void runOwaspDependencyCheck(String projectType, String path = '.') {
        pipeline.script.stage("OWASP依赖检查") {
            pipeline.script.echo "执行OWASP依赖漏洞扫描..."
            pipeline.script.sh "mkdir -p ${reportPath}"

            def mountPath = pipeline.script.isUnix() ? "/src" : "C:/src"
            def reportFormat = "XML"
            def reportFile = "${reportPath}/owasp-dependency-check-report.xml"

            // 构建OWASP依赖检查命令
            def owaspCommand = "docker run --rm -v ${pipeline.script.pwd()}/${path}:${mountPath} -v ${pipeline.script.pwd()}/${reportPath}:/report owasp/dependency-check:${owaspCheckVersion} \"
            owaspCommand += "--project \"${pipeline.config.projectName}\" --scan ${mountPath} --format ${reportFormat} --out /report --failOnCVSS 7"

            pipeline.script.sh(owaspCommand)

            // 发布安全报告
            pipeline.script.publishHTML([
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: reportPath,
                reportFiles: 'owasp-dependency-check-report.html',
                reportName: 'OWASP Dependency Check Report'
            ])

            // 记录质量检查结果
            pipeline.recordQualityResult("OWASP Dependency Check", true, [status: "passed", reportPath: reportFile])
            pipeline.script.echo "OWASP依赖检查完成"
        }
    }

    /**
     * 执行容器镜像安全扫描
     * @param imageName 镜像名称
     * @param imageTag 镜像标签
     */
    void scanDockerImage(String imageName, String imageTag = 'latest') {
        pipeline.script.stage("容器镜像安全扫描") {
            pipeline.script.echo "执行Trivy容器镜像漏洞扫描..."
            pipeline.script.sh "mkdir -p ${reportPath}"

            def image = "${imageName}:${imageTag}"
            def reportFile = "${reportPath}/trivy-scan-report.json"

            // 安装Trivy(如果未安装)
            def trivyInstallCmd = pipeline.script.isUnix() ? 
                "curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin v${trivyVersion}" : 
                "choco install trivy --version=${trivyVersion} -y"

            pipeline.script.sh "which trivy || ${trivyInstallCmd}"

            // 执行Trivy扫描
            def trivyCommand = "trivy image --exit-code 1 --severity HIGH,CRITICAL --format json --output ${reportFile} ${image}"
            pipeline.script.sh(trivyCommand)

            // 记录质量检查结果
            pipeline.recordQualityResult("Docker Image Scan", true, [status: "passed", image: image, reportPath: reportFile])
            pipeline.script.echo "容器镜像安全扫描完成"
        }
    }

    /**
     * 执行代码秘钥泄露扫描
     * @param path 扫描路径
     */
    void runSecretScan(String path = '.') {
        pipeline.script.stage("代码秘钥泄露扫描") {
            pipeline.script.echo "执行TruffleHog秘钥泄露扫描..."
            pipeline.script.sh "mkdir -p ${reportPath}"

            def reportFile = "${reportPath}/trufflehog-report.json"

            // 安装TruffleHog(如果未安装)
            def trufflehogInstallCmd = pipeline.script.isUnix() ? 
                "go install github.com/trufflesecurity/trufflehog/v3/cmd/trufflehog@latest" : 
                "choco install trufflehog -y"

            pipeline.script.sh "which trufflehog || ${trufflehogInstallCmd}"

            // 执行TruffleHog扫描
            def trufflehogCommand = "trufflehog filesystem --no-update --json ${path} > ${reportFile}"
            pipeline.script.sh(trufflehogCommand)

            // 检查扫描结果
            pipeline.script.script {
                def scanResult = pipeline.script.readJSON file: reportFile
                if (scanResult.find { it.Verified }) {
                    pipeline.script.error "发现敏感信息泄露，请检查${reportFile}"
                }
            }

            // 记录质量检查结果
            pipeline.recordQualityResult("Secret Scan", true, [status: "passed", reportPath: reportFile])
            pipeline.script.echo "代码秘钥泄露扫描完成"
        }
    }
}