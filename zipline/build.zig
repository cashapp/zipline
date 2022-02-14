const std = @import("std");

pub fn build(b: *std.build.Builder) !void {
  const mode = b.standardReleaseOptions();

  var version_buf: [10]u8 = undefined;
  const version = try readVersionFile(&version_buf);

  const linux_x86 = b.addSharedLibrary("quickjs", null, .unversioned);
  try commonQuickJsSetup(linux_x86, version);
  linux_x86.setBuildMode(mode);
  linux_x86.override_dest_dir = std.build.InstallDir { .custom = "amd64" };
  linux_x86.setTarget(std.zig.CrossTarget{
    .cpu_arch = .x86_64,
    .os_tag = .linux,
    .abi = .gnu,
  });
  linux_x86.install();

  const macos_arm = b.addSharedLibrary("quickjs", null, .unversioned);
  try commonQuickJsSetup(macos_arm, version);
  macos_arm.setBuildMode(mode);
  macos_arm.override_dest_dir = std.build.InstallDir { .custom = "aarch64" };
  macos_arm.setTarget(std.zig.CrossTarget{
    .cpu_arch = .aarch64,
    .os_tag = .macos,
    .abi = .gnu,
  });
  macos_arm.install();

  const macos_x86 = b.addSharedLibrary("quickjs", null, .unversioned);
  try commonQuickJsSetup(macos_x86, version);
  macos_x86.setBuildMode(mode);
  macos_x86.override_dest_dir = std.build.InstallDir { .custom = "x86_64" };
  macos_x86.setTarget(std.zig.CrossTarget{
    .cpu_arch = .x86_64,
    .os_tag = .macos,
    .abi = .gnu,
  });
  macos_x86.install();
}

fn commonQuickJsSetup(quickjs: *std.build.LibExeObjStep, version: []const u8) !void {
  var quoted_version_buf: [12]u8 = undefined;
  const quoted_version = try std.fmt.bufPrint(&quoted_version_buf, "\"{s}\"", .{ version });
  quickjs.defineCMacro("CONFIG_VERSION", quoted_version);

  // Platform-independent code (i.e., relative jumps) to be safe.
  quickjs.force_pic = true;

  // Add the JDK's include/ headers.
  const java_home = std.os.getenv("JAVA_HOME").?;
  const java_include = try std.fs.path.join(std.testing.allocator, &[_][]const u8{ java_home, "include" });
  quickjs.addIncludeDir(java_include);

  // Walk the include/ directory for any child dirs (usually platform specific) and add them too.
  const java_include_dir = try std.fs.cwd().openDir(java_include, .{ .iterate = true });
  var jdk_walker = try java_include_dir.walk(std.testing.allocator);
  defer jdk_walker.deinit();
  while (try jdk_walker.next()) |entry| {
    switch (entry.kind) {
      .Directory => {
        const include_subdir = try std.fs.path.join(std.testing.allocator, &[_][]const u8{ java_include, entry.path });
        quickjs.addIncludeDir(include_subdir);
      },
      else => {},
    }
  }

  quickjs.linkLibC();
  quickjs.addCSourceFiles(&.{
    "native/quickjs/cutils.c",
    "native/quickjs/libregexp.c",
    "native/quickjs/libunicode.c",
    "native/quickjs/quickjs.c",
  }, &.{
    "-std=gnu99",
  });

  quickjs.linkLibCpp();
  quickjs.addCSourceFiles(&.{
    "native/Context.cpp",
    "native/ExceptionThrowers.cpp",
    "native/InboundCallChannel.cpp",
    "native/OutboundCallChannel.cpp",
  }, &.{
    "-std=c++11",
  });
}

fn readVersionFile(version_buf: []u8) ![]u8 {
  const version_file = try std.fs.cwd().openFile(
    "native/quickjs/VERSION",
    .{ .read = true },
  );
  defer version_file.close();

  var version_file_reader = std.io.bufferedReader(version_file.reader());
  var version_file_stream = version_file_reader.reader();
  const version = try version_file_stream.readUntilDelimiterOrEof(version_buf, '\n');
  return version.?;
}
