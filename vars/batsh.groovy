def call(String cmd) {
  if (isUnix()) {
    sh cmd
  } else {
    // not perfect but should cover most cases
    bat cmd.replaceAll('\\$(\\w*)', '%$1%')
  }
}
