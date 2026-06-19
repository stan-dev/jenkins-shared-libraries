/* return true if any of the listed files are changed
 * - either by the current PR
 * - or since the last build
 * these can be overridden by "[ci skip]" (always false) or "[ci run all]" (always true) in the last commit message
 *
 * This replaces utils.verifyChanges, but returns its negation
 */

def call(String[] paths) {
  for (changeSet in currentBuild.changeSets) {
    for (change in changeSet) {
      def commitMsg = change.getMsg()

      // If last commit message contains [ci skip] the current build will be skipped
      if (commitMsg.contains("[ci skip]")) {
        return false
      }

      // If last commit message contains [ci run all] we will run all stages no matter of source code changes
      if (commitMsg.contains("[ci run all]")) {
        return true
      }
    }
  }

  def changeTarget = null
  if (env.CHANGE_TARGET) {
    changeTarget = "refs/remotes/origin/${env.CHANGE_TARGET}"
  } else {
    /* always run builds on downstream_ branches?? */
    if (env.BRANCH_NAME == "downstream_tests" || env.BRANCH_NAME == "downstream_hotfix") {
      return true
    }
  }

  if (changeTarget) {
    /* FIXME: unsafe path shell handling */
    def pathstr = paths.join(" ")
    def diff = sh(script: "git diff --quiet ${changeTarget} -- ${pathstr}", returnStatus: true)
    return (diff != 0)
  }

  return currentBuild.changeSets.any { changeSet ->
    changeSet.any { change ->
      change.getAffectedPaths().any { path ->
        /* FIXME: overinclusive prefix match */
        path.startsWithAny(paths)
      }
    }
  }
}
