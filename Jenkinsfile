pipeline {
    agent any

    tools {
        maven 'Maven3.9'
        jdk 'JDK17'
    }

    environment {
        AWS_REGION = 'ap-southeast-2'
        ECR_REPO = '600307629942.dkr.ecr.ap-southeast-2.amazonaws.com/smart-vehicile'
    }

    stages {

        stage('Maven Build') {
            steps {
                dir('backend') {
                    sh 'mvn clean package'
                }
            }
        }

        stage('Verify WAR') {
            steps {
                sh 'ls -lh backend/target/'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    docker build -t smart-vehicle-tracker:latest .
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
                        aws ecr get-login-password --region $AWS_REGION |
                        docker login --username AWS --password-stdin $ECR_REPO
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
                        docker tag smart-vehicle-tracker:latest $ECR_REPO:latest
                        docker push $ECR_REPO:latest
                    '''
                }
            }
        }

        stage('Test SSH to Application EC2') {
            steps {
                sshagent(['app-ec2-ssh']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no \
                        ubuntu@3.27.194.24 \
                        "echo SSH connection successful"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'CI + SSH test completed successfully.'
        }

        failure {
            echo 'Pipeline failed. Check the stage that reported the error.'
        }
    }
}
