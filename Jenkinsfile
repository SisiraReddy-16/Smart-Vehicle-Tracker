pipeline {
    agent any

    tools {
        maven 'Maven3.9'
        jdk 'JDK17'
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
                sh 'docker build -t smart-vehicle-tracker:1.0.0 .'
            }
        }
    }

    post {
        success {
            echo 'Build successful: WAR created and Docker image built.'
        }

        failure {
            echo 'Pipeline failed. Check the stage that reported the error.'
        }
    }
}