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
        APP_EC2 = 'ubuntu@3.107.202.91'
    }

    stages {

        /*
         * 1. BUILD JAVA APPLICATION
         */
        stage('Maven Build') {
            steps {
                dir('backend') {
                    sh 'mvn clean package'
                }
            }
        }

        /*
         * 2. VERIFY WAR FILE
         */
        stage('Verify WAR') {
            steps {
                sh 'ls -lh backend/target/'
            }
        }

        /*
         * 3. BUILD DOCKER IMAGE
         */
        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build \
                        -t ${IMAGE_NAME}:${IMAGE_TAG} \
                        .
                '''
            }
        }

        /*
         * 4. LOGIN TO AWS ECR
         */
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

        /*
         * 5. PUSH DOCKER IMAGE TO ECR
         */
        stage('Push Image to ECR') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'awscreds']
                ]) {
                    sh '''
                        docker tag \
                            ${IMAGE_NAME}:${IMAGE_TAG} \
                            ${ECR_REPO}:${IMAGE_TAG}

                        docker push \
                            ${ECR_REPO}:${IMAGE_TAG}
                    '''
                }
            }
        }

        /*
         * 6. DEPLOY TO APPLICATION EC2
         */
        stage('Deploy to Application EC2') {
            steps {
                sshagent(['app-ec2-ssh']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no ${APP_EC2} "
                            
                            echo 'Logging into ECR...'

                            aws ecr get-login-password \
                                --region ${AWS_REGION} |
                            docker login \
                                --username AWS \
                                --password-stdin ${ECR_REPO}

                            echo 'Pulling latest Docker image...'

                            docker pull ${ECR_REPO}:${IMAGE_TAG}

                            echo 'Stopping old application container...'

                            docker stop smartvehicle || true

                            echo 'Removing old application container...'

                            docker rm smartvehicle || true

                            echo 'Starting new application container...'

                            docker run -d \
                                --name smartvehicle \
                                -p 8080:8080 \
                                ${ECR_REPO}:${IMAGE_TAG}

                            echo 'Checking running containers...'

                            docker ps

                            echo 'Deployment completed successfully.'
                        "
                    '''
                }
            }
        }
    }

    post {

        success {
            echo 'CI/CD pipeline completed successfully!'
            echo 'Maven → Docker → ECR → Application EC2 deployment completed.'
        }

        failure {
            echo 'CI/CD pipeline failed. Check the failed stage in the console output.'
        }
    }
}
