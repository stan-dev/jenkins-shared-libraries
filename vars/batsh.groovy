def call(String cmd) {
  if (isUnix()) {
    sh cmd
  } else {
    bat cmd
  }
}
