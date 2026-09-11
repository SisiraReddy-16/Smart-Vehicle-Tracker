pipeline {
    agent any

    tools {
        maven 'Maven'
        jdk 'JDK8'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Maven Build') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t smart-vehicle-tracker:1.0.0 .'
            }
        }
    }

    post {
        success {
            echo 'Build and Docker image creation completed successfully!'
        }

        failure {
            echo 'Pipeline failed. Check the Jenkins console output.'
        }
    }
}