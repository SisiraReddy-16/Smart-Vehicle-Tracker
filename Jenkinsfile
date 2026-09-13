pipeline {
    agent any

    tools {
        maven 'Maven3.9'
        jdk 'JDK17'
    }

    environment {
        AWS_REGION = 'ap-southeast-2'

        ECR_REPO = '600307629942.dkr.ecr.ap-southeast-2.amazonaws.com/smart-vehicile'

        IMAGE_NAME = 'smart-vehicle-tracker'
        IMAGE_TAG = 'latest'

        APP_EC2 = 'ubuntu@54.66.11.133'
    }

    stages {

        stage('Checkout') {
            steps {
                deleteDir()

                git branch: 'main',
                    url: 'https://github.com/SisiraReddy-16/Smart-Vehicle-Tracker.git'
            }
        }

        stage('Verify Source Code') {
            steps {
                sh '''
                    echo "===== Git Commit ====="
                    git log -1 --oneline

                    echo ""
                    echo "===== API_BASE ====="
                    grep -n "API_BASE" frontend/assets/api.js
                '''
            }
        }

        stage('Maven Build') {
            steps {
                dir('backend') {
                    sh 'mvn clean package'
                }
            }
        }

        stage('Verify WAR') {
            steps {
                sh '''
                    echo "===== WAR FILE ====="
                    ls -lh backend/target/

                    test -f backend/target/smart-vehicle-tracker.war
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build \
                        --no-cache \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        .
                '''
            }
        }

        stage('Verify Docker Image') {
            steps {
                sh '''
                    echo "===== Docker Image ====="
                    docker images ${IMAGE_NAME}:${IMAGE_TAG}

                    echo ""
                    echo "===== Verify API_BASE inside Docker image ====="

                    docker run --rm \
                        ${IMAGE_NAME}:${IMAGE_TAG} \
                        sh -c 'grep -Rni "API_BASE" /usr/local/tomcat/webapps/ROOT/assets/api.js'
                '''
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'awscreds']
                ]) {
                    sh '''
                        aws ecr get-login-password \
                            --region ${AWS_REGION} |
                        docker login \
                            --username AWS \
                            --password-stdin ${ECR_REPO}
                    '''
                }
            }
        }

        stage('Push Image to ECR') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'awscreds']
                ]) {
                    sh '''
                        echo "===== Tagging Image ====="

                        docker tag \
                            ${IMAGE_NAME}:${IMAGE_TAG} \
                            ${ECR_REPO}:${IMAGE_TAG}

                        echo "===== Pushing Image to ECR ====="

                        docker push \
                            ${ECR_REPO}:${IMAGE_TAG}
                    '''
                }
            }
        }

        stage('Deploy to Application EC2') {
            steps {
                sshagent(['app-ec2-ssh']) {

                    sh '''
                        ssh -o StrictHostKeyChecking=no ${APP_EC2} "
                            
                            echo '======================================'
                            echo 'Connecting to Application EC2'
                            echo '======================================'

                            echo 'Current server:'
                            hostname

                            echo ''
                            echo '======================================'
                            echo 'Logging into ECR'
                            echo '======================================'

                            aws ecr get-login-password \
                                --region ${AWS_REGION} |
                            docker login \
                                --username AWS \
                                --password-stdin ${ECR_REPO}

                            echo ''
                            echo '======================================'
                            echo 'Preparing Docker Network'
                            echo '======================================'

                            docker network inspect smartvehicle-net >/dev/null 2>&1 || \
                            docker network create smartvehicle-net

                            echo ''
                            echo '======================================'
                            echo 'Pulling Latest Image'
                            echo '======================================'

                            docker pull ${ECR_REPO}:${IMAGE_TAG}

                            echo ''
                            echo '======================================'
                            echo 'Stopping Old Application Container'
                            echo '======================================'

                            docker stop smartvehicle || true

                            echo ''
                            echo '======================================'
                            echo 'Removing Old Application Container'
                            echo '======================================'

                            docker rm smartvehicle || true

                            echo ''
                            echo '======================================'
                            echo 'Starting New Application Container'
                            echo '======================================'

                            docker run -d \
                                --name smartvehicle \
                                --network smartvehicle-net \
                                --restart unless-stopped \
                                -p 8080:8080 \
                                ${ECR_REPO}:${IMAGE_TAG}

                            echo ''
                            echo '======================================'
                            echo 'Connecting MySQL to Network'
                            echo '======================================'

                            docker network connect smartvehicle-net mysql 2>/dev/null || true

                            echo ''
                            echo '======================================'
                            echo 'Running Containers'
                            echo '======================================'

                            docker ps

                            echo ''
                            echo '======================================'
                            echo 'Docker Network'
                            echo '======================================'

                            docker network inspect smartvehicle-net

                            echo ''
                            echo '======================================'
                            echo 'Deployment Completed'
                            echo '======================================'
                        "
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                sshagent(['app-ec2-ssh']) {

                    sh '''
                        ssh -o StrictHostKeyChecking=no ${APP_EC2} "
                            
                            echo 'Checking application container...'
                            docker ps --filter name=smartvehicle

                            echo ''
                            echo 'Checking application locally...'
                            curl -I http://localhost:8080

                            echo ''
                            echo 'Checking API endpoint...'
                            curl -i http://localhost:8080/api/signup
                        "
                    '''
                }
            }
        }
    }

    post {

        success {
            echo '''
========================================
CI/CD PIPELINE SUCCESSFUL
========================================

GitHub
   ↓
Jenkins Checkout
   ↓
Maven Build
   ↓
Docker Build
   ↓
ECR Push
   ↓
Application EC2
   ↓
Docker Container
   ↓
MySQL

Deployment completed successfully.
'''
        }

        failure {
            echo '''
========================================
CI/CD PIPELINE FAILED
========================================

Check the failed stage in the Jenkins
console output.
'''
        }
    }
}
