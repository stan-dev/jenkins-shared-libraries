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
