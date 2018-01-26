package org.stan

import hudson.model.Result
import jenkins.model.CauseOfInterruption.UserInterruption

def killOldBuilds() {
  def hi = Hudson.instance
  def pname = env.JOB_NAME.split('/')[0]

  hi.getItem(pname).getItem(env.JOB_BASE_NAME).getBuilds().each{ build ->
    def exec = build.getExecutor()

    if (build.number != currentBuild.number && exec != null) {
      exec.interrupt(
        Result.ABORTED,
        new CauseOfInterruption.UserInterruption(
          "job #${currentBuild.number} supersedes this build"
        )
      )
      println("Aborted previous running build #${build.number}")
    }
  }
}

def isBranch(env, String b) { env.BRANCH_NAME == b }

def updateUpstream(env, String upstreamRepo) {
    if (isBranch(env, 'develop')) {
        node('master') {
            retry(3) {
                checkout([$class: 'GitSCM',
                        branches: [[name: '*/develop']],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [[$class: 'SubmoduleOption',
                                    disableSubmodules: false,
                                    parentCredentials: false,
                                    recursiveSubmodules: true,
                                    reference: '',
                                    trackingSubmodules: false]],
                        submoduleCfg: [],
                        userRemoteConfigs: [[url: "git@github.com:stan-dev/${upstreamRepo}.git",
                                           credentialsId: 'a630aebc-6861-4e69-b497-fd7f496ec46b'
                ]]])
            }
            sh """
                curl -O https://raw.githubusercontent.com/stan-dev/ci-scripts/master/jenkins/create-${upstreamRepo}-pull-request.sh
                sh create-${upstreamRepo}-pull-request.sh
            """
            retry(3) { deleteDir() }
        }
    }
}
