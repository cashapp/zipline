package app.cash.zipline.loader

enum class FileState {
  /**
   * The file is either currently downloading, or left over from a previous process.
   *
   * If it's currently downloading, it'll transition to `READY` when the download completes. Or the file will be
   * removed if the download fails.
   *
   * If it's left over from a previous process, that's the same as a failed download. Such files should be deleted
   * when the cache is opened.
   */
  DIRTY,

  /** The file is on the file system and ready to read. It will not be modified until it is deleted. */
  READY
}
