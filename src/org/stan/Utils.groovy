package org.stan

import hudson.model.Result
import jenkins.model.CauseOfInterruption.UserInterruption

def killOldBuilds() {
  while(currentBuild.rawBuild.getPreviousBuildInProgress() != null) {
    currentBuild.rawBuild.getPreviousBuildInProgress().doKill()
  }
}
