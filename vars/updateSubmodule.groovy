def call(String repo, String branch, String path, String commit = '') {
  if (!commit)
    commit = sh(returnStdout: true, script: "git rev-parse HEAD").trim()
  def submodule = scmGit(branches: [[name: "refs/heads/$branch"]],
    userRemoteConfigs: [[credentialsId: 'stan-github', url: "https://github.com/stan-dev/${repo}.git"]])
  checkout scm: submodule, changelog: false, poll: false
  def nocommit = sh(returnStatus: true, script: """
    echo "160000 commit ${commit}\t$path" | git update-index --index-info
    GIT_COMMITTER_NAME="Stan Jenkins" GIT_COMMITTER_EMAIL="mc.stanislaw@gmail.com" git commit --author="Stan Jenkins <mc.stanislaw@gmail.com>" -m "Update submodules"
  """)
  if (!nocommit)
    gitPush(gitScm: submodule, targetBranch: branch, targetRepo: 'origin')
}
