def call(String path, String pr = "") {
  if (!pr) {
    if (env.BRANCH_NAME == 'master' || env.CHANGE_TARGET == 'master')
      pr = "master"
    else
      pr = "develop"
  }

  def ref = "heads/$pr"
  if (pr.startsWith("PR-"))
    ref = "pull/${pr.minus('PR-')}/merge"

  dir (path) {
    sh """
      git fetch origin +refs/$ref:$pr
      git checkout $pr
    """
  }
}
