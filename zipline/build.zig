const std = @import("std");

pub fn build(b: *std.Build) !void {
  // The Windows builds create a .lib file in the lib/ directory which we don't need.
  const deleteLib = b.addRemoveDirTree(.{ .cwd_relative = b.getInstallPath(.prefix, "lib") });
  b.getInstallStep().dependOn(&deleteLib.step);

  try setupTarget(b, &deleteLib.step, .linux, .aarch64, "aarch64");
  try setupTarget(b, &deleteLib.step, .linux, .x86_64, "amd64");
  try setupTarget(b, &deleteLib.step, .macos, .aarch64, "aarch64");
  try setupTarget(b, &deleteLib.step, .macos, .x86_64, "x86_64");
  try setupTarget(b, &deleteLib.step, .windows, .aarch64, "aarch64");
  try setupTarget(b, &deleteLib.step, .windows, .x86_64, "amd64");
}

fn setupTarget(b: *std.Build, step: *std.Build.Step, tag: std.Target.Os.Tag, arch: std.Target.Cpu.Arch, dir: []const u8) !void {
  const lib = b.addSharedLibrary(.{
    .name = "quickjs",
    .target = b.resolveTargetQuery(.{
      .cpu_arch = arch,
      .os_tag = tag,
      // We need to explicitly specify gnu for linux, as otherwise it defaults to musl.
      // See https://github.com/ziglang/zig/issues/16624#issuecomment-1801175600.
      .abi = if (tag == .linux) .gnu else null,
    }),
    .optimize = .ReleaseSmall,
  });

  var version_buf: [64]u8 = undefined;
  const version = try readVersionFile(&version_buf);
  var quoted_version_buf: [64]u8 = undefined;
  const quoted_version = try std.fmt.bufPrint(&quoted_version_buf, "\"{s}\"", .{ version });
  lib.root_module.addCMacro("CONFIG_VERSION", quoted_version);

  lib.addIncludePath(b.path("native/include/share"));
  lib.addIncludePath(
    switch (tag) {
      .windows => b.path("native/include/windows"),
      else => b.path("native/include/unix"),
    }
  );

  lib.linkLibC();
  // TODO Tree-walk these two dirs for all C files.
  lib.addCSourceFiles(.{
    .files = &.{
      "native/common/context-no-eval.c",
      "native/common/finalization-registry.c",
      "native/common/global-gc.c",
      "native/quickjs/cutils.c",
      "native/quickjs/libregexp.c",
      "native/quickjs/libunicode.c",
      "native/quickjs/quickjs.c",
    },
    .flags = &.{
      "-std=gnu99",
    },
  });

  lib.linkLibCpp();
  // TODO Tree-walk this dirs for all C++ files.
  lib.addCSourceFiles(.{
    .files = &.{
      "native/Context.cpp",
      "native/ExceptionThrowers.cpp",
      "native/InboundCallChannel.cpp",
      "native/OutboundCallChannel.cpp",
      "native/quickjs-jni.cpp",
    },
    .flags = &.{
      "-std=c++11",
    },
  });

  const install = b.addInstallArtifact(lib, .{
    .dest_dir = .{
      .override = .{
        .custom = dir,
      },
    },
  });

  step.dependOn(&install.step);
}

fn readVersionFile(version_buf: []u8) ![]const u8 {
  const version = try std.fs.cwd().readFile(
    "native/quickjs/VERSION",
    version_buf,
  );

  return std.mem.trim(u8, version, "\r\n");
}
