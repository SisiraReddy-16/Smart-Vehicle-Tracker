pipeline {

    agent any

    tools {
        maven 'Maven3.9'
        jdk 'JDK17'
    }

    environment {

        // ==============================
        // AWS CONFIGURATION
        // ==============================

        AWS_REGION = 'ap-southeast-2'

        ECR_REGISTRY = '600307629942.dkr.ecr.ap-southeast-2.amazonaws.com'

        ECR_REPO = '600307629942.dkr.ecr.ap-southeast-2.amazonaws.com/smart-vehicle'

        // ==============================
        // DOCKER CONFIGURATION
        // ==============================

        IMAGE_NAME = 'smart-vehicle-tracker'

        IMAGE_TAG = 'latest'

        // ==============================
        // APPLICATION EC2
        // ==============================

        APP_EC2 = 'ubuntu@54.66.11.133'

        // ==============================
        // DOCKER CONTAINER
        // ==============================

        CONTAINER_NAME = 'smartvehicle'

        DOCKER_NETWORK = 'smartvehicle-net'
    }


    stages {


        // ============================================================
        // 1. CHECKOUT
        // ============================================================

        stage('Checkout') {

            steps {

                deleteDir()

                git branch: 'main',
                    url: 'https://github.com/SisiraReddy-16/Smart-Vehicle-Tracker.git'
            }
        }


        // ============================================================
        // 2. VERIFY SOURCE CODE
        // ============================================================

        stage('Verify Source Code') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFYING SOURCE CODE"
                    echo "======================================"

                    echo ""
                    echo "===== Git Commit ====="

                    git log -1 --oneline

                    echo ""
                    echo "===== Repository Files ====="

                    ls -la

                    echo ""
                    echo "===== Backend ====="

                    ls -la backend

                    echo ""
                    echo "===== Frontend ====="

                    ls -la frontend

                    echo ""
                    echo "===== API_BASE ====="

                    grep -n "API_BASE" frontend/assets/api.js
                '''
            }
        }


        // ============================================================
        // 3. MAVEN BUILD
        // ============================================================

        stage('Maven Build') {

            steps {

                dir('backend') {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "MAVEN BUILD"
                        echo "======================================"

                        mvn clean package
                    '''
                }
            }
        }


        // ============================================================
        // 4. VERIFY WAR
        // ============================================================

        stage('Verify WAR') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFYING WAR FILE"
                    echo "======================================"

                    echo ""
                    echo "===== Backend Target Directory ====="

                    ls -lh backend/target/

                    echo ""
                    echo "===== Checking WAR ====="

                    test -f backend/target/smart-vehicle-tracker.war

                    echo ""
                    echo "WAR file found successfully."
                '''
            }
        }


        // ============================================================
        // 5. BUILD DOCKER IMAGE
        // ============================================================

        stage('Build Docker Image') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "BUILDING DOCKER IMAGE"
                    echo "======================================"

                    docker build \
                        --no-cache \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        .
                '''
            }
        }


        // ============================================================
        // 6. VERIFY DOCKER IMAGE
        // ============================================================

        stage('Verify Docker Image') {

            steps {

                sh '''
                    set -e

                    echo "======================================"
                    echo "VERIFYING DOCKER IMAGE"
                    echo "======================================"

                    echo ""
                    echo "===== Docker Image ====="

                    docker images ${IMAGE_NAME}:${IMAGE_TAG}

                    echo ""
                    echo "===== Docker Image Details ====="

                    docker inspect ${IMAGE_NAME}:${IMAGE_TAG} > /dev/null

                    echo ""
                    echo "===== Verify API_BASE inside Docker Image ====="

                    docker run --rm \
                        ${IMAGE_NAME}:${IMAGE_TAG} \
                        sh -c 'grep -Rni "API_BASE" /usr/local/tomcat/webapps/ROOT/assets/api.js'

                    echo ""
                    echo "Docker image verification successful."
                '''
            }
        }


        // ============================================================
        // 7. LOGIN TO ECR
        // ============================================================

        stage('Login to ECR') {

            steps {

                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'awscreds']
                ]) {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "LOGIN TO AMAZON ECR"
                        echo "======================================"

                        aws sts get-caller-identity

                        aws ecr get-login-password \
                            --region ${AWS_REGION} |
                        docker login \
                            --username AWS \
                            --password-stdin ${ECR_REGISTRY}

                        echo ""
                        echo "ECR login successful."
                    '''
                }
            }
        }


        // ============================================================
        // 8. PUSH IMAGE TO ECR
        // ============================================================

        stage('Push Image to ECR') {

            steps {

                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'awscreds']
                ]) {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "PUSHING IMAGE TO AMAZON ECR"
                        echo "======================================"

                        echo ""
                        echo "===== Tagging Image ====="

                        docker tag \
                            ${IMAGE_NAME}:${IMAGE_TAG} \
                            ${ECR_REPO}:${IMAGE_TAG}

                        echo ""
                        echo "===== Pushing Image ====="

                        docker push \
                            ${ECR_REPO}:${IMAGE_TAG}

                        echo ""
                        echo "Image pushed successfully to ECR."
                    '''
                }
            }
        }


        // ============================================================
        // 9. DEPLOY TO APPLICATION EC2
        // ============================================================

        stage('Deploy to Application EC2') {

            steps {

                sshagent(['app-ec2-ssh']) {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "DEPLOYING TO APPLICATION EC2"
                        echo "======================================"

                        ssh -o StrictHostKeyChecking=no ${APP_EC2} "
                            
                            set -e

                            echo '======================================'
                            echo 'CONNECTING TO APPLICATION EC2'
                            echo '======================================'

                            echo ''
                            echo 'Current server:'
                            hostname

                            echo ''
                            echo '======================================'
                            echo 'AWS IDENTITY'
                            echo '======================================'

                            aws sts get-caller-identity

                            echo ''
                            echo '======================================'
                            echo 'LOGIN TO ECR'
                            echo '======================================'

                            aws ecr get-login-password \
                                --region ${AWS_REGION} |
                            docker login \
                                --username AWS \
                                --password-stdin ${ECR_REGISTRY}

                            echo ''
                            echo '======================================'
                            echo 'PREPARING DOCKER NETWORK'
                            echo '======================================'

                            docker network inspect ${DOCKER_NETWORK} >/dev/null 2>&1 || \
                            docker network create ${DOCKER_NETWORK}

                            echo ''
                            echo '======================================'
                            echo 'PULLING LATEST IMAGE'
                            echo '======================================'

                            docker pull ${ECR_REPO}:${IMAGE_TAG}

                            echo ''
                            echo '======================================'
                            echo 'STOPPING OLD APPLICATION CONTAINER'
                            echo '======================================'

                            docker stop ${CONTAINER_NAME} || true

                            echo ''
                            echo '======================================'
                            echo 'REMOVING OLD APPLICATION CONTAINER'
                            echo '======================================'

                            docker rm ${CONTAINER_NAME} || true

                            echo ''
                            echo '======================================'
                            echo 'STARTING NEW APPLICATION CONTAINER'
                            echo '======================================'

                            docker run -d \
                                --name ${CONTAINER_NAME} \
                                --network ${DOCKER_NETWORK} \
                                --restart unless-stopped \
                                -p 8080:8080 \
                                ${ECR_REPO}:${IMAGE_TAG}

                            echo ''
                            echo '======================================'
                            echo 'CONNECTING MYSQL TO NETWORK'
                            echo '======================================'

                            docker network connect ${DOCKER_NETWORK} mysql 2>/dev/null || true

                            echo ''
                            echo '======================================'
                            echo 'RUNNING CONTAINERS'
                            echo '======================================'

                            docker ps

                            echo ''
                            echo '======================================'
                            echo 'APPLICATION CONTAINER LOGS'
                            echo '======================================'

                            docker logs --tail 30 ${CONTAINER_NAME}

                            echo ''
                            echo '======================================'
                            echo 'DEPLOYMENT COMPLETED'
                            echo '======================================'
                        "
                    '''
                }
            }
        }


        // ============================================================
        // 10. VERIFY DEPLOYMENT
        // ============================================================

        stage('Verify Deployment') {

            steps {

                sshagent(['app-ec2-ssh']) {

                    sh '''
                        set -e

                        echo "======================================"
                        echo "VERIFYING DEPLOYMENT"
                        echo "======================================"

                        ssh -o StrictHostKeyChecking=no ${APP_EC2} "

                            set -e

                            echo '======================================'
                            echo 'CHECKING APPLICATION CONTAINER'
                            echo '======================================'

                            docker ps --filter name=${CONTAINER_NAME}

                            echo ''
                            echo '======================================'
                            echo 'WAITING FOR APPLICATION'
                            echo '======================================'

                            for i in 1 2 3 4 5 6 7 8 9 10
                            do

                                echo 'Attempt '\$i'/10...'

                                if curl -fsS http://localhost:8080 > /dev/null
                                then

                                    echo 'Application is responding!'
                                    break

                                fi

                                if [ \$i -eq 10 ]
                                then

                                    echo 'Application did not become ready.'

                                    echo ''
                                    echo '========== CONTAINER STATUS =========='

                                    docker ps -a --filter name=${CONTAINER_NAME}

                                    echo ''
                                    echo '========== CONTAINER LOGS =========='

                                    docker logs --tail 100 ${CONTAINER_NAME}

                                    exit 1
                                fi

                                sleep 3

                            done

                            echo ''
                            echo '======================================'
                            echo 'APPLICATION HTTP CHECK'
                            echo '======================================'

                            curl -fsS -I http://localhost:8080

                            echo ''
                            echo '======================================'
                            echo 'CONTAINER STATUS'
                            echo '======================================'

                            docker ps --filter name=${CONTAINER_NAME}

                            echo ''
                            echo '======================================'
                            echo 'DEPLOYMENT VERIFICATION SUCCESSFUL'
                            echo '======================================'
                        "
                    '''
                }
            }
        }


        // ============================================================
        // 11. CLEAN OLD LOCAL JENKINS DOCKER IMAGES
        // ============================================================

        stage('Docker Cleanup') {

            steps {

                sh '''
                    echo "======================================"
                    echo "DOCKER CLEANUP"
                    echo "======================================"

                    docker image prune -f || true

                    echo ""
                    echo "Docker cleanup completed."
                '''
            }
        }
    }


    // ================================================================
    // POST ACTIONS
    // ================================================================

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
WAR Verification
   ↓
Docker Build
   ↓
Docker Verification
   ↓
Amazon ECR
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

One of the pipeline stages failed.

Check the Jenkins Console Output
to identify the failed stage.
'''
        }
    }
}