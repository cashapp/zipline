const std = @import("std");

pub fn build(b: *std.Build) !void {
  try setupTarget(b, .linux, .aarch64, "aarch64");
  try setupTarget(b, .linux, .x86_64, "amd64");
  try setupTarget(b, .macos, .aarch64, "aarch64");
  try setupTarget(b, .macos, .x86_64, "x86_64");
  try setupTarget(b, .windows, .aarch64, "aarch64");
  try setupTarget(b, .windows, .x86_64, "amd64");
}

fn setupTarget(b: *std.Build, tag: std.Target.Os.Tag, arch: std.Target.Cpu.Arch, dir: []const u8) !void {
  const lib = b.addLibrary(.{
    .name = "quickjs",
    .linkage = .dynamic,
    .root_module = b.createModule(.{
      .target = b.resolveTargetQuery(.{
        .cpu_arch = arch,
        .os_tag = tag,
        // We need to explicitly specify gnu for linux, as otherwise it defaults to musl.
        // See https://github.com/ziglang/zig/issues/16624#issuecomment-1801175600.
        .abi = if (tag == .linux) .gnu else null,
      }),
      .optimize = .ReleaseSmall,
    }),
  });

  var version_buf: [64]u8 = undefined;
  const version = try readVersionFile(b, &version_buf);
  var quoted_version_buf: [64]u8 = undefined;
  const quoted_version = try std.fmt.bufPrint(&quoted_version_buf, "\"{s}\"", .{ version });
  lib.root_module.addCMacro("CONFIG_VERSION", quoted_version);

  lib.root_module.addIncludePath(b.path("native/include/share"));
  lib.root_module.addIncludePath(
    switch (tag) {
      .windows => b.path("native/include/windows"),
      else => b.path("native/include/unix"),
    }
  );

  lib.root_module.link_libc = true;
  // TODO Tree-walk these two dirs for all C files.
  lib.root_module.addCSourceFiles(.{
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

  lib.root_module.link_libcpp = true;
  // TODO Tree-walk this dirs for all C++ files.
  lib.root_module.addCSourceFiles(.{
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

  b.getInstallStep().dependOn(&install.step);
}

fn readVersionFile(b: *std.Build, version_buf: []u8) ![]const u8 {
  const version = try std.Io.Dir.cwd().readFile(
    b.graph.io,
    "native/quickjs/VERSION",
    version_buf,
  );

  return std.mem.trim(u8, version, "\r\n");
}
