package modules.deployment

import modules.BasePipeline

/**
 * Helm部署模块，提供基于Helm Chart的Kubernetes部署管理功能
 */
class HelmDeploymentModule {
    private BasePipeline pipeline
    private String chartPath
    private String releaseName
    private String namespace = 'default'
    private Map<String, String> valuesFiles = [:]
    private boolean waitForDeployment = true
    private int timeoutSeconds = 300

    /**
     * 初始化Helm部署模块
     * @param pipeline 基础流水线实例
     * @param config 部署配置
     */
    HelmDeploymentModule(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        this.chartPath = config.chartPath ?: pipeline.script.error("必须指定Helm Chart路径")
        this.releaseName = config.releaseName ?: "${pipeline.config.projectName}-${pipeline.currentEnvironment}"
        this.namespace = config.namespace ?: namespace
        this.valuesFiles = config.valuesFiles ?: [:]
        this.waitForDeployment = config.waitForDeployment ?: waitForDeployment
        this.timeoutSeconds = config.timeoutSeconds ?: timeoutSeconds
        pipeline.script.echo "初始化Helm部署模块，发布名称: ${releaseName}, 命名空间: ${namespace}"
    }

    /**
     * 添加环境特定的values文件
     * @param environment 环境名称
     * @param filePath values文件路径
     */
    void addValuesFile(String environment, String filePath) {
        valuesFiles[environment] = filePath
        pipeline.script.echo "添加${environment}环境values文件: ${filePath}"
    }

    /**
     * 执行Helm部署
     * @param environment 目标环境
     * @param extraArgs 额外Helm参数
     */
    void deploy(String environment, String extraArgs = '') {
        pipeline.script.stage("Helm部署到${environment}") {
            pipeline.script.echo "开始使用Helm部署到${environment}环境..."

            // 检查Kubernetes集群连接
            pipeline.script.sh "kubectl config current-context"
            pipeline.script.sh "kubectl get namespace ${namespace} || kubectl create namespace ${namespace}"

            // 构建Helm命令
            def valuesArgs = ""
            if (valuesFiles[environment]) {
                valuesArgs = "-f ${valuesFiles[environment]}"
            }
            if (valuesFiles['common']) {
                valuesArgs += " -f ${valuesFiles['common']}"
            }

            def helmCommand = "helm upgrade --install ${releaseName} ${chartPath} ${valuesArgs} --namespace ${namespace} --set environment=${environment}"
            if (waitForDeployment) {
                helmCommand += " --wait --timeout ${timeoutSeconds}s"
            }
            if (extraArgs) {
                helmCommand += " ${extraArgs}"
            }

            pipeline.script.echo "执行Helm命令: ${helmCommand}"
            pipeline.script.sh(helmCommand)

            // 验证部署状态
            if (waitForDeployment) {
                verifyDeployment(environment)
            }
            pipeline.script.echo "Helm部署到${environment}环境成功"
        }
    }

    /**
     * 验证部署状态
     * @param environment 环境名称
     */
    private void verifyDeployment(String environment) {
        pipeline.script.echo "验证${environment}环境部署状态..."
        // 检查所有Pod是否就绪
        pipeline.script.sh "kubectl rollout status deployment/${releaseName} --namespace ${namespace} --timeout ${timeoutSeconds}s"
        // 检查服务是否可用
        pipeline.script.sh "kubectl get svc ${releaseName} --namespace ${namespace}"
        pipeline.script.echo "${environment}环境部署验证通过"
    }

    /**
     * 回滚到上一版本
     */
    void rollback() {
        pipeline.script.stage("Helm回滚部署") {
            pipeline.script.echo "回滚Helm发布: ${releaseName}"
            pipeline.script.sh "helm rollback ${releaseName} 1 --namespace ${namespace}"
            if (waitForDeployment) {
                pipeline.script.sh "kubectl rollout status deployment/${releaseName} --namespace ${namespace} --timeout ${timeoutSeconds}s"
            }
            pipeline.script.echo "Helm发布回滚成功"
        }
    }

    /**
     * 卸载Helm发布
     */
    void uninstall() {
        pipeline.script.stage("卸载Helm发布") {
            pipeline.script.echo "卸载Helm发布: ${releaseName}"
            pipeline.script.sh "helm uninstall ${releaseName} --namespace ${namespace}"
            pipeline.script.echo "Helm发布卸载成功"
        }
    }
}