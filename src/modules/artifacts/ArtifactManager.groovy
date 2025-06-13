package modules.artifacts

import modules.BasePipeline

/**
 * 制品管理模块，处理不同类型制品的推送和拉取
 */
class ArtifactManager {
    private BasePipeline pipeline
    private Map<String, String> repositories
    private String nexusUrl
    private String nexusUser
    private String nexusPassword

    /**
     * 初始化制品管理器
     * @param pipeline 基础流水线实例
     * @param config 制品仓库配置
     */
    ArtifactManager(BasePipeline pipeline, config = [:]) {
        this.pipeline = pipeline
        this.repositories = config.repositories ?: [:]
        this.nexusUrl = config.nexusUrl ?: pipeline.script.error("必须指定Nexus服务器URL")
        this.nexusUser = config.nexusUser ?: pipeline.script.error("必须指定Nexus用户名")
        this.nexusPassword = config.nexusPassword ?: pipeline.script.error("必须指定Nexus密码")
        pipeline.script.echo "初始化制品管理器，Nexus服务器: ${nexusUrl}"
    }

    /**
     * 推送Maven制品到Nexus
     * @param pomPath pom.xml文件路径
     * @param repoId Nexus仓库ID
     */
    void pushMavenArtifact(String pomPath = 'pom.xml', String repoId = 'maven-releases') {
        pipeline.script.stage("推送Maven制品到Nexus") {
            pipeline.script.echo "推送Maven制品到Nexus仓库: ${repoId}"

            def deployCommand = "mvn deploy -f ${pomPath} " +
                              "-DaltDeploymentRepository=${repoId}::default::${nexusUrl}/repository/${repoId}/ " +
                              "-Dusername=${nexusUser} -Dpassword=${nexusPassword}"

            pipeline.script.sh(deployCommand)
            pipeline.script.echo "Maven制品推送完成"
        }
    }

    /**
     * 推送Docker镜像到Nexus
     * @param imageName 镜像名称
     * @param imageTag 镜像标签
     * @param repoId Nexus仓库ID
     */
    void pushDockerImage(String imageName, String imageTag = 'latest', String repoId = 'docker-releases') {
        pipeline.script.stage("推送Docker镜像到Nexus") {
            pipeline.script.echo "推送Docker镜像到Nexus仓库: ${repoId}"

            // 登录到Nexus Docker仓库
            pipeline.script.sh "docker login ${nexusUrl} -u ${nexusUser} -p ${nexusPassword}"

            // 标记镜像
            def fullImageName = "${nexusUrl}/${repoId}/${imageName}:${imageTag}"
            pipeline.script.sh "docker tag ${imageName}:${imageTag} ${fullImageName}"

            // 推送镜像
            pipeline.script.sh "docker push ${fullImageName}"

            // 登出
            pipeline.script.sh "docker logout ${nexusUrl}"

            pipeline.script.echo "Docker镜像推送完成: ${fullImageName}"
        }
    }

    /**
     * 从Nexus拉取Docker镜像
     * @param imageName 镜像名称
     * @param imageTag 镜像标签
     * @param repoId Nexus仓库ID
     * @return 完整镜像名称
     */
    String pullDockerImage(String imageName, String imageTag = 'latest', String repoId = 'docker-releases') {
        pipeline.script.stage("从Nexus拉取Docker镜像") {
            pipeline.script.echo "从Nexus仓库拉取镜像: ${repoId}/${imageName}:${imageTag}"

            // 登录到Nexus Docker仓库
            pipeline.script.sh "docker login ${nexusUrl} -u ${nexusUser} -p ${nexusPassword}"

            // 拉取镜像
            def fullImageName = "${nexusUrl}/${repoId}/${imageName}:${imageTag}"
            pipeline.script.sh "docker pull ${fullImageName}"

            // 登出
            pipeline.script.sh "docker logout ${nexusUrl}"

            pipeline.script.echo "Docker镜像拉取完成: ${fullImageName}"
            return fullImageName
        }
    }

    /**
     * 从Nexus下载通用制品
     * @param artifactPath 制品在仓库中的路径
     * @param targetPath 本地目标路径
     * @param repoId Nexus仓库ID
     */
    void downloadGenericArtifact(String artifactPath, String targetPath, String repoId = 'generic-releases') {
        pipeline.script.stage("从Nexus下载通用制品") {
            pipeline.script.echo "从Nexus仓库下载制品: ${repoId}/${artifactPath}"

            def downloadUrl = "${nexusUrl}/repository/${repoId}/${artifactPath}"
            def auth = "${nexusUser}:${nexusPassword}"

            pipeline.script.sh "mkdir -p $(dirname ${targetPath})"
            pipeline.script.sh "curl -u ${auth} -O -L ${downloadUrl} -o ${targetPath}"

            pipeline.script.echo "通用制品下载完成: ${targetPath}"
        }
    }
}