/* return true if any of the listed files are changed
 * - either by the current PR
 * - or since the last build
 * these can be overridden by "[ci skip]" (always false) or "[ci run all]" (always true) in the last commit message
 *
 * This replaces utils.verifyChanges, but returns its negation
 */

def call(String[] paths) {
  def changeSets = currentBuild.changeSets.collectMany { it }
  def changeTarget = null

  def commitMsgs = []
  if (changeSets) {
    commitMsgs = changeSets.collect { it.getMsg() }
  } else {
    // fall back to current commit
    changeTarget = "HEAD^"
    commitMsgs <<= sh(script: "git show -s --raw", returnStdout: true)
  }

  if (commitMsgs.any { it.contains("[ci skip]") }) {
    // If last commit message contains [ci skip] the current build will be skipped
    return false
  }
  if (commitMsgs.any { it.contains("[ci run all]") }) {
    // If last commit message contains [ci run all] we will run all stages no matter of source code changes
    return true
  }

  if (env.CHANGE_TARGET) {
    changeTarget = "refs/remotes/origin/${env.CHANGE_TARGET}"
  } else {
    /* always run builds on downstream_ branches (mainly for stan builds from math updates) */
    if (env.BRANCH_NAME == "downstream_tests" || env.BRANCH_NAME == "downstream_hotfix") {
      return true
    }
  }

  if (changeTarget) {
    /* XXX: unsafe path shell handling */
    def pathstr = paths.join(" ")
    def diff = sh(script: "git diff --quiet ${changeTarget} -- ${pathstr}", returnStatus: true)
    return (diff != 0)
  }

  return changeSets.any { change ->
    change.getAffectedPaths().any { path ->
      /* XXX: overinclusive prefix match */
      path.startsWithAny(paths)
    }
  }
}
