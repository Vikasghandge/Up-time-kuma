pipeline {
    agent any

    tools {
        jdk 'jdk17'
        nodejs 'node19'
    }

    environment {
        SCANNER_HOME = tool 'sonar-scanner'
    }

    stages {
        stage('Checkout from Git') {
            steps {
                git branch: 'main', url: 'https://github.com/Vikasghandge/Up-time-kuma.git'
            }
        }

        stage('Install Dependencies') {
            steps {
                dir('Uptime-kuma-main') {
                    sh 'npm install'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh '''
                        ${SCANNER_HOME}/bin/sonar-scanner \
                        -Dsonar.projectName=uptime \
                        -Dsonar.projectKey=uptime
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-token'
                }
            }
        }

        stage('Trivy Filesystem Scan') {
            steps {
                dir('Uptime-kuma-main') {
                    sh 'trivy fs . > trivyfs.json'
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                dir('Uptime-kuma-main') {
                    script {
                        withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                            sh 'docker build -t uptime .'
                            sh 'docker tag uptime ghandgevikas/uptime:latest'
                            sh 'docker push ghandgevikas/uptime:latest'
                        }
                    }
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh 'trivy image ghandgevikas/uptime:latest > trivy.json'
            }
        }

        stage('Remove Container') {
            steps {
                sh 'docker stop uptime || true'
                sh 'docker rm uptime || true'
            }
        }

        stage('Deploy to Container') {
            steps {
                sh 'docker run -d --name uptime -v /var/run/docker.sock:/var/run/docker.sock -p 3001:3001 ghandgevikas/uptime:latest'
            }
        }
    }
}
