package app.cash.zipline.loader

enum class FileState {
  /** The file is probably downloading, but might be left over from a previous process. */
  DIRTY,
  READY
}
