package org.stan

import hudson.model.Result
import jenkins.model.CauseOfInterruption.UserInterruption

def killOldBuilds() {
  def hi = Hudson.instance
  def rootProject = env.JOB_NAME.split('/')[0]
  def secondaryProject = env.JOB_NAME.split('/')[1]
  def targetBranchOrPR = env.CHANGE_ID?.trim() ? "PR-" + env.CHANGE_ID : env.BRANCH_NAME

  hi.getItem(rootProject).getItem(secondaryProject).getItem(targetBranchOrPR).getBuilds().each{ build ->
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
        node('v100 && mesa') {
            retry(3) {
                deleteDir()
                withCredentials([
                    usernamePassword(
                        credentialsId: 'a630aebc-6861-4e69-b497-fd7f496ec46b',
                        usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                    sh "git clone --recursive https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/stan-dev/${upstreamRepo}.git"
                }
                sh """
                cd ${upstreamRepo}
                git config user.email "mc.stanislaw@gmail.com"
                git config user.name "Stan Jenkins"
                curl -O https://raw.githubusercontent.com/stan-dev/ci-scripts/master/jenkins/create-${upstreamRepo}-pull-request.sh
                bash create-${upstreamRepo}-pull-request.sh
            """
                deleteDir() // don't leave credentials on disk
            }
        }
    }
}

def isBuildAReplay() {
  def replyClassName = "org.jenkinsci.plugins.workflow.cps.replay.ReplayCause"
  currentBuild.rawBuild.getCauses().any{ cause -> cause.toString().contains(replyClassName) }
}

def verifyChanges(String sourceCodePaths, String mergeWith = "develop") {

    sh """
        git config user.email "mc.stanislaw@gmail.com"
        git config user.name "Stan Jenkins"
    """

    def commitHash = ""
    def changeTarget = ""
    def currentRepository = ""
    def mergeStatus = -1

    if (env.GIT_URL) {
        currentRepository = sh(script: "echo ${env.GIT_URL} | cut -d'/' -f 5", returnStdout: true)
    }
    else{
        currentRepository = sh(script: "echo ${env.CHANGE_URL} | cut -d'/' -f 5", returnStdout: true)
    }

    sh(script: "git config remote.origin.fetch '+refs/heads/*:refs/remotes/origin/*' --replace-all", returnStdout: true)
    sh(script: "git remote rm forkedOrigin || true", returnStdout: true)
    sh(script: "git fetch --all || true", returnStdout: true)
    sh(script: "git pull --all || true", returnStdout: true)

    if (env.CHANGE_TARGET) {
        println "This build is a PR, checking out target branch to compare changes."
        changeTarget = env.CHANGE_TARGET

        if (env.CHANGE_FORK) {
            println "This PR is a fork."

            // Content of CHANGE_FORK varies, see https://issues.jenkins-ci.org/browse/JENKINS-58450.
            forkedRepository = env.CHANGE_FORK.matches('.*/.*') ?
                    env.CHANGE_FORK :
                    env.CHANGE_FORK + "/${currentRepository}"

            withCredentials([usernamePassword(credentialsId: 'a630aebc-6861-4e69-b497-fd7f496ec46b', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                sh """#!/bin/bash
                   git remote add forkedOrigin https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/${forkedRepository}
                   git fetch forkedOrigin
                   git checkout -f forkedOrigin/${env.CHANGE_BRANCH}
                """
            }

            commitHash = sh(script: "git rev-parse HEAD | tr '\\n' ' '", returnStdout: true)
            sh(script: "git checkout -f origin/${changeTarget}", returnStdout: false)
            sh(script: "git checkout -f forkedOrigin/${env.CHANGE_BRANCH}", returnStdout: false)
        }
        else {
            sh(script: "git checkout -f ${env.CHANGE_BRANCH}", returnStdout: false)
            commitHash = sh(script: "git rev-parse HEAD | tr '\\n' ' '", returnStdout: true)
            sh(script: "git checkout -f ${changeTarget} && git pull", returnStdout: false)
            sh(script: "git checkout -f ${env.CHANGE_BRANCH}", returnStdout: false)
        }

        println "Trying to merge origin/master into current PR branch"
        mergeStatus = sh(returnStatus: true, script: "git merge --no-commit --no-ff origin/${mergeWith}")
        if (mergeStatus != 0) {
            println "Auto merge has failed, aborting merge."
            sh(script: "git merge --abort", returnStdout: false)
        }
    }
    else{
        println "This build is not PR, checking out current branch and extract HEAD^1 commit to compare changes or develop when downstream_tests."
        if (env.BRANCH_NAME == "downstream_tests" || env.BRANCH_NAME == "downstream_hotfix"){
            // Exception added for Math PR #1832
            if (params.math_pr != null && params.math_pr == "PR-1832"){
                return true
            }
            return false
        }
        else{
            sh(script: "git checkout -f ${env.BRANCH_NAME}", returnStdout: false)
            commitHash = sh(script: "git rev-parse HEAD | tr '\\n' ' '", returnStdout: true)
            changeTarget = sh(script: "git rev-parse HEAD^1 | tr '\\n' ' '", returnStdout: true)
        }
    }

    def differences = ""
    if (env.CHANGE_FORK) {
        println "Comparing differences between current ${commitHash} from forked repository ${env.CHANGE_FORK}/${currentRepository} and target ${changeTarget}"
        if (mergeStatus != 0){
            differences = sh(script: """
                for i in ${sourceCodePaths};
                do
                    git diff forkedOrigin/${env.CHANGE_BRANCH} origin/${changeTarget} -- \$i
                done
            """, returnStdout: true)
        }
        else{
            differences = sh(script: """
                for i in ${sourceCodePaths};
                do
                    git diff --staged origin/${changeTarget} -- \$i
                done
            """, returnStdout: true)
        }
    }
    else{
        println "Comparing differences between current ${commitHash} and target ${changeTarget}"
        if (mergeStatus != 0){
            differences = sh(script: """
                for i in ${sourceCodePaths};
                do
                    git diff ${commitHash} ${changeTarget} -- \$i
                done
            """, returnStdout: true)
        }
        else {
            differences = sh(script: """
                for i in ${sourceCodePaths};
                do
                    git diff --staged ${changeTarget} -- \$i
                done
            """, returnStdout: true)
        }
    }

    println differences

    // Remove origin
    sh(script: "git remote rm forkedOrigin || true", returnStdout: true)
    //Hard reset to change branch
    sh(script: "git merge --abort || true", returnStdout: true)
    sh(script: "git reset --hard ${commitHash}", returnStdout: true)

    // If last commit message contains [ci skip] the current build will be skipped
    checkCiSkip = sh (script: "git log -1 | grep '.*\\[ci skip\\].*'", returnStatus: true)
    if (checkCiSkip == 0) {
        return true
    }

    // If last commit message contains [ci run all] we will run all stages no matter of source code changes
    checkCiRunAll = sh (script: "git log -1 | grep '.*\\[ci run all\\].*'", returnStatus: true)
    if (checkCiRunAll == 0) {
        return false
    }

    if (differences?.trim()) {
        println "There are differences in the source code, CI/CD will run."
        return false
    }
//     else if (isBuildAReplay()){
//         println "Build is a replay."
//         return false
//     }
    else{
        println "There are no differences in the source code, CI/CD will not run."
        return true
    }
}

def checkout_pr(String repo, String dir, String pr) {
    println env.BRANCH_NAME
    if (pr == '') {
        if (env.BRANCH_NAME == 'master' || env.CHANGE_TARGET == 'master'){
            pr = "master"
        }
        else {
            pr = "develop"
        }
    }
    println pr
    prNumber = pr.tokenize('-').last()
    if (pr.startsWith("PR-")) {
        sh """
          cd ${dir}
          git fetch https://github.com/stan-dev/${repo} +refs/pull/${prNumber}/merge:refs/remotes/origin/pr/${prNumber}/merge
          git checkout refs/remotes/origin/pr/${prNumber}/merge
        """
    } else {
        sh "cd ${dir} && git remote update && git fetch && git pull origin --track origin/${pr} && git checkout --track origin/${pr}"

    }
    sh "cd ${dir} && git clean -xffd"
}

def mailBuildResults(String _ = "", additionalEmails='') {
    script {
        if (env.BRANCH_NAME == 'downstream_tests') return
        try {
            emailext (
                subject: "[StanJenkins] ${currentBuild.currentResult}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
                body: """${currentBuild.currentResult}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.CHANGE_TITLE} ${env.BRANCH_NAME}): Check console output at ${env.BUILD_URL}
Or, check out the new blue ocean view (easier for most errors) at ${env.RUN_DISPLAY_URL}
""",
                recipientProviders: [brokenBuildSuspects(), requestor(), culprits()],
                to: additionalEmails
            )
        } catch (all) {
            println "Encountered the following exception sending email; please ignore:"
            println all
            println "End ignoreable email-sending exception."
        }
    }
}
